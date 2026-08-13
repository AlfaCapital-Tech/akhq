# AKHQ — JSON-логи в stdout для Vector/OpenSearch

Приложение — AKHQ (корпоративный форк), Java 17+ / Micronaut 4 / Logback 1.5,
деплой в Kubernetes через ArgoCD.

Формат вывода согласован с пайплайном Vector в namespace `infra`: события
содержат обязательные поля `message`, `logType` и `@timestamp`. Правки общего
трансформа Vector не требуются — соседние сервисы namespace не затрагиваются.

## Что нужно сделать инфраструктурной команде

Ровно две вещи.

**1. Раскатать образ, собранный из ветки `release/ak-fork`.** Конфигурация
логирования и нужный энкодер лежат внутри образа, монтировать ничего не надо.

**2. Добавить в манифест Deployment переменную окружения:**

```yaml
env:
  - name: JAVA_OPTS
    value: "-Dlogback.configurationFile=logback-json.xml"
```

Здесь `logback-json.xml` — имя ресурса в classpath приложения, не путь в
файловой системе. ConfigMap, volume и volumeMount для логирования не нужны.

Если переменная `JAVA_OPTS` в манифесте уже задана — флаг нужно **добавить** к
существующему значению, а не заменить его. Точка входа образа (`/app/akhq`)
дописывает к `JAVA_OPTS` флаги из `/app/jvm.options` и передаёт результат в
`java`.

Без этой переменной приложение работает со штатным текстовым форматом —
поведение локальной разработки и docker-compose не меняется.

Если используется Helm chart, имя ключа для переменных окружения смотреть в его
`values.yaml` — обычно `extraEnv`.

## Формат события

Проверено запуском с этой конфигурацией:

```json
{"@timestamp":"2026-08-13T18:12:38.157+03:00","message":"hello json","logger_name":"org.akhq.Demo","thread_name":"main","level":"INFO","logType":"tech","app":"akhq"}
```

Запись с исключением получает дополнительное поле `stack_trace` — стектрейс
одной строкой с экранированными переводами строк, multiline-склейка на стороне
сборщика не нужна:

```json
{"@timestamp":"2026-08-13T18:12:38.166+03:00","message":"boom","logger_name":"org.akhq.Demo","thread_name":"main","level":"ERROR","stack_trace":"java.lang.IllegalStateException: bad\n\tat T.main(T.java:6)\n","logType":"tech","app":"akhq"}
```

Соответствие контракту пайплайна:

| Поле         | Значение                                                          |
|--------------|-------------------------------------------------------------------|
| `@timestamp` | ISO-8601 со смещением `+03:00`, часовой пояс задан явно в конфиге  |
| `message`    | строка с текстом сообщения, аргументы уже подставлены              |
| `logType`    | статическая строка `"tech"`                                        |
| `app`        | статическая строка `"akhq"`, для отбора событий сервиса            |

Свободные поля: `level`, `logger_name`, `thread_name`, `stack_trace`, а также
значения MDC — они добавляются в корень объекта. Поля `@version` и
`level_value`, которые энкодер пишет по умолчанию, отключены.

Одна строка JSON = одна строка stdout, pretty-print выключен.
`NopStatusListener` в конфигурации отсутствует.

## Как менять уровни логирования без релиза

Конфигурация лежит внутри образа, но уровни логгеров меняются в рантайме через
management-эндпоинт на порту `28081` — пересборка и перезапуск не нужны:

```bash
# посмотреть текущий уровень
kubectl exec deploy/akhq -- curl -s localhost:28081/loggers/org.akhq

# включить DEBUG
kubectl exec deploy/akhq -- curl -s -X POST localhost:28081/loggers/org.akhq \
  -H 'Content-Type: application/json' -d '{"configuredLevel":"DEBUG"}'

# вернуть обратно
kubectl exec deploy/akhq -- curl -s -X POST localhost:28081/loggers/org.akhq \
  -H 'Content-Type: application/json' -d '{"configuredLevel":"INFO"}'
```

Изменение действует до перезапуска пода. Штатные уровни: `root` — INFO,
`org.apache` и `io.micronaut` — WARN.

## Проверка после раскатки

```bash
kubectl logs deploy/akhq --tail=1 | jq '{"@timestamp", message, logType}'
```

Признаки успеха:

- `jq` разбирает каждую строку без ошибок
- все три поля непустые, `message` — строка, а не `null`
- событие доезжает до индекса `tech`, а не в `_unmatched`

Если логи остались текстовыми с ANSI-цветами — переменная окружения не доехала
до процесса:

```bash
kubectl exec deploy/akhq -- sh -c 'cat /proc/1/cmdline | tr "\0" " "'
```

Если в выводе есть `-Dlogback.configurationFile`, но формат текстовый — образ
собран не из ветки `release/ak-fork`, в нём нет файла конфигурации.

## Смежное наблюдение по общему трансформу Vector

Событие без поля `message` роняет обработку целиком — `string!(...)` возвращает
`expected string, got null`, и событие теряется, не попадая ни в `tech`, ни в
`trash`. Это касается любого сервиса namespace, а не только AKHQ: следующий
сервис с другим форматом даст тот же молчаливый дроп.

Задачу это не блокирует — конфигурация выше отдаёт `message` всегда. Но
защитный coalesce в трансформе (значение по умолчанию вместо падения) сделал бы
потерю логов заметной, а не молчаливой. Решение за владельцем трансформа.
