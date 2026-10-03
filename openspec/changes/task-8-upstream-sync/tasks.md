# Tasks

## 1. Merge

- [x] 1.1 На ветке `task-8-upstream-sync` выполнить `git merge origin/dev` (сообщение `TASK-8: влить upstream tchiotludo/akhq в release/ak-fork`)
- [x] 1.2 Конфликты `AbstractController.java`, `AKHQSecurityRule.java` — взять версию upstream целиком
- [x] 1.3 Конфликты `Acls.jsx`, `AclDetails.jsx` — взять версию upstream; вернуть `client/src/utils/functions.jsx` к версии upstream

## 2. Динамические группы через подмену UserGroupsResolver

- [x] 2.1 Добавить в `org.akhq.security.claim` класс форка `extends UserGroupsResolver` с `@Replaces(UserGroupsResolver.class)` и `@Requires(property = "akhq.access-management.enabled", value = "true")`: обычная аутентификация — `unrollGroups` → `mergeDynamicGroups` → плоский список `Group`; MCP OAuth — `super.resolve`
- [x] 2.2 Перенести `mergeDynamicGroups` из публичного API `DatabaseClaimProvider` в новый класс
- [x] 2.3 Проверить `git grep unrollGroups` и вызовы `ClaimProvider` вне `UserGroupsResolver`: новый код upstream не должен обходить подмену
- [x] 2.4 Обновить `DatabaseClaimProviderDynamicGroupsTest` под новую точку подключения; добавить тесты: выдача и отзыв без перелогина через новый класс, MCP-путь получает динамические группы, при `enabled: false` используется штатный `UserGroupsResolver`

## 3. Сборка на Micronaut 5

- [x] 3.1 `JAVA_HOME=~/.jdks/corretto-25.0.3 ./gradlew compileJava compileTestJava -x installFrontend -x assembleFrontend`; исправить ошибки в коде форка (JPA-сущности, репозитории, Flyway, email)
- [x] 3.2 Полный `./gradlew test` (нужен Docker для Testcontainers); исправить падения
- [x] 3.3 Фронтенд в `client/`: `npm ci`, `npm run lint`, `npx vitest run`, `npm run build`

## 4. Ручная проверка (docker-compose-local)

- [x] 4.1 Запрос доступа на топик и на префикс, одобрение владельцем, доступ появляется без перелогина, отзыв убирает его без перелогина
- [x] 4.2 Письмо владельцу о новом запросе приходит в Mailpit
- [x] 4.3 UI: пункт Access в меню, единая страница Access Management, кнопка Request Access на странице топика после переработки topic data, избранные топики не ломают список
- [x] 4.4 Страница ACL с кириллическим принципалом открывается, ссылка на детали работает
- [x] 4.5 `akhq.access-management.enabled: false` — раздел Access скрыт, эндпоинты отвечают `404`, права как в оригинале

## 5. Документация

- [x] 5.1 `CLAUDE.md`: Micronaut 5, сборка на JDK 25
- [x] 5.2 Скилл `.claude/skills/upstream-sync/SKILL.md`: upstream → `dev` → ветка `task-<N>-upstream-sync` от `release/ak-fork` → merge → правило конфликтов (берём upstream, форк подключается через свои классы) → тесты → ручная проверка → fast-forward `release/ak-fork`
- [x] 5.3 `openspec validate task-8-upstream-sync --strict`

## 6. Доставка

- [ ] 6.1 Пуш ветки `task-8-upstream-sync`, `Tests` в GitHub Actions зелёный
- [ ] 6.2 После подтверждения: fast-forward `release/ak-fork` на ветку и пуш (пересобирает прод)
- [ ] 6.3 `openspec archive task-8-upstream-sync`
