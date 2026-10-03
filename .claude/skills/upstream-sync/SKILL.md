---
name: upstream-sync
description: Вливание upstream tchiotludo/akhq в корпоративный форк (upstream → dev → release/ak-fork). Используй, когда просят влить или подтянуть upstream, обновить форк до свежего akhq, синхронизироваться с оригиналом, разрешить конфликты после merge из upstream.
---

# Вливание upstream в форк

Только merge: без rebase, без force, опубликованные коммиты не переписываются. Каждое вливание — OpenSpec change `task-<N>-upstream-sync`, номер задачи спросить у пользователя. Любой пуш — только после подтверждения пользователя.

## 1. upstream → dev

```bash
git fetch upstream && git fetch origin
git switch dev && git merge --ff-only origin/dev
git merge upstream/dev -m "TASK-<N>: влить upstream tchiotludo/akhq dev в dev"
```

В `dev` конфликтуют только правки CI форка — их сохраняем.

## 2. Ветка и merge в форк

```bash
git switch release/ak-fork && git merge --ff-only origin/release/ak-fork
git switch -c task-<N>-upstream-sync
# /opsx:propose → коммит планов change
git merge --no-ff origin/dev -m "TASK-<N>: влить upstream tchiotludo/akhq в release/ak-fork"
```

## 3. Конфликты

- Штатный файл upstream — берём версию upstream целиком и проверяем, что правок форка в нём не осталось:
  ```bash
  git checkout --theirs <file> && git diff --quiet origin/dev -- <file>
  ```
- Поведение форка возвращаем своим классом в пакете форка, а не правкой штатного кода: подмена бина `@Replaces(<UpstreamClass>.class)` + `@Requires(property = "akhq.access-management.enabled", value = "true")`. Пример: `DatabaseUserGroupsResolver` подменяет `UserGroupsResolver`.
- Upstream сам исправил то же, что форк, — удаляем код форка (включая хелперы в штатных файлах) и выводим capability из спек через delta `REMOVED`.
- В merge-коммите только разрешение конфликтов. Адаптация форка — отдельными коммитами с тестами.
- После merge проверить, что новый upstream-код не определяет группы в обход `UserGroupsResolver`:
  ```bash
  git grep -n "unrollGroups\|claimProvider\.\|get(\"groups\")" -- src/main/java
  ```

## 4. Сборка и тесты

```bash
export JAVA_HOME=~/.jdks/corretto-25.0.3   # Micronaut 5 не собирается на Java 21
./gradlew compileJava compileTestJava -x installFrontend -x assembleFrontend
./gradlew test -x installFrontend -x assembleFrontend   # нужен Docker (Testcontainers)
```

- `Unable to delete .../build/classes` или `AccessDeniedException` в `.gradle/` — файлы root после docker-compose-dev: `sudo rm -rf build/` (без sudo: `docker run --rm -v $PWD/build:/b -v $PWD/.gradle:/g alpine rm -rf /b/classes /b/tmp /g/<старая версия gradle>`).
- Таймаут `packages.confluent.io` — один повтор с `-Dorg.gradle.internal.http.socketTimeout=120000 -Dorg.gradle.internal.http.connectionTimeout=120000`; не помогло — остановиться и сообщить пользователю.
- Отчёты JUnit: `build/test-results/test/*.xml`.
- Известные флейки upstream на общем тестовом кластере Kafka (`/tmp/akhq-cs.json`): `TopicRepositoryTest.createWithConfig` (`Error for Describe Topic Config`, оставляет топик и сбивает счётчики в `list*`), `Subject 'stream-map-value' not found` и `MissingSourceTopicException` (гонка старта Kafka Streams). Не гонять параллельно с фронтенд-тестами и вторым `./gradlew test`.

Фронтенд:

```bash
cd client && npm ci
ESLINT_USE_FLAT_CONFIG=false npx eslint src   # npm run lint падает: ESLint 9 без flat-конфига
NODE_OPTIONS=--no-experimental-webstorage npx vitest run   # на Node ≥ 25 иначе ломается localStorage
npm run build
```

Падение, не связанное с форком, доказываем прогоном того же на upstream и сравнением списков:

```bash
git worktree add --detach <scratch>/dev-wt origin/dev
cd <scratch>/dev-wt/client && npm ci && npx vitest run   # или ./gradlew test --tests "<Test>"
git worktree remove <scratch>/dev-wt
```

Форк не должен добавлять новых ошибок lint и тестов относительно `origin/dev`.

## 5. Ручная проверка

Окружение из `CLAUDE.md` (`docker-compose-local.yml` + backend + frontend), сценарии — в `tasks.md` change: запрос, одобрение и отзыв доступа без перелогина, письмо в Mailpit, раздел Access Management, кнопка Request Access на странице топика, `akhq.access-management.enabled: false` ведёт себя как оригинал.

## 6. Доставка

1. Пуш ветки `task-<N>-upstream-sync`, workflow `Tests` в GitHub Actions зелёный.
2. После подтверждения пользователя (пересобирает прод):
   ```bash
   git switch release/ak-fork && git merge --ff-only task-<N>-upstream-sync && git push origin release/ak-fork
   ```
3. `openspec archive task-<N>-upstream-sync`.
