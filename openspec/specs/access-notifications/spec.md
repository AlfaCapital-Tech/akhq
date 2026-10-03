# access-notifications Specification

## Purpose
Оповещение о событиях самообслуживания доступов: email владельцам префикса (или суперадминам) о новом запросе на доступ и журналирование всех событий (создание, одобрение, отклонение, отзыв). Пользовательские данные в HTML-письмах экранируются.

## Requirements

### Requirement: Конфигурация уведомлений
Отправка email MUST управляться флагом `akhq.access-management.notifications.enabled` (по умолчанию `false`). Блок `notifications` SHALL также поддерживать `subject-prefix` (по умолчанию `[AKHQ]`) и необязательный `base-url`. Отправитель SHALL задаваться штатными ключами Micronaut Email `micronaut.email.from.email` и `micronaut.email.from.name`, SMTP — ключами `javamail.properties.mail.smtp.*` (`host`, `port`, при необходимости `auth`, `starttls.enable`).

#### Scenario: Уведомления выключены
- **WHEN** `notifications.enabled: false` и создаётся запрос на доступ
- **THEN** письмо не отправляется, событие только пишется в лог

#### Scenario: Уведомления включены без SMTP
- **WHEN** `notifications.enabled: true`, но бин `EmailSender` не создан
- **THEN** письмо не отправляется, в лог пишется WARN `notifications enabled but EmailSender bean not found`

### Requirement: Журналирование событий
Каждое событие MUST записываться в лог уровня INFO с префиксом `ACCESS-MANAGEMENT:` независимо от `notifications.enabled`: новый запрос (пользователь, роль, тип и значение цели, причина, список адресатов), одобрение (кто одобрил), отклонение (кто отклонил и причина), отзыв доступа (кто отозвал).

#### Scenario: Лог нового запроса
- **WHEN** `alice` запрашивает `READ` к топику `team-a.orders`
- **THEN** в логе есть строка `ACCESS-MANAGEMENT: New access request: user 'alice' requests 'READ' access to topic 'team-a.orders'. ...` с перечнем адресатов

#### Scenario: Лог отзыва
- **WHEN** владелец `owner-a` отзывает доступ `alice`
- **THEN** в логе есть строка `ACCESS-MANAGEMENT: Access revoked: ...` с указанием `owner-a`

### Requirement: Email только о новом запросе
Email SHALL отправляться только при создании запроса на доступ. Одобрение, отклонение и отзыв доступа MUST NOT порождать email — только запись в лог.

#### Scenario: Одобрение не шлёт письмо
- **WHEN** при включённых уведомлениях владелец одобряет запрос
- **THEN** ни заявителю, ни владельцам письмо не отправляется

### Requirement: Получатели письма о новом запросе
Для запроса на топик получателями SHALL быть email всех владельцев всех `prefix-owners`, чей `prefix` матчит имя топика; для запроса на префикс — email владельцев записи `prefix-owners` с в точности совпадающей строкой `prefix`. Владельцы без email пропускаются. Если получателей нет, письмо MUST уйти всем суперадминам, у которых задан email; если нет и их — письмо не отправляется.

#### Scenario: Письмо владельцам префикса
- **WHEN** для `team-a\..*` заданы владельцы `owner-a@example.com` и `owner-b@example.com`, и создаётся запрос на `team-a.orders`
- **THEN** письмо получают оба владельца

#### Scenario: Нет владельца
- **WHEN** создаётся запрос на топик, не покрытый ни одним `prefix-owners`
- **THEN** письмо получают суперадмины, например `admin@example.com`

### Requirement: Содержимое письма
Тема письма MUST иметь вид `<subject-prefix> New access request from <username>`. Письмо SHALL содержать HTML-часть и текстовую альтернативу с именем заявителя, ролью, типом цели (`topic` или `prefix`), целью и причиной (`-`, если причина не указана). Если задан `base-url`, письмо SHALL содержать ссылку `<base-url>/ui` (в HTML — «Review in AKHQ»); без `base-url` ссылки нет.

#### Scenario: Письмо со ссылкой
- **WHEN** `base-url: "https://akhq.example.com"` и `alice` запрашивает `READ` на префикс `team-a\..*`
- **THEN** тема — `[AKHQ] New access request from alice`, тело содержит `prefix`, `team-a\..*`, `READ` и ссылку `https://akhq.example.com/ui`

### Requirement: Экранирование пользовательских полей в HTML
В HTML-части письма все подставляемые значения — имя пользователя, роль, тип цели, цель (имя топика или префикс) и причина — MUST быть HTML-экранированы (`&`, `<`, `>`, `"`, `'`). Текстовая часть SHALL содержать значения без изменений.

#### Scenario: Попытка внедрить разметку
- **WHEN** причина запроса равна `<script>alert(1)</script>`
- **THEN** HTML-часть содержит `&lt;script&gt;alert(1)&lt;/script&gt;`, а текстовая часть — исходную строку

### Requirement: Устойчивость отправки
Письмо MUST отправляться отдельно каждому получателю. Ошибка отправки одному получателю SHALL логироваться уровнем ERROR и MUST NOT прерывать отправку остальным или создание запроса.

#### Scenario: SMTP недоступен
- **WHEN** при создании запроса отправка письма завершается исключением
- **THEN** запрос создан и API отвечает `200`, в логе есть ERROR `failed to send email to ...`
