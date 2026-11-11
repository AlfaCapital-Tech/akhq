# Tasks

## 1. Бэкенд

- [x] 1.1 DTO `Approver` (`username`, `email`) в `org.akhq.models.accessmanagement`
- [x] 1.2 `AccessRequestEntity.approvers` — `@Transient`, заполняется в `AccessManagementController.myRequests` и в ответе `createRequest`
- [x] 1.3 `AccessManagementController.approversFor`: владельцы префикса цели (точное совпадение для префиксного запроса, регулярка для топика), иначе суперадмины
- [x] 1.4 Тесты в `AccessManagementControllerTest`: владельцы в `/requests/my`; топик без владельца → суперадмины; префиксный запрос → владельцы префикса

## 2. Фронтенд

- [x] 2.1 `approversOf(request)` в `linkedRequest.js` + vitest
- [x] 2.2 Колонка Approvers в My Requests (`renderApprovers`: имена с `mailto:`)
- [x] 2.3 `RequestCard`: для состояния `pending-requester` — «Waiting for the decision of: …»

## 3. Спеки

- [x] 3.1 Дельты `topic-access-requests`, `access-management-ui`

## 4. Проверка

- [x] 4.1 `AccessManagementControllerTest` 25/25, `AccessRequestReminderJobTest` 5/5 (JDK 25); `npx vitest run --environment node` — linkedRequest 9/9; `npm run build` — OK
- [x] 4.2 Ручная проверка 10.11.2026 на `docker-compose-local` через Playwright (petrov → eis.test-approvers): колонка Approvers в My Requests с `mailto:dima@example.com`, карточка по ссылке «Waiting for the decision of: dima», топик без владельца → `approvers: [admin]`
- [ ] 4.3 Проверка на dev после выката
- [ ] 4.4 `openspec validate task-11-request-approvers --strict`
