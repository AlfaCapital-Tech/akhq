# AKHQ — корпоративный форк

Форк [akhq.io](https://github.com/tchiotludo/akhq) с доработками для управления доступами к топикам через UI.

## Сборка и запуск

### Локальная разработка (рекомендуемый способ)

Инфраструктура в Docker, backend и frontend — локально:

```bash
# 1. Поднять инфраструктуру (Kafka, PostgreSQL, Schema Registry, Connect, ksqlDB)
docker compose -f docker-compose-local.yml up -d

# 2. Backend (Java 17, Micronaut 4.10.7, Gradle) — запуск на :8080
MICRONAUT_CONFIG_FILES=application-local.yml ./gradlew run -x installFrontend -x assembleFrontend

# 3. Frontend (React, Vite) — запуск на :4000 (или следующий свободный)
cd client && npm install && npm start
# Важно: нужен client/.env.local с proxy на backend:
#   APP_BASE_URL=http://localhost:8080
# Без него Vite проксирует на :8081 (дефолт оригинала)

# Остановить инфраструктуру
docker compose -f docker-compose-local.yml down
```

В IDEA: запускать `org.akhq.App` с VM option `-Dmicronaut.config.files=application-local.yml`.

### Docker (всё-в-одном)

```bash
# Полное окружение в Docker (с PostgreSQL)
docker compose -f docker-compose-dev.yml up
```

**Важно:** после работы с docker-compose-dev контейнер создаёт файлы в `build/` от root. Перед локальной сборкой: `sudo rm -rf build/`

## Тесты

```bash
./gradlew test                         # все тесты
./gradlew test --tests "*.TopicControllerTest"  # конкретный тест
```

## Структура проекта

- `src/main/java/org/akhq/` — backend
  - `controllers/` — REST API (аннотация `@AKHQSecured` для авторизации)
  - `repositories/` — бизнес-логика работы с Kafka
  - `models/` — доменные модели
  - `configs/security/` — конфигурация ролей, групп, LDAP, OIDC
  - `configs/accessmanagement/` — конфигурация access-management (форк)
  - `security/claim/` — claim providers (LocalSecurity, DatabaseClaimProvider)
  - `modules/accessmanagement/` — NotificationService (форк)
  - `repositories/accessmanagement/` — JPA-репозитории access-management (форк)
  - `models/accessmanagement/` — JPA-сущности access-management (форк)
- `client/src/` — React frontend (JSX, SCSS, Vite, Bootstrap 5, react-router v7)
  - `containers/` — страницы (Topic, Node, AccessManagement и т.д.)
  - `components/` — переиспользуемые компоненты (Table, Modal, Form, Root)
  - `utils/` — API клиент (`api.jsx`), эндпоинты (`endpoints.jsx`), роутинг (`AkhqRoutes.jsx`)
  - Все страницы наследуют `Root` — базовый класс с `getApi()`/`postApi()`/`putApi()`/`removeApi()`
  - Авторизация через `sessionStorage`: `roles`, `user`, `auths` (включая `accessManagementEnabled`)
  - Флаг `accessManagementEnabled` из `/api/auths` управляет видимостью раздела Access Management
- `fork-docs/TASK-*` — постановки задач на доработку (ADR, аналитика, ревью)

## Система авторизации

Ключевые сущности: `Role` (ресурс + действие) → `Group` (роли + паттерны топиков) → маппинг юзеров на группы через `ClaimProvider`. Авторизация в `AKHQSecurityRule` по аннотациям `@AKHQSecured`.

Два ресурса для топиков:
- `TOPIC` — метаданные (список, партиции, конфиги)
- `TOPIC_DATA` — сообщения (READ = чтение, CREATE = запись)

## Принципы работы с форком

- **Минимально трогаем существующий код** — чтобы rebase с оригиналом проходил без конфликтов
- Новый код — в отдельных пакетах (например `org.akhq.configs.accessmanagement`)
- Фичи форка управляются флагом `enabled` в конфигурации — при `false` поведение = оригинал
- Текст коммитов на русском, начинать с номера задачи (спросить у пользователя)
- Постановки задач в `fork-docs/TASK-*/`
- **ErrorController** ловит `Throwable` → 500, поэтому в контроллерах используем `HttpResponse<?>` вместо исключений
- Тесты с БД наследуются от `AbstractTestWithPostgres` (Kafka + PostgreSQL Testcontainers)

## Текущие задачи

- **TASK-1**: Система самообслуживания доступов к топикам (PostgreSQL, Flyway, Micronaut Data JPA). Backend готов, frontend в работе. См. `fork-docs/TASK-1/`
- 
