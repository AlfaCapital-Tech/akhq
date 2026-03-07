# TASK-1: Результаты код-ревью backend

## Известные ограничения (MVP)

| # | Проблема | Серьёзность | Когда исправить |
|---|----------|-------------|-----------------|
| 1 | `@Secured(IS_ANONYMOUS)` на AccessManagementController — нет проверки авторизации на уровне фреймворка | Средняя | Перед включением security (OIDC/LDAP). Заменить на `@AKHQSecured(resource = TOPIC, action = READ)` |
| 2 | `findAll()` + фильтрация в Java (pendingRequests, allRequests, accesses) — неэффективно при большом объёме | Средняя | При масштабировании. Перенести фильтрацию по prefix-паттернам в SQL (LIKE или regex) |
| 3 | Параметр `cluster` в URL не используется — access-management глобальный | Низкая | Если понадобится per-cluster управление доступами |
| 4 | `Pattern.quote()` в DatabaseClaimProvider вместо ручного regex-экранирования | Низкая | Не баг — `\Q...\E` работает с `Pattern.matches()`. Безопаснее чем ручное экранирование |

## Исправлено в ходе ревью

- Добавлена секция `jpa` в `application-dev.yml` — без неё Hibernate падал при запуске через docker-compose-dev

## Архитектурные решения

- **HttpResponse вместо HttpStatusException** — ErrorController в AKHQ ловит `Throwable` и возвращает 500. Поэтому контроллер возвращает `HttpResponse<?>` с явным статусом
- **DatabaseClaimProvider как @Primary + @Requires** — активируется только при `enabled: true`, не ломает оригинальное поведение
- **AbstractTestWithPostgres** — переиспользуемый базовый тест-класс (Kafka + PostgreSQL Testcontainers) для будущих тестов с БД
