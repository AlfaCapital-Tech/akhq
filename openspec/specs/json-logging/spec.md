# json-logging Specification

## Purpose
Опциональный вывод логов приложения в stdout в виде JSON-событий для сбора в Kubernetes (Vector → OpenSearch). Включается параметром JVM без пересборки образа; без него сохраняется штатный текстовый формат AKHQ.

## Requirements

### Requirement: Включение JSON-формата
JSON-формат MUST включаться системным свойством JVM `-Dlogback.configurationFile=logback-json.xml`, где `logback-json.xml` — ресурс в classpath приложения, поставляемый в образе (в Kubernetes — через переменную окружения `JAVA_OPTS`, дополняя её текущее значение). Без этого свойства приложение SHALL использовать штатный `logback.xml` без изменений: текстовый формат, INFO и ниже — в stdout, WARN и ERROR — в stderr.

#### Scenario: Включение в Deployment
- **WHEN** контейнер запущен с `JAVA_OPTS=-Dlogback.configurationFile=logback-json.xml`
- **THEN** каждая строка stdout — валидный JSON-объект

#### Scenario: Без параметра
- **WHEN** приложение запущено без `-Dlogback.configurationFile`
- **THEN** логи выводятся в штатном текстовом формате, как в оригинальном AKHQ

### Requirement: Формат JSON-события
В JSON-режиме каждое событие любого уровня MUST выводиться в stdout одной строкой без pretty-print. Событие SHALL содержать обязательные поля `@timestamp` (ISO-8601 со смещением, часовой пояс `Europe/Moscow`), `message` (строка с подставленными аргументами), `logType` со значением `"tech"` и `app` со значением `"akhq"`, а также `level`, `logger_name`, `thread_name` и значения MDC в корне объекта. Поля `@version` и `level_value` и свойства контекста logback MUST NOT выводиться.

#### Scenario: Обычное событие
- **WHEN** логгер `org.akhq.Example` пишет INFO `hello json`
- **THEN** в stdout выводится строка вида `{"@timestamp":"2026-01-01T12:00:00.000+03:00","message":"hello json","logger_name":"org.akhq.Example","thread_name":"main","level":"INFO","logType":"tech","app":"akhq"}`

#### Scenario: Ошибки тоже идут в stdout
- **WHEN** в JSON-режиме пишется событие уровня ERROR
- **THEN** оно выводится в stdout, а не в stderr

### Requirement: Исключения в одном событии
Если событие содержит исключение, JSON-объект MUST содержать поле `stack_trace` со стектрейсом одной строкой (переводы строк экранированы), чтобы сборщику не требовалась multiline-склейка.

#### Scenario: Событие с исключением
- **WHEN** логируется ERROR с исключением `IllegalStateException`
- **THEN** выводится одна строка JSON, где `stack_trace` начинается с `java.lang.IllegalStateException`

### Requirement: Уровни логгеров по умолчанию
JSON-конфигурация MUST задавать те же уровни по умолчанию, что и штатная: `root` — INFO, `org.apache` и `io.micronaut` — WARN, `io.micronaut.context.DefaultApplicationContext` и `io.micronaut.runtime.Micronaut` — INFO.

#### Scenario: Шумные библиотеки приглушены
- **WHEN** логгер пакета `org.apache` пишет INFO-событие
- **THEN** событие не выводится
