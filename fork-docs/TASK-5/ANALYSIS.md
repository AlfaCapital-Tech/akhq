# TASK-5: Фикс падения страницы ACL при не-Latin1 символах в principal

## Проблема

На dev-контуре при открытии страницы `/ui/{cluster}/acls` падает с ошибкой:

```
Uncaught (in promise) InvalidCharacterError: Failed to execute 'btoa' on 'Window':
The string to be encoded contains characters outside of the Latin1 range.
    at Acls.jsx:46:30
    at Array.map (<anonymous>)
    at f_.handleData (Acls.jsx:45:26)
    at f_.getAcls (Acls.jsx:41:10)
```

На локалке не воспроизводится — там ACL содержат только ASCII-принципалы.
На dev-контуре в Kafka ACL через LDAP попадают принципалы с кириллицей
(например `User:CN=Иванов Иван,OU=Users,DC=corp,...`).

## Причина

`btoa()` в браузере — legacy-функция для base64, работает только с Latin1 (код 0-255).
Любой Unicode символ (кириллица, emoji и т.д.) → `InvalidCharacterError`.

Principal кодируется в base64 потому что идёт в URL path
(`/ui/{cluster}/acls/{principalEncoded}`) — принципалы содержат `:`, `=`, `/`, `,`.

Симметричная проблема у `atob()` на странице деталей ACL.

## Решение

Заменить `btoa`/`atob` на UTF-8-safe версии через `TextEncoder`/`TextDecoder`:

```js
export function encodeBase64Utf8(str) {
  const bytes = new TextEncoder().encode(str);
  let binary = '';
  bytes.forEach(b => binary += String.fromCharCode(b));
  return btoa(binary);
}

export function decodeBase64Utf8(str) {
  const bytes = Uint8Array.from(atob(str), c => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}
```

### Файлы

- `client/src/utils/functions.jsx` — добавить 2 функции
- `client/src/containers/Acl/AclList/Acls.jsx:46` — заменить `btoa`
- `client/src/containers/Acl/AclDetail/AclDetails.jsx:70` — заменить `atob`

### Backend

Не трогаем. `AccessControl.decodePrincipal`:

```java
return new String(Base64.getDecoder().decode(encodedPrincipal));
```

`new String(bytes)` без charset использует JVM default. На Linux + Java 18+
это UTF-8 — корректно декодирует наши UTF-8 байты.

## Анализ рисков

Экран ACL в AKHQ — **строго read-only**. В `AclsController` только `@Get` эндпоинты,
никаких `@Post/@Put/@Delete`. Операций записи/удаления нет, сломать данные невозможно.

### Что меняется

| Кейс | Старое поведение | Новое поведение |
|------|------------------|-----------------|
| ASCII principal (`User:CN=admin`) | Работало | Работает идентично (байт в байт) |
| Unicode (кириллица) | Падение `InvalidCharacterError` | Работает |
| Latin1 non-ASCII (`é`, `©`) | btoa кодировал, но backend интерпретировал как mojibake | Работает корректно |

### Совместимость со старыми URL

Никаких рабочих URL с Unicode principal не существовало —
`btoa` падал **до** генерации URL, закладки сломать невозможно.

Для ASCII-принципалов URL бит-в-бит идентичны старым.

## Вердикт

Безопасная доработка. Для большинства кейсов поведение идентично,
для Unicode — починка падения страницы.
