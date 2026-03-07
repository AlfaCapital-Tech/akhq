# AKHQ — корпоративный форк

Форк [akhq.io](https://github.com/tchiotludo/akhq) с доработками для управления доступами к топикам через UI.

## Сборка и запуск

```bash
# Backend (Java 17, Micronaut 4.10.7, Gradle)
./gradlew build
./gradlew run                          # запуск dev-сервера на :8080

# Frontend (React)
cd client && npm install && npm start  # dev-сервер на :3000

# Docker (полное окружение: Kafka + Schema Registry + Connect)
docker compose -f docker-compose-dev.yml up
```

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
  - `security/` — аутентификация, маппинг, claim providers, authorization rule
- `client/src/` — React frontend (JSX, SCSS)
- `fork-docs/TASK-*` — постановки задач на доработку (ADR, аналитика, тестирование)

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
- Постановки задач в `docs/TASK-*/README.md`

## Текущие задачи

- **TASK-1**: Система самообслуживания доступов к топикам (PostgreSQL, Flyway, Micronaut Data JPA). См. `docs/TASK-1/README.md`
