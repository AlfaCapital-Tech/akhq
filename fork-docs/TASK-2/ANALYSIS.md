# TASK-2: Баги и улучшения по обратной связи со стенда

## Источник

Обратная связь от команды после развёртывания TASK-1 на стенде (2026-03-17).

## Задачи

### 2.1 Баг: case-sensitivity username при матчинге owner

**Приоритет:** критичный

**Проблема:** user-a залогинился через LDAP (sAMAccountName = `user-a`), является owner `atom.*`, но не видит pending-запросы в Manage Accesses. В конфиге прописано `User-A`, LDAP возвращает `user-a` — сравнение case-sensitive, матч не проходит.

**Лог:**
```
NotificationService ACCESS-MANAGEMENT: New access request: user 'user-b' requests 'READ' access to topic 'atom.example.email.outbox'. Notify: User-A@example.com
```
Запрос создан, но owner его не видит в UI.

**Решение:** сравнение username делать case-insensitive (equalsIgnoreCase) во всех местах: контроллер (фильтрация pending/accesses), определение isOwner/isSuperAdmin.

---

### 2.2 Баг: запрос на топик без owner

**Приоритет:** высокий

**Проблема:** user-b отправил запрос на топик с префиксом `aedo.*`, для которого не настроен owner в `prefix-owners`. Запрос успешно создан, но некому его аппрувить — он зависнет в статусе Pending навсегда.

**Решение (варианты):**
- A) Запретить создание запроса если owner не найден (показать ошибку в UI)
- B) Назначать запрос на super-admins если owner не найден
- C) Оба варианта: запрос уходит на super-admins + предупреждение в UI что owner не назначен

---

### 2.3 Floating кнопка "Request Access"

**Приоритет:** средний

**Проблема:** кнопка "Request Access" прибита внизу страницы топика, при прокрутке не видна. Пользователи её не замечают.

**Решение:** сделать кнопку floating — фиксированная позиция справа внизу экрана (аналог кнопки чата на сайтах), видна при любой прокрутке.

---

### 2.4 Бейджик количества pending-запросов в сайдбаре

**Приоритет:** средний

**Проблема:** owner не знает что ему пришли запросы, пока не зайдёт в Manage Accesses.

**Решение:** на пункте "Access" в сайдбаре показывать badge с количеством pending-запросов, которые текущий пользователь может аппрувить. Нужен новый эндпоинт или расширение `/api/auths`.

---

### 2.5 Email-уведомления о запросах

**Приоритет:** средний

**Проблема:** owner не получает уведомлений о новых запросах. `NotificationService` уже логирует события, но `notifications.enabled=false`.

**Статус:** отложено в отдельную задачу (требует отладки интеграции Micronaut Email + @Replaces)

**Решение:** реализовать отправку email через Micronaut Email (micronaut-email-javamail). Шаблоны уведомлений:
- Новый запрос (owner'у)
- Запрос одобрен (заявителю)
- Запрос отклонён с причиной (заявителю)
- Доступ отозван (пользователю)

**Пример конфига для ArgoCD values.yaml (DevOps):**
```yaml
configuration:
  akhq:
    access-management:
      notifications:
        enabled: true
        from: "akhq@company.com"

secrets:
  javamail:
    authentication:
      username: ${SMTP_USERNAME}   # из Vault, опционально
      password: ${SMTP_PASSWORD}   # из Vault, опционально
    properties:
      mail:
        smtp:
          host: "smtp.company.com"
          port: 587
          starttls:
            enable: true
```

Если `enabled: false` или `EmailSender` не сконфигурирован (нет javamail секции) — email не отправляется, только логирование.

---

## Конфигурация стенда

- Кластер: dev
- LDAP: sAMAccountName авторизация, два контроллера домена
- access-management: enabled, один super-admin, prefix-owner atom.*
- Requestable roles: READ (topic-data-read), WRITE (topic-data-write)
- Notifications: disabled
