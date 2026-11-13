# Tasks

## 1. БД и модель

- [x] 1.1 Добавить `revokedBy`/`revokedAt` в `TopicAccessEntity` и миграцию `V4__topic_access_revocation.sql`: nullable-колонки и unique-индексы только для активных записей; запустить `AccessManagementControllerTest` и проверить, что Flyway + `hbm2ddl.validate` проходят.
- [x] 1.2 Добавить в `TopicAccessRepository` active-only derived-запросы и условный UPDATE первого отзыва; создать `TopicAccessRepositoryTest` с проверкой для топика и префикса: повторная выдача после отзыва допустима, вторая активная запись запрещена, активная выборка исключает историю.

## 2. Бэкенд

- [x] 2.1 Перевести `DatabaseClaimProvider`, `my-access` и поиск дубликата при Approve на active-only запросы; обновить `DatabaseClaimProviderDynamicGroupsTest` на soft-delete и добавить в `AccessManagementControllerTest` проверку, что отозванный доступ не виден в `my-access`; оба класса тестов зелёные.
- [x] 2.2 Заменить hard delete в `revokeAccess` на условный UPDATE с ответом `200` для повторного вызова и уведомлением только при первом отзыве; расширить `AccessManagementControllerTest`: запись и исходный запрос сохраняются, `revokedBy`/`revokedAt` возвращаются в `/accesses`, повторный revoke их не меняет, чужой revoke остаётся `403`; тест класса зелёный.
- [x] 2.3 Обеспечить создание новой активной записи при Approve после отзыва; добавить в `AccessManagementControllerTest` циклы повторной выдачи для топика и префикса с проверкой нового `id`, сохранной старой записи и одного активного доступа; тест класса зелёный.

## 3. Фронтенд

- [x] 3.1 Переименовать Current Accesses в Accesses, показать Active/Revoked и `revokedBy`/`revokedAt`, рендерить Revoke только для активных записей, заменить `noContent` на `No accesses found`, обновить текст тура; `npm run build` завершается успешно.
- [x] 3.2 На `docker-compose-local` проверить в UI отзыв без исчезновения строки, отсутствие повторной кнопки Revoke, отображение автора/даты и повторную выдачу с двумя записями в Accesses; зафиксировать результат в задаче. Проверено 13.11.2026: после Revoke строка осталась со статусом Revoked, автором `anonymous`, датой и `-` вместо кнопки; после нового запроса и Approve в Accesses отображались прежняя Revoked и новая Active записи. Повторная выдача того же префикса дополнительно подтверждена через API и контроллерные тесты.

## 4. Интеграционная проверка

- [x] 4.1 На JDK 25 запустить `AccessManagementControllerTest`, `TopicAccessRepositoryTest`, `DatabaseClaimProviderTest`, `DatabaseClaimProviderDynamicGroupsTest` и `npm run build`; все проверки зелёные.
- [x] 4.2 Запустить `openspec validate task-12-access-revocation-history --strict`; ошибок и предупреждений нет.
