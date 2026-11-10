# Tasks

## 1. Бэкенд: данные

- [x] 1.1 Миграция `V3__access_request_cluster_reminder.sql`: колонки `cluster`, `last_reminded_at` в `access_request`
- [x] 1.2 `AccessRequestEntity`: поля `cluster`, `lastRemindedAt`; `AccessManagementController.createRequest` сохраняет `{cluster}`
- [x] 1.3 `AccessRequestRepository`: выборка Pending-запросов, просроченных относительно `coalesce(last_reminded_at, created_at)`, и условный UPDATE-захват отметки

## 2. Бэкенд: письма

- [x] 2.1 `NotificationProperties.reminderInterval: Duration = 3d`; документировать `0` как выключение
- [x] 2.2 `NotificationService`: ссылка `/ui/<cluster>/access-management?request=<id>` (кластер записи, иначе первый из `akhq.connections`); метод `notifyReminder` с темой-напоминанием и датой создания
- [x] 2.3 `AccessRequestReminderJob` (`@Scheduled`, тик 15 мин): выборка, захват, отправка; не работает при `enabled: false` или интервале `0`
- [x] 2.4 Тесты (`AccessRequestReminderJobTest`, 5 тестов, зелёные): ссылка содержит кластер и id; старая запись без кластера → первый кластер; напоминание уходит для просроченного Pending и не уходит для свежего, решённого и при интервале `0`; повторный захват той же записи возвращает 0 строк; `base-url` пуст → ссылки нет

## 3. Фронтенд

- [x] 3.1 `AccessManagement.jsx`: чтение `?request=` (`useLocation`/`props.location.search`), выбор вкладки Pending при наличии параметра
- [x] 3.2 Карточка запроса: поиск в `allRequests` и `myRequests`; три состояния (Pending у согласующего — кнопки, решённый — статус, не найден — сообщение); переиспользование `handleApprove`/`openRejectModal`
- [x] 3.3 Выделение строки в таблице Pending: маркер `data-request-highlight` в ячейке цели, CSS `tr:has(...)` в стилях форка, прокрутка к строке после загрузки
- [x] 3.4 vitest на выбор состояния карточки (7 тестов, прогон с `--environment node`: jsdom в репе требует Node 26, локально Node 20) по спискам и параметру

## 4. Спеки и документация

- [x] 4.1 Дельты спек `access-notifications`, `topic-access-requests`, `access-management-ui` (в этом change)
- [x] 4.2 `application-dev.yml`: пример `reminder-interval` (в `application.example.yml` блока access-management нет)

## 5. Проверка

- [x] 5.1 `./gradlew test --tests` по четырём классам access management — 36/36 зелёные (JDK 25); `npx vitest run --environment node` — 7/7; `npm run build` — OK. Полный `./gradlew test` не гонялся (нестабильные тесты upstream, как в TASK-8/9)
- [x] 5.2 Ручная проверка 10.11.2026 на `docker-compose-local` + Mailpit, AKHQ из исходников (`gradle run`, конфиг с `reminder-interval: 2m`, `reminder-check-interval: 20s`), через Playwright: письмо о новом запросе со ссылкой `/ui/local/access-management?request=<id>`; ссылка без сессии → форма логина → после входа та же страница; карточка Pending с Approve/Reject у владельца, строка в Pending подсвечена; Approve из карточки → статус Approved, письма об одобрении нет; перезагрузка по ссылке показывает Approved с автором; чужая ссылка под другим владельцем → «Request not found or you are not its approver»; напоминания пришли в 11:14:15 и 11:16:15 (тема `Reminder: … pending 0 days`, та же ссылка), после Reject из карточки новых нет. Две реплики вживую не поднимал: атомарность захвата покрыта тестом `claimIsAtomic`. Скриншоты в `~/Projects/.playwright-mcp/dig35672-*.png`
- [ ] 5.3 Проверка на dev-стенде: открыть ссылку из письма без сессии → вход → та же страница с карточкой (SSO на стендах пока не настроен, вход по форме)
- [ ] 5.4 `openspec validate task-10-access-request-deeplink --strict`
