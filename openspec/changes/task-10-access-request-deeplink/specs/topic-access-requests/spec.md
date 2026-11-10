## MODIFIED Requirements

### Requirement: Хранение запросов и доступов
Запросы и доступы MUST храниться в PostgreSQL в таблицах `access_request` и `topic_access`, схема создаётся миграциями Flyway (`V1__access_management.sql`, `V2__prefix_access.sql`, `V3__access_request_cluster_reminder.sql`). У записи заполнено ровно одно из полей `topic_name` или `prefix`. Запрос SHALL хранить кластер, в котором он создан (`cluster`, из пути `/api/{cluster}/...`; у записей до V3 — пусто), и время последнего напоминания (`last_reminded_at`, пусто, пока напоминаний не было). Уникальность доступа MUST обеспечиваться по `(username, topic_name, role)` для доступов на топик и по `(username, prefix, role)` для доступов на префикс. Доступы бессрочны, запросы не удаляются.

#### Scenario: Доступ на топик и на префикс одновременно
- **WHEN** у `alice` есть доступ `READ` к `team-a.orders` и одобряется её запрос `READ` на префикс `team-a\..*`
- **THEN** в `topic_access` хранятся обе записи

#### Scenario: Кластер запроса
- **WHEN** `alice` вызывает `POST /api/prod/access-management/request`
- **THEN** в записи запроса `cluster = prod`, и это поле возвращается в ответах API вместе с остальными полями запроса
