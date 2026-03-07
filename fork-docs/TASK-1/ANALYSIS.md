# TASK-1: Аналитика — Система самообслуживания доступов к топикам

## Цель

Добавить в AKHQ возможность запрашивать и одобрять доступ к данным конкретных топиков через UI, с хранением доступов в PostgreSQL. Заменить ручное редактирование YAML для управления доступами пользователей.

## Контекст

В компании несколько команд работают с одной Kafka. Топики разделены по префиксам (`eis.*`, `edo.*`, `pc.*`). Сейчас для выдачи доступа нужно править `application.yml` и передеплоивать AKHQ. Нужно перевести управление доступами в UI с хранением в БД.

**Важно**: это форк AKHQ, который должен оставаться синхронизируемым с оригиналом (rebase-friendly). Минимально трогаем существующий код, максимально добавляем новый.

## Как работает текущая система авторизации

Подробнее: `src/main/java/org/akhq/configs/security/`, `src/main/java/org/akhq/security/`

1. **Роли** (`akhq.security.roles`) — набор ресурсов + действий (например `TOPIC:READ`, `TOPIC_DATA:CREATE`)
2. **Группы** (`akhq.security.groups`) — привязка ролей к паттернам топиков (regex)
3. **Маппинг** — при логине `ClaimProvider` маппит LDAP-юзера на AKHQ-группы
4. **Авторизация** — `AKHQSecurityRule` проверяет `@AKHQSecured` аннотации на контроллерах, сверяя ресурс+действие с группами юзера из JWT

Ключевые ресурсы:
- `TOPIC` — метаданные топика (список, партиции, конфиги)
- `TOPIC_DATA` — данные (сообщения): чтение (`READ`), запись (`CREATE`), удаление (`DELETE`)

## Требования

### 1. Конфигурация (YAML)

Новый блок `akhq.access-management`:

```yaml
akhq:
  security:
    roles:
      topic-browse:
        - resources: [TOPIC]
          actions: [READ, READ_CONFIG]
      topic-data-read:
        - resources: [TOPIC, TOPIC_DATA]
          actions: [READ]
      topic-data-write:
        - resources: [TOPIC]
          actions: [READ]
        - resources: [TOPIC_DATA]
          actions: [READ, CREATE]

    default-group: browser
    groups:
      browser:
        - role: topic-browse  # все видят все топики (метаданные), но не данные

  access-management:
    enabled: true    # false = фича выключена, поведение как у оригинала

    super-admins:
      - username: "petrov"
        email: "petrov@company.ru"
      - username: "sidorov"
        email: "sidorov@company.ru"

    prefix-owners:
      - prefix: "eis\\..*"
        owners:
          - username: "ivanov"
            email: "ivanov@company.ru"
          - username: "kuznetsov"
            email: "kuznetsov@company.ru"
      - prefix: "edo\\..*"
        owners:
          - username: "smirnov"
            email: "smirnov@company.ru"
      - prefix: "pc\\..*"
        owners:
          - username: "fedorov"
            email: "fedorov@company.ru"

    requestable-roles:
      - name: READ
        label: "Чтение данных"
        akhq-role: topic-data-read
      - name: WRITE
        label: "Чтение и запись данных"
        akhq-role: topic-data-write

    notifications:
      enabled: false
      from: "akhq@company.ru"
```

- `enabled: false` — вся фича выключена, AKHQ работает как оригинал
- `super-admins` — видят и одобряют все запросы (включая топики без владельца)
- `prefix-owners` — владельцы по regex-паттерну, одобряют запросы к своим топикам
- `requestable-roles` — какие роли можно запрашивать, с маппингом на akhq-роли
- `notifications.enabled: false` — email не шлётся, события пишутся в лог

### 2. БД (PostgreSQL + Flyway)

Добавить зависимости: Micronaut Data JPA, PostgreSQL driver, Flyway.

Миграция `V1__access_management.sql`:

```sql
CREATE TABLE access_request (
    id              UUID PRIMARY KEY,
    username        VARCHAR(255) NOT NULL,
    topic_name      VARCHAR(255) NOT NULL,
    role            VARCHAR(50)  NOT NULL,  -- READ / WRITE
    status          VARCHAR(50)  NOT NULL,  -- PENDING / APPROVED / REJECTED
    reason          TEXT,                    -- зачем нужен доступ
    reject_reason   TEXT,                    -- причина отказа
    resolved_by     VARCHAR(255),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMP
);

CREATE TABLE topic_access (
    id              UUID PRIMARY KEY,
    username        VARCHAR(255) NOT NULL,
    topic_name      VARCHAR(255) NOT NULL,
    role            VARCHAR(50)  NOT NULL,  -- READ / WRITE
    granted_by      VARCHAR(255) NOT NULL,
    granted_at      TIMESTAMP NOT NULL DEFAULT now(),
    request_id      UUID REFERENCES access_request(id),
    UNIQUE (username, topic_name, role)
);

CREATE INDEX idx_access_request_status ON access_request(status);
CREATE INDEX idx_access_request_username ON access_request(username);
CREATE INDEX idx_topic_access_username ON topic_access(username);
```

### 3. Backend

#### 3.1. Конфигурационные классы

Пакет `org.akhq.configs.accessmanagement`:

- `AccessManagementProperties` — `@ConfigurationProperties("akhq.access-management")`
- Вложенные: `PrefixOwner`, `Owner`, `RequestableRole`, `NotificationProperties`, `SuperAdmin`

#### 3.2. JPA-сущности и репозитории

Пакет `org.akhq.models.accessmanagement`:
- `AccessRequestEntity` — JPA-сущность для `access_request`
- `TopicAccessEntity` — JPA-сущность для `topic_access`

Пакет `org.akhq.repositories.accessmanagement`:
- `AccessRequestRepository` — Micronaut Data JPA
- `TopicAccessRepository` — Micronaut Data JPA

#### 3.3. DatabaseClaimProvider

Пакет `org.akhq.security.claim`.

**Ключевой компонент**. Оборачивает `LocalSecurityClaimProvider`:

1. Вызывает оригинальный `LocalSecurityClaimProvider.generateClaim()`
2. Читает из `topic_access` все доступы для данного username
3. Для каждого доступа добавляет динамическую группу с ролью и паттерном точного имени топика (например `eis\.topic1`). Java `String.matches()` матчит строку целиком, поэтому `^$` не нужны.
4. Возвращает объединённый результат

Пример: юзер `ivanov` имеет в БД `topic_access(eis.topic1, READ)` →
provider добавляет группу с `role: topic-data-read, pattern: "eis\\.topic1"`.

Активируется только при `akhq.access-management.enabled: true`. Когда `false` — работает только оригинальный `LocalSecurityClaimProvider`.

#### 3.4. REST API

Пакет `org.akhq.controllers`.

`AccessManagementController` — `@Controller("/api/{cluster}/access-management")`.

На уровне класса: `@AKHQSecured(resource = Role.Resource.TOPIC, action = Role.Action.READ)` — доступ есть у всех, кто видит топики. Бизнес-логика авторизации (владелец/суперадмин) проверяется внутри методов по username из конфигурации.

| Метод  | Endpoint                                                  | Кто вызывает        | Описание                      |
|--------|-----------------------------------------------------------|---------------------|-------------------------------|
| POST   | `/api/{cluster}/access-management/request`                | Любой юзер          | Создать запрос (topic_name, role, reason) |
| GET    | `/api/{cluster}/access-management/requests/my`            | Любой юзер          | Мои запросы                   |
| GET    | `/api/{cluster}/access-management/requests/pending`       | Владелец/суперадмин | Входящие PENDING-запросы      |
| GET    | `/api/{cluster}/access-management/requests/all`           | Владелец/суперадмин | Все запросы к своим топикам (история) |
| PUT    | `/api/{cluster}/access-management/requests/{id}/approve`  | Владелец/суперадмин | Одобрить                      |
| PUT    | `/api/{cluster}/access-management/requests/{id}/reject`   | Владелец/суперадмин | Отклонить (с reject_reason)   |
| GET    | `/api/{cluster}/access-management/accesses`               | Владелец/суперадмин | Текущие доступы к топикам своих префиксов |
| DELETE | `/api/{cluster}/access-management/accesses/{id}`          | Владелец/суперадмин | Отозвать доступ (revoke)      |
| GET    | `/api/{cluster}/access-management/topic/{topicName}/owners`    | Любой юзер     | Владельцы топика              |
| GET    | `/api/{cluster}/access-management/topic/{topicName}/my-access` | Любой юзер     | Мой текущий доступ к этому топику |

**Логика определения "свои топики" для владельца**: username юзера ищется в `prefix-owners[].owners[].username` → получаем список prefix-паттернов → фильтруем запросы/доступы по матчу `topic_name` на эти паттерны.

**Super-admin**: username ищется в `super-admins[].username` → видит всё, одобряет всё.

#### 3.5. NotificationService

Пакет `org.akhq.modules.accessmanagement`.

- При `notifications.enabled: true` — отправляет email через Micronaut Email (SMTP)
- При `notifications.enabled: false` — пишет в лог (`INFO: Запрос на доступ к топику X от юзера Y`)

События:
- Новый запрос → уведомление владельцам (или суперадминам если нет владельца)
- Одобрение → уведомление запросившему
- Отклонение → уведомление запросившему (с причиной)
- Отзыв доступа (revoke) → уведомление юзеру

### 4. Frontend (React)

#### 4.1. Новый раздел в левом меню

"Access Requests" — виден всем юзерам.

#### 4.2. Страница "Мои запросы" (для обычного юзера)

Таблица с запросами текущего юзера:
- Топик, роль, статус (PENDING/APPROVED/REJECTED), дата, кто одобрил/отклонил, причина отказа

#### 4.3. Страница "Управление доступами" (для владельца/суперадмина)

Три вкладки:
- **Входящие** — PENDING-запросы к топикам своих префиксов. Кнопки Approve / Reject (с полем для причины отказа).
- **История** — все запросы (одобренные, отклонённые, ожидающие)
- **Текущие доступы** — все выданные доступы к своим топикам. Кнопка Revoke.

#### 4.4. Кнопка "Запросить доступ" на странице топика

- Видна если у юзера нет доступа к данным этого топика
- При нажатии — модальное окно: выбор роли (READ/WRITE), поле "причина" (опционально), кнопка "Отправить"
- Отображение текущего доступа юзера к этому топику (если есть)
- Отображение владельцев топика (username из YAML)

### 5. Принцип совместимости с оригиналом

- **Не меняем** существующие файлы (кроме минимальных точек интеграции: регистрация нового раздела в UI, подключение зависимостей в `build.gradle`)
- **Новый код** — в отдельных пакетах (`accessmanagement`)
- **`DatabaseClaimProvider`** — обёртка поверх существующего провайдера, не замена
- **`enabled: false`** — всё выключено, AKHQ = оригинал
- При конфликтах после rebase — конфликтовать будут только `build.gradle` (зависимости) и точки интеграции в UI

## Зависимости для добавления в build.gradle

- `io.micronaut.data:micronaut-data-hibernate-jpa`
- `io.micronaut.sql:micronaut-jdbc-hikari`
- `io.micronaut.flyway:micronaut-flyway`
- `org.postgresql:postgresql`
- `io.micronaut.email:micronaut-email-javamail` (для notifications)

## Локальная разработка

```bash
docker compose -f docker-compose-dev.yml up
```

Для PostgreSQL — добавить сервис в `docker-compose-dev.yml`:
```yaml
postgres:
  image: postgres:16
  environment:
    POSTGRES_DB: akhq
    POSTGRES_USER: akhq
    POSTGRES_PASSWORD: akhq
  ports:
    - "5432:5432"
```

Конфигурация datasource в `application-dev.yml`:
```yaml
datasources:
  default:
    url: jdbc:postgresql://postgres:5432/akhq
    username: akhq
    password: akhq
    driver-class-name: org.postgresql.Driver
flyway:
  datasources:
    default:
      enabled: true
```
