# Proposal

## Why

Локально полный прогон тестов стабильно красный, хотя на CI зелёный. Тестовая Kafka (`cp-kafka:8.1.0`) отвечает на создание топика раньше, чем топик появляется в метаданных брокера.

- `KafkaTestCluster` создаёт топики для Kafka Streams и сразу запускает Streams. Streams получает `MissingSourceTopicException` и останавливается, поэтому падают около 17 тестов, которые ждут данных от Streams: subject `stream-map-value`, служебные топики Streams, offset'ы группы `stream-test-example`.
- `TopicRepositoryTest.createWithConfig` сразу после создания топика запрашивает его конфиг и получает `UnknownTopicOrPartitionException`. Топик при этом не удаляется, и все повторы падают с `TopicExistsException`.

## What Changes

- `KafkaTestCluster`: пауза 2 с между созданием топиков и запуском Streams
- `createWithConfig`: пауза 2 с после создания топика и удаление топика в `finally`, как в соседнем `increasePartition`

Меняются только тесты, поведение приложения не меняется.

## Capabilities

### New Capabilities

Нет.

### Modified Capabilities

Нет.

## Impact

- Код: `src/test/java/org/akhq/KafkaTestCluster.java`, `src/test/java/org/akhq/repositories/TopicRepositoryTest.java` (оба файла из upstream)
- Локально полный прогон зелёный два раза подряд: 441 тест, без повторов
