# TASK-6: Подхватывать выданные доступы без релогина

## Проблема

После выдачи прав на топик через UI пользователю требуется выйти и зайти снова, чтобы новые ACL применились. Нужно, чтобы выданные/отозванные доступы подхватывались сразу.

## Причина

Архитектура авторизации в AKHQ:

1. При логине `CustomClaimsGenerator.populateWithAuthentication` упаковывает все группы пользователя (gzip+base64) в claim `groups` JWT-токена.
2. На каждом HTTP-запросе `AKHQSecurityRule.check()` читает группы **из токена** (`AKHQSecurityRule.java:147`, метод `decompressGroups`), а **не из БД**.
3. `DatabaseClaimProvider.generateClaim` дёргается ровно один раз — в момент выпуска токена. Дальше токен «замораживает» состояние ACL до следующего логина.

Ветка `getClaimProviderGroups`, которая ходит в БД на каждом запросе, отрабатывает только когда groups в токене лежат как `List<String>` (OIDC с `useOidcGroupsInToken`). У нас групп-структура — `Map<String,List<Group>>`, эта ветка не исполняется.

Отсюда: после `INSERT/DELETE` в `topic_access` JWT остаётся прежним → пользователь не видит изменений до следующего логина.

## Решение

Динамические группы (db-access) в `AKHQSecurityRule.check()` всегда берутся из БД. То, что лежит в JWT под ключами `db-access-*`, игнорируется. Статические группы (LDAP/OIDC/local/header) остаются в JWT как раньше.

### Почему без кэша

Один SELECT по индексу на запрос — бесплатно для нагрузок AKHQ. Revoke применяется мгновенно. Если понадобится — `@Cacheable` добавляется одной аннотацией (Caffeine уже подключён).

## Реализация

### 1. Метод `resolveDynamicGroups` в `DatabaseClaimProvider`

Публичный метод `Map<String, List<Group>> resolveDynamicGroups(String username)` — берёт `TopicAccessRepository.findByUsername`, маппит каждый `TopicAccessEntity` в `Group` ровно так же, как сейчас делает цикл в `generateClaim` (строки 45–70). Возвращает map с ключами `db-access-*`.

Существующий `generateClaim` начинает использовать этот же метод, чтобы не дублировать логику маппинга.

### 2. `AKHQSecurityRule.check()` — игнорировать db-access из JWT, мержить из БД

После того как `userGroups` собраны из токена (строки 79–86):

- выкинуть из них всё, что пришло под ключами `db-access-*` (актуально для старых токенов с зашитыми динамическими группами);
- если access-management включён — добавить свежие из `databaseClaimProvider.resolveDynamicGroups(authentication.getName())`.

`databaseClaimProvider` инжектится как опциональный bean (`@Inject @Nullable`), чтобы не ломать конфигурации без access-management.

### 3. Тесты

`AbstractTestWithPostgres` + интеграционный тест:

- Залогиниться → запрос к защищённому endpoint → 403.
- Вставить `TopicAccessEntity` напрямую в БД (без релогина) → повторный запрос → 200.
- Удалить запись → запрос → 403.

Между шагами JWT-токен **не перевыпускается**.

## Анализ рисков

| Кейс | Старое поведение | Новое поведение |
|------|------------------|-----------------|
| Выдали доступ через UI | Нужен релогин | Применяется на следующем запросе |
| Отозвали доступ | Действовал до релогина (дыра в безопасности) | Пропадает на следующем запросе |
| Нет access-management (`enabled=false`) | Работает | Работает идентично (resolver не вызывается) |

### Поведение при ошибке БД

Не обрабатываем. Если PostgreSQL недоступна — access-management и так не функционирует. Исключение из репозитория долетает до `ErrorController` → 500. Никаких try/catch и fallback'ов.
