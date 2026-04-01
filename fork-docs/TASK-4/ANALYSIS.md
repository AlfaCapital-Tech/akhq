# TASK-4: Email-уведомления при запросе доступов

## Проблема

При создании запроса на доступ владелец префикса узнаёт о нём только если зайдёт в AKHQ и проверит вкладку Pending. Нет активного оповещения — запросы могут висеть долго.

## Решение

При создании запроса на доступ отправляется email владельцам соответствующего префикса. Если владельцы не найдены — письмо уходит super-admin'ам.

## Реализация

### Зависимости (build.gradle)

```groovy
implementation("io.micronaut.email:micronaut-email-javamail")
runtimeOnly("org.eclipse.angus:angus-mail")
```

`micronaut-email-javamail` — модуль Micronaut для отправки email через JavaMail API.
`angus-mail` — runtime-реализация Jakarta Mail (без неё `EmailSender` бин не работает).

### Конфигурация (application.yml)

```yaml
akhq:
  access-management:
    notifications:
      enabled: true                              # false = только лог, email не шлётся
      subject-prefix: "[AKHQ]"                   # префикс темы письма
      base-url: "https://akhq.company.ru"        # URL для ссылки в письме

micronaut:
  email:
    from:
      email: "akhq@company.ru"                   # адрес отправителя
      name: "AKHQ Access Management"             # имя отправителя

javamail:
  properties:
    mail:
      smtp:
        host: "smtp.company.ru"                  # SMTP-сервер
        port: 25                                 # порт (25 без SSL, 587 с STARTTLS)
        # auth: true                             # если нужна аутентификация
        # starttls.enable: true                  # если нужен STARTTLS
```

### Что отправляется

| Событие | Email | Получатели |
|---------|-------|------------|
| Новый запрос на доступ | Да | Владельцы префикса (или super-admins) |
| Одобрение | Нет (только лог) | — |
| Отклонение | Нет (только лог) | — |
| Отзыв доступа | Нет (только лог) | — |

### Пример письма

```
From: AKHQ Access Management <akhq@company.ru>
To: ivanov@company.ru
Subject: [AKHQ] New access request from petrov

User 'petrov' requests 'READ' access to prefix 'eis\..*'.

Reason: Нужен доступ для отладки интеграции с EIS

Review: https://akhq.company.ru/ui
```

### Локальная разработка

Для тестирования email используется Mailpit (mock SMTP-сервер):

```yaml
# docker-compose-dev.yml / docker-compose-local.yml
mailpit:
  image: axllent/mailpit:latest
  restart: unless-stopped
  ports:
    - "1025:1025"   # SMTP
    - "8025:8025"   # Web UI для просмотра писем
```

Конфиг для dev:
```yaml
javamail:
  properties:
    mail:
      smtp:
        host: mailpit    # имя контейнера (для docker-compose-dev)
        port: 1025
```

Конфиг для local (backend вне Docker):
```yaml
javamail:
  properties:
    mail:
      smtp:
        host: localhost
        port: 1025
```

Web UI Mailpit: http://localhost:8025

### Файлы

- `AccessManagementProperties.NotificationProperties` — конфиг (`enabled`, `subjectPrefix`, `baseUrl`)
- `NotificationService` — отправка email через `EmailSender` из micronaut-email
- `build.gradle` — зависимости `micronaut-email-javamail` + `angus-mail`
- `docker-compose-dev.yml`, `docker-compose-local.yml` — контейнер Mailpit
- `application-dev.yml`, `application-local.yml` — SMTP-конфиг
