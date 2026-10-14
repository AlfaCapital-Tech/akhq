## MODIFIED Requirements

### Requirement: Уровни логгеров по умолчанию
JSON-конфигурация MUST задавать те же уровни логгеров, что и штатный `logback.xml`: `root` — INFO, `org.apache` и `io.micronaut` — WARN, `io.micronaut.context.DefaultApplicationContext` и `io.micronaut.runtime.Micronaut` — INFO, `org.apache.kafka.clients.consumer.internals.OffsetFetcherUtils` — ERROR. Логгер, который появляется в штатном `logback.xml`, MUST появиться и в JSON-конфигурации с тем же уровнем.

#### Scenario: Шумные библиотеки приглушены
- **WHEN** логгер пакета `org.apache` пишет INFO-событие
- **THEN** событие не выводится

#### Scenario: Предупреждение KAFKA-20449 приглушено
- **WHEN** при чтении offset'ов consumer group Kafka-клиент пишет WARN `Not updating last stable offset ... as it is no longer assigned`
- **THEN** событие не выводится

## ADDED Requirements

### Requirement: Логи библиотек на Log4j API
События библиотек, которые пишут через Log4j API, в том числе Hibernate через jboss-logging, MUST проходить через logback и выводиться в том же формате и с теми же уровнями, что события остальных логгеров. При старте приложения MUST NOT выводиться ошибка Log4j об отсутствии провайдера.

#### Scenario: Событие Hibernate в JSON
- **WHEN** в JSON-режиме при старте Hibernate пишет INFO-событие о версии
- **THEN** в stdout выводится JSON-событие с `logger_name` из пакета `org.hibernate`

#### Scenario: Нет ошибки Log4j при старте
- **WHEN** приложение запускается
- **THEN** в выводе нет строки `Log4j API could not find a logging provider`
