# Tasks

## 1. Подготовка

- [x] 1.1 Влить в ветку `task-9-access-onboarding` актуальный `release/ak-fork` с TASK-8 (merge)
- [x] 1.2 Перенести картинки из `openspec/changes/task-9-access-onboarding/assets/` в `client/src/images/mascot/`
- [x] 1.3 Добавить зависимость `react-joyride@3.2.0` в `client/`

## 2. Бэкенд

- [x] 2.1 `GET /api/{cluster}/access-management/me` → `{approver}` на `isSuperAdmin`/`getOwnedPrefixes`, `404` при выключенной функции
- [x] 2.2 `GET /api/{cluster}/access-management/topic/{topicName}/data-access` → `{read}` по группам из `UserGroupsResolver` и ролям `TOPIC_DATA`/`READ` с проверкой кластера и паттерна
- [ ] 2.3 Тесты: владелец, суперадмин, обычный пользователь; чтение по статической группе, по доступу на топик, по доступу на префикс, отказ для чужого топика и чужого кластера, `404` при выключенной функции. Кроме `404`: без datasource и SMTP-настроек контроллер access management не создаётся и отвечает `500` (существующая проблема, не TASK-9)

## 3. Состояние и маскот

- [x] 3.1 Модуль состояния онбординга: версия тура, «больше не показывать», показ за сессию; хранилища в `try/catch`; тесты vitest
- [x] 3.2 Компонент маскота: позы, облачко, кнопки, CSS-анимации, моргание, `prefers-reduced-motion`, скрытие при открытой модалке
- [x] 3.3 Смонтировать маскот в `AkhqRoutes.jsx`; анонс после релиза при `accessManagementEnabled`

## 4. Тур

- [x] 4.1 Атрибуты `data-tour` на пункте Access в `Sidebar.jsx` и на целях в `AccessManagement.jsx`
- [x] 4.2 Тур на `react-joyride` с `tooltipComponent` маскота: шаги из спеки, шаги про одобрение по `GET /me`, центрированные карточки для шагов без цели
- [x] 4.3 Запуск по `?tour=1` после загрузки данных страницы, кнопка повторного запуска, отметка версии при завершении и пропуске

## 5. Страница топика

- [x] 5.1 Компонент форка: запрос `data-access`, плашка «Нет доступа к сообщениям» с кнопкой Request Access, подсказка маскота раз за сессию
- [x] 5.2 `Topic.jsx`: при `accessManagementEnabled` вкладка Data видна всегда и открывается по умолчанию, содержимое через компонент форка; без функции — как в upstream

## 6. Проверка

- [ ] 6.1 `./gradlew test` (JDK 25), `npm run lint`, `npx vitest run`, `npm run build`
- [ ] 6.2 Ручная проверка на `docker-compose-local`: анонс и тур обычного пользователя и владельца; «Пропустить», «Не сейчас», «Больше не показывать», повторный запуск; топик без прав (нет ролей вообще и есть права на другие топики); выдача доступа и вкладка Data без перелогина; модалка поверх тура; `accessManagementEnabled: false` — всё как в оригинале
- [x] 6.3 `openspec validate task-9-access-onboarding --strict`
