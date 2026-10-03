# Proposal

## Why

Форк отстаёт от upstream `tchiotludo/akhq` на 78 коммитов (последнее вливание 05.06): переход на Micronaut 5, экспериментальный MCP-сервер, переработка просмотра и поиска сообщений, избранные топики, аудит записи и удаления, исправление Unicode-принципалов в ACL, обновления зависимостей. Чем дольше отставание, тем тяжелее следующее вливание и тем дольше прод живёт без исправлений upstream. Upstream уже влит в `dev` (`9aed7373`), осталось довезти его до `release/ak-fork`.

## What Changes

- `dev` вливается в `release/ak-fork` merge-коммитом, без rebase
- **BREAKING (сборка)**: Micronaut 4.10 → 5, для сборки нужен JDK 25 (целевая версия Java уже 25, но теперь этого требует и Gradle-плагин Micronaut)
- Подмешивание динамических групп из БД переносится из правок штатных `AbstractController` и `AKHQSecurityRule` в один класс форка, который подменяет новый upstream-класс определения групп пользователя; оба штатных файла возвращаются к версии upstream
- Исправление Unicode-принципалов в ACL берётся из upstream (#3227), собственное исправление форка (TASK-5) удаляется, capability `acl-view` выводится из спек форка
- Зависимости форка (JPA, Flyway, email, Testcontainers PostgreSQL) проверяются и при необходимости обновляются под Micronaut 5
- Процедура вливания upstream оформляется скиллом проекта `.claude/skills/upstream-sync/`

## Capabilities

### New Capabilities

Нет.

### Modified Capabilities

- `topic-access-requests`: требование «Применение выдачи и отзыва без перелогина» переформулировано без привязки к классам и распространено на все способы аутентификации, включая клиентов MCP-сервера upstream
- `acl-view`: capability удаляется — поведение теперь обеспечивает upstream, форк этим кодом не владеет

## Impact

- Код: `org.akhq.security` (новый класс форка), `DatabaseClaimProvider`, откат правок в `AbstractController`, `AKHQSecurityRule`, `Acls.jsx`, `AclDetails.jsx`, `functions.jsx`; автоматически слитые правки форка в `Topic.jsx`, `Header.jsx`, `Sidebar.jsx`, `AkhqRoutes.jsx`, `api.jsx`, `endpoints.jsx`, `constants.jsx`, `AkhqController`, `application.yml`
- Сборка: Micronaut 5.0.x, Gradle 9.7, Vite 8, JDK 25 локально и на CI
- Прод: после пуша в `release/ak-fork` пересобирается образ, поэтому перед пушем нужны полный прогон тестов и ручная проверка
