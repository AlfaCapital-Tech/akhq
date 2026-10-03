# Design

## Context

Пробный merge `dev` (`9aed7373`) в `release/ak-fork` даёт 4 конфликта:

- `AbstractController`, `AKHQSecurityRule`. Upstream вынес определение групп пользователя в новый `org.akhq.security.authentication.UserGroupsResolver` (#3236, MCP-сервер). Форк в обоих файлах вызывает `DatabaseClaimProvider.mergeDynamicGroups(unrollGroups(...))` (TASK-6).
- `Acls.jsx`, `AclDetails.jsx`. Upstream исправил Unicode-принципалы (#3227, `utils/base64.js`), форк исправил то же самое в TASK-5 (`encodeBase64Utf8`/`decodeBase64Utf8` в `utils/functions.jsx`).

Остальные правки форка в штатных файлах слились автоматически: `Topic.jsx` (кнопка Request Access), `Header.jsx`, `Sidebar.jsx`, `AkhqRoutes.jsx`, `api.jsx`, `endpoints.jsx`, `constants.jsx`, `AkhqController`, `application.yml`, `build.gradle`. В upstream за это время: Micronaut 5.0.1, Gradle 9.7, Vite 8, MCP-сервер (по умолчанию выключен, `akhq.mcp.enabled: false`), переработка topic data (#3235), избранные топики (#3179), аудит (#3110).

`UserGroupsResolver.resolve` для обычной аутентификации разворачивает группы из JWT (`AKHQSecurityRule.unrollGroups`) и возвращает плоский список, теряя ключи групп. Для MCP OAuth он вызывает `ClaimProvider.generateClaim`, то есть при включённой функции — `DatabaseClaimProvider` (`@Primary`), который уже подмешивает свежие группы из БД.

## Goals / Non-Goals

**Goals:**
- Влить upstream без потери поведения форка, описанного в спеках
- Сократить правки штатных файлов: точка подключения форка к авторизации — один класс в пакете форка
- Сборка и все тесты зелёные на Micronaut 5

**Non-Goals:**
- Аудит действий access management через новый механизм аудита upstream
- Исправление известных слабых мест access management — отдельные change
- Обновление зависимостей форка сверх того, что требует Micronaut 5

## Decisions

### Динамические группы — через подмену `UserGroupsResolver`
Новый класс форка в `org.akhq.security.claim` (рядом с `DatabaseClaimProvider`): `extends UserGroupsResolver`, `@Singleton @Replaces(UserGroupsResolver.class) @Requires(property = "akhq.access-management.enabled", value = "true")`.
- Обычная аутентификация: `unrollGroups` → `mergeDynamicGroups` (убрать `db-access-*` из JWT, добавить свежие из БД) → плоский список `Group`, как в upstream
- MCP OAuth: `super.resolve(...)` — `DatabaseClaimProvider` уже возвращает свежие группы

`AbstractController` и `AKHQSecurityRule` берутся из upstream без изменений. `mergeDynamicGroups` перестаёт быть публичным API для штатного кода: переносится в новый класс или становится package-private.

Альтернатива — разрешить конфликты, вставив `mergeDynamicGroups` в upstream-версии обоих файлов. Отклонена: две правки штатных файлов вместо нуля, и MCP-путь пришлось бы обрабатывать отдельно.

### ACL — версия upstream
Берём `Acls.jsx` и `AclDetails.jsx` из upstream, удаляем `encodeBase64Utf8`/`decodeBase64Utf8` из `functions.jsx` (других использований нет), возвращая файл к версии upstream. Capability `acl-view` выводится из спек (`retire_capabilities: true`).

### Доступы распространяются на MCP-клиентов
Получается само собой: MCP-путь upstream идёт через `ClaimProvider`, а это `DatabaseClaimProvider`. Отключать отдельно — лишний код, а права у пользователя должны быть одинаковыми независимо от клиента. MCP-сервер по умолчанию выключен.

### Merge, а не rebase
`release/ak-fork` — опубликованная ветка, из которой собирается прод. Merge-коммит делается на ветке `task-8-upstream-sync`, после проверок `release/ak-fork` переводится на неё fast-forward.

## Risks / Trade-offs

- Micronaut 5 тянет новые мажорные версии Micronaut Data / Hibernate, Flyway и Micronaut Email → JPA-сущности, миграции или настройки почты форка могут не собраться или вести себя иначе. Проверка: компиляция, `AbstractTestWithPostgres`-тесты, ручная отправка письма в Mailpit
- Автоматически слитые UI-файлы (`Topic.jsx` после переработки topic data, `Header.jsx`, `Sidebar.jsx`) могут собраться, но сломаться в рантайме → ручная проверка UI по сценариям спек
- Новый upstream-код, использующий группы в обход `UserGroupsResolver`, не увидит динамические доступы → проверить `git grep unrollGroups` и вызовы `ClaimProvider` после merge
- Системная Java — 21, Gradle-плагин Micronaut 5 требует JDK 25 → собирать с `JAVA_HOME=~/.jdks/corretto-25.0.3` (или указать JDK 25 в IDEA)
- Пуш в `release/ak-fork` пересобирает прод → пушить только после зелёных тестов и ручной проверки
