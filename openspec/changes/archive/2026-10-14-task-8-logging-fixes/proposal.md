# Proposal

## Why

После вливания upstream (Kafka-клиент 4.3.1, Micronaut 5) на стенде с JSON-логами нашлись две проблемы.

- Kafka-клиент при каждом запросе списка consumer group пишет WARN `Not updating last stable offset ... as it is no longer assigned` (KAFKA-20449) на каждую партицию кластера. На стенде с ~4,5 тыс. партиций это около 7 тыс. строк на запрос и 8 МБ логов за минуту работы одного пользователя. Upstream приглушил этот логгер в `logback.xml`, а в `logback-json.xml` форка этой строки нет.
- При старте выводится `main ERROR Log4j API could not find a logging provider.`. Hibernate (access-management форка) пишет через jboss-logging, тот видит на classpath `log4j-api` и выбирает его. Провайдера у `log4j-api` нет: upstream исключил мост `log4j-to-slf4j`. В итоге INFO и WARN Hibernate теряются, а ERROR уходит в stderr простым текстом мимо JSON. То же происходит с другими библиотеками на Log4j API, например с клиентом ksqlDB.

## What Changes

- В `logback-json.xml` логгер `OffsetFetcherUtils` получает уровень ERROR, как в `logback.xml`
- В runtime добавлен мост `log4j-to-slf4j` (версия из BOM Micronaut), исключение этого модуля из всех конфигураций убрано: события Log4j API идут в logback
- Тест сверяет логгеры `logback-json.xml` с `logback.xml` и проверяет, что Log4j API пишет через SLF4J, чтобы следующее вливание upstream не разошлось молча

## Capabilities

### New Capabilities

Нет.

### Modified Capabilities

- `json-logging`: уровни логгеров JSON-конфигурации следуют штатному `logback.xml`, включая приглушение KAFKA-20449; логи библиотек на Log4j API выводятся через logback

## Impact

- Код: `build.gradle`, `src/main/resources/logback-json.xml`, новый тест `LoggingConfigTest`
- Прод: меньше логов на стендах с JSON-форматом; при старте в JSON появляются две INFO-строки Hibernate (версия и параметры БД)
