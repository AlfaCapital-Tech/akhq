# Proposal

## Why

Постановки задач форка лежали в `fork-docs/TASK-*` как история изменений: чтобы понять текущее поведение access management, приходилось читать TASK-1…6 по порядку и мысленно применять их друг к другу. Часть аналитики к тому же разошлась с реализацией. Нужен один источник правды о том, как форк работает сейчас, и единый процесс для новых изменений.

## What Changes

- Подключён OpenSpec (`openspec/config.yaml`, язык артефактов — русский)
- Написаны базовые спеки текущего поведения форка по коду: `topic-access-requests`, `access-management-ui`, `access-notifications`, `json-logging`, `acl-view`
- Удалён каталог `fork-docs/` — его содержимое перенесено в спеки и в `design.md` этого change, история остаётся в git
- `CLAUDE.md`: правила работы через OpenSpec, политика веток и вливания upstream (merge, без rebase), актуальная версия Java
- Сгенерированные OpenSpec скиллы `/opsx:*` не коммитятся (`.claude/.gitignore`)

## Capabilities

### New Capabilities

Нет: спеки описывают уже существующее поведение и добавлены напрямую в `openspec/specs/` как базовая линия, поэтому change помечен `skip_specs: true`.

### Modified Capabilities

Нет.

## Impact

Только документация и инструменты разработки. Код, конфигурация и сборка не меняются.
