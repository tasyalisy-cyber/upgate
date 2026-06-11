# Тест-кейсы для API регистрации пользователей

Первичное исследование: 2026-06-09. Уточнение форматов, границ и нормализации: 2026-06-10. Актуализация чек-листа и автотестов: 2026-06-11.

Base URL: `http://18.194.45.232:3333`

## Исследованное поведение API

### Общие concerns

- Текущий Base URL использует `http`, а `POST /user/create` передает `email` и `password`. Для production-среды регистрация должна выполняться по HTTPS/TLS; для тестового стенда это зафиксировано как security concern, а не как отдельный функциональный test case.
- Так как endpoint удаления пользователя не описан, автотесты загрязняют тестовую среду новыми пользователями. Для регулярных прогонов нужна стратегия очистки данных: отдельная тестовая БД, seed/reset перед запуском или прямой cleanup-скрипт для таблицы пользователей.
- CORS имеет смысл проверять только если API должен вызываться из браузера с другого origin. В текущем задании браузерный клиент не описан, поэтому CORS зафиксирован как integration concern, а не как бизнес-кейс регистрации.

### GET `/user/get`

Фактический ответ:

- Status: `200 OK`
- Content-Type: `application/json; charset=utf-8`
- Body: JSON-массив
- Поля объекта пользователя: `id`, `username`, `email`, `password`, `created_at`, `updated_at`
- Поле `password` фактически возвращается как hash, но это рассматривается как security defect. Активная schema-проверка не закрепляет `password` как обязательное публичное поле.

Риски и открытые вопросы:

- Endpoint возвращает хеши паролей в публичном списке пользователей.
- Endpoint доступен без токена авторизации. Если список пользователей не должен быть публичным, ожидаемый ответ для anonymous-запроса - `401 Unauthorized` или `403 Forbidden`.
- Query-параметры пагинации не поддерживаются корректно: `GET /user/get?page=1&limit=1` возвращает `500 Internal Server Error`.
- В теле ответа на `GET /user/get?page=1&limit=1` API раскрывает SQL-ошибку: `select * from users where page = '1' and limit = '1' - SQLITE_ERROR: no such column: page`. По этому тексту видно, что `page` и `limit` интерпретируются не как параметры пагинации.
- Раскрытие SQL-ошибки на query-параметрах повышает риск SQL injection и требует отдельной P0-проверки с SQLi payload.
- Если пагинации нет, при большом количестве пользователей endpoint может начать отвечать слишком долго, падать по timeout или создавать избыточную нагрузку на память сервера.

### POST `/user/create`

Формат из условия задачи: `FormData` с полями `username`, `email`, `password`. Для HTTP-запроса это соответствует `multipart/form-data`.

Основной контракт для автотестов: `multipart/form-data`, потому что именно этот формат указан в задаче.

Фактически endpoint также принимает:

- raw `application/json`
- `application/x-www-form-urlencoded`

Эти форматы стоит фиксировать как фактическое поведение API, но они не должны подменять основной приоритет проверки `FormData`-контракта.

Успешная регистрация:

- Status: `200 OK`
- Body содержит `success: true`
- Body содержит `details.id`, `details.username`, `details.email`, `details.created_at`, `details.updated_at`
- На текущем стенде body также содержит `details.password` с хешем пароля; это зафиксировано как security defect, а не как желаемый контракт.
- Body содержит сообщение о создании пользователя; на текущем стенде наблюдается текст `User Successully created`
- Активные автотесты проверяют, что исходный plaintext password не возвращается. Они намеренно не требуют наличия хеша в публичном ответе, чтобы тесты не ломались после исправления security-дефекта.

Контрактное замечание: текущее успешное создание пользователя возвращает `200 OK`. Для операции создания ресурса ожидаемым REST-статусом обычно является `201 Created`; при наличии endpoint для получения пользователя по id также уместен заголовок `Location`. Так как отдельный контракт API не предоставлен, автотесты фиксируют фактический `200 OK`, а вопрос статуса вынесен в checklist как contract discussion.

Фактически проверенная валидация:

- Отсутствующий или пустой `username`: `400 Bad Request`, `success=false`, message упоминает `username`
- Отсутствующий или пустой `email`: `400 Bad Request`, `success=false`, message упоминает `email`
- Отсутствующий или пустой `password`: `400 Bad Request`, `success=false`, message упоминает `password`
- Дубликат `username`: `400 Bad Request`, `success=false`, message упоминает `username`
- Дубликат `email`: `400 Bad Request`, `success=false`, message упоминает `email`

Фактически проверенные границы и нормализация:

- Минимальные непустые значения принимаются: `username` длиной 1 символ, `email` длиной 1 символ, `password` длиной 1 символ.
- Максимальные ограничения в проверенном диапазоне не найдены: `username`, `email`, `password` длиной 10 000 символов принимаются с `200 OK`.
- Слабый пароль `123456` принимается с `200 OK`; явная password policy не обнаружена.
- `password` из одних пробелов трактуется как пустой: `400`, `success=false`, message упоминает `password`.
- `username` и `email` из одних пробелов трактуются как пустые: `400`, `success=false`, message упоминает соответствующее поле.
- Пробелы в начале/конце `username` и `email` не trim-ятся, а сохраняются как часть значения.
- Unicode принимается в `username`, `email`, `password`.
- Уникальность `username` и `email` чувствительна к регистру: значения, отличающиеся только case, создаются как разные пользователи.

Найденные дефекты:

- Email невалидного формата, например `not-an-email`, принимается с `200 OK`.
- Raw `application/json` с числовыми значениями в `username`, `email`, `password` принимается как валидный запрос. Ожидаемо API должен требовать строковые значения и возвращать `400` при несовпадении типов.
- `email` длиной 1 символ и 10 000 символов принимается с `200 OK`; формат и длина email не валидируются.
- `username` и `password` длиной 10 000 символов принимаются с `200 OK`; max length validation не обнаружена.
- Слабый пароль и пароль длиной 1 символ принимаются с `200 OK`; password policy не обнаружена.
- `GET /user/get?page=1&limit=1` возвращает `500 Internal Server Error` и раскрывает SQL-ошибку с деталями запроса к базе.
- Хеш пароля возвращается в ответе создания пользователя и в списке пользователей.
- `GET /user/get` возвращает полный список пользователей без авторизации; если endpoint должен быть закрытым, это P0 security defect.

Требования, которые нужно продуктово согласовать:

- Ожидаемые min/max длины для `username`, `email`, `password`.
- Password policy: минимальная длина, сложность, допустимые символы.
- Нужно ли trim-ить `username` и `email` перед сохранением.
- Должны ли `username` и `email` быть case-insensitive для уникальности.
- Допустим ли Unicode в `username`, `email`, `password`.
- Нужен ли endpoint обновления пользователя (`PATCH`/`PUT`): сейчас публично описаны только `POST /user/create` и `GET /user/get`, поэтому изменение `updated_at` после update полноценно проверить нельзя. В автотестах проверяется только create-сценарий, где `created_at` и `updated_at` должны быть валидными и равными.
- Должен ли `id` быть строго последовательным (`previous id + 1`) или достаточно положительного уникального значения. В автотестах проверяется только положительность и уникальность `id`, чтобы не делать тест flaky на общей среде.
- Какой должна быть стратегия очистки тестовых данных при отсутствии публичного `DELETE` endpoint.
- Должен ли API учитывать `Accept` header, например возвращать `406 Not Acceptable` для `Accept: application/xml` или всегда отвечать JSON независимо от `Accept`.

## Легенда приоритетов

- P0 - критичные проверки контракта из задачи, happy path, целостности данных, безопасности и базовой валидации
- P1 - важные проверки границ, масштабируемости, дополнительных форматов и стабильности поведения
- P2 - расширенные проверки безопасности и защиты бизнес-операций от злоупотреблений

## Приоритизированный чек-лист

| ID | Priority | Area | Проверка | Ожидаемый результат | Автоматизация |
|---|---:|---|---|---|---|
| TC-001 | P0 | Create | Создать пользователя с уникальными валидными `username`, `email`, `password` через `multipart/form-data` | `200`, `success=true`, поля пользователя совпадают с запросом, `id` присутствует | Automated |
| TC-002 | P0 | Create/Get | Созданный через `multipart/form-data` пользователь появляется в `GET /user/get` | Список содержит пользователя с `id`, который вернулся в create response; `username` и `email` у найденного объекта совпадают с запросом | Automated |
| TC-003 | P0 | Get | Получить список пользователей | `200`, JSON-массив, стабильная схема публичных non-sensitive полей (`id`, `username`, `email`, `created_at`, `updated_at`). Наличие `password` не закрепляется как обязательный контракт | Automated |
| TC-004 | P0 | Validation | Не передать `username` в `multipart/form-data` | `400`, `success=false`, непустое сообщение упоминает `username` | Automated |
| TC-005 | P0 | Validation | Не передать `email` в `multipart/form-data` | `400`, `success=false`, непустое сообщение упоминает `email` | Automated |
| TC-006 | P0 | Validation | Не передать `password` в `multipart/form-data` | `400`, `success=false`, непустое сообщение упоминает `password` | Automated |
| TC-007 | P0 | Validation | Передать пустые `username`, `email`, `password` в `multipart/form-data` | `400`, `success=false`, непустое сообщение упоминает соответствующее поле | Automated |
| TC-008 | P0 | Uniqueness | Создать пользователя с уже существующим `username` и новым `email` | `400`, `success=false`, сообщение упоминает `username` | Automated |
| TC-009 | P0 | Uniqueness | Создать пользователя с уже существующим `email` и новым `username` | `400`, `success=false`, сообщение упоминает `email` | Automated |
| TC-010 | P0 | Security | Проверить, что plaintext password не возвращается в ответе | В ответе нет исходного пароля; тест не требует, чтобы API возвращал hash | Automated |
| TC-011 | P0 | Security | Проверить, что password hash не раскрывается в публичных ответах | Поле `password` отсутствует в `GET` и create response | Disabled known-defect test |
| TC-012 | P0 | Validation | Передать email невалидного формата | `400`, ошибка формата email | Disabled known-defect test |
| TC-013 | P0 | Security | Проверить доступ к `GET /user/get` без авторизации | `401`/`403` или явно задокументированный публичный доступ без sensitive fields | Disabled requirement-gap test |
| TC-014 | P0 | Security | Передать SQLi payload в query-параметры `GET /user/get` | Нет `5xx`, SQL details не раскрываются, данные не возвращаются сверх ожидаемого доступа | Disabled known-defect test |
| TC-015 | P0 | Data | Проверить формат `created_at` и `updated_at` | В create response валидный timestamp; при создании `created_at` и `updated_at` равны. Сравнение с HTTP `Date` header не используется, потому что API timestamp не содержит timezone. Изменение `updated_at` при update не проверяется, так как endpoint обновления не описан | Automated |
| TC-016 | P1 | Create | Создать пользователя через raw `application/json` | `200`, поведение совпадает с `multipart/form-data` для тех же строковых полей | Automated |
| TC-017 | P1 | Validation | Передать numeric values в `username`, `email`, `password` через raw `application/json` | `400`, ошибка типа/формата; API не должен молча принимать number вместо string | Planned |
| TC-018 | P1 | Contract | Создать пользователя через `application/x-www-form-urlencoded` | `200`, поведение совпадает с другими поддержанными форматами | Planned |
| TC-019 | P1 | Contract | Проверить HTTP status успешного `POST /user/create` | API возвращает `201 Created` для нового ресурса или контракт явно фиксирует использование `200 OK`; при возможности возвращается `Location` | Planned/Contract discussion |
| TC-020 | P1 | Scalability | Проверить, поддерживает ли `GET /user/get` query-параметры пагинации, например `?page=1&limit=20` | API возвращает ограниченный набор данных или контролируемый `4xx`; `500` и SQL details недопустимы | Manual checked / Known defect |
| TC-021 | P1 | Scalability | Проверить поведение `GET /user/get` при очень большом количестве пользователей | Нет timeout/OOM/`5xx`; при отсутствии пагинации риск зафиксирован как архитектурный | Planned/Manual |
| TC-022 | P1 | Validation | Передать whitespace-only значения в `username`, `email`, `password` | `400`, `success=false`, message упоминает соответствующее поле; фактически API трактует такие значения как пустые | Manual checked |
| TC-023 | P1 | Validation | Передать значения с пробелами в начале/конце | Значения trim-ятся или отклоняются согласно правилам API | Manual checked / Behavior to clarify |
| TC-024 | P1 | Validation | Уточнить и проверить min/max длину `username` | Сейчас требования не определены; после согласования границ API должен отклонять значения вне диапазона контролируемым `4xx`, а не `5xx` | Manual checked up to 10 000 / Requirement needed |
| TC-025 | P1 | Validation | Уточнить и проверить min/max длину `email` | Сейчас требования не определены; после согласования границ API должен отклонять значения вне диапазона контролируемым `4xx`, а не `5xx` | Manual checked up to 10 000 / Requirement needed |
| TC-026 | P1 | Validation | Уточнить и проверить min/max длину `password` | Сейчас требования не определены; после согласования границ API должен отклонять значения вне диапазона контролируемым `4xx`, а не `5xx` | Manual checked up to 10 000 / Requirement needed |
| TC-027 | P1 | Validation | Передать экстремально длинный `username`, например 10 000 символов | Контролируемый `400` или `413`, без `500 Internal Server Error` | Manual checked / Behavior to clarify |
| TC-028 | P1 | Validation | Передать экстремально длинный `email`, например 10 000 символов | Контролируемый `400` или `413`, без `500 Internal Server Error` | Manual checked / Requirement needed |
| TC-029 | P1 | Validation | Передать экстремально длинный `password`, например 10 000 символов | Контролируемый `400` или `413`, без `500 Internal Server Error` | Manual checked / Behavior to clarify |
| TC-030 | P1 | Validation | Передать слабый пароль | Отклоняется, если password policy существует; если policy нет, риск зафиксирован | Manual checked / Known defect or requirement gap |
| TC-031 | P1 | Validation | Проверить case sensitivity email | Фактически API создает второго пользователя, если email отличается только регистром. Нужно согласовать бизнес-правило: либо email нормализуется/сравнивается case-insensitive и второй запрос получает `400`, либо case-sensitive уникальность явно документируется | Manual checked / Behavior to clarify |
| TC-032 | P1 | Validation | Проверить case sensitivity username | Фактически API создает второго пользователя, если username отличается только регистром. Нужно согласовать бизнес-правило: либо username нормализуется/сравнивается case-insensitive и второй запрос получает `400`, либо case-sensitive уникальность явно документируется | Manual checked / Behavior to clarify |
| TC-033 | P1 | Validation | Передать Unicode в username/email/password | Принимается или отклоняется согласно правилам API | Manual checked / Behavior to clarify |
| TC-034 | P1 | Contract | Передать неподдержанный `Content-Type`, например `text/plain` | Контролируемый `400` или `415`, без debug HTML и stack trace | Planned |
| TC-035 | P1 | Contract | Проверить HTTP methods вне описанного контракта: для `/user/create` вызвать `GET`, `PUT`, `PATCH`, `DELETE`; для `/user/get` вызвать `POST`, `PUT`, `PATCH`, `DELETE` | `405 Method Not Allowed` или другой явно задокументированный `4xx`; запрос не создает и не изменяет пользователей, нет `5xx`, debug HTML или stack trace | Planned |
| TC-036 | P1 | Contract | Проверить Content-Type error response | Ошибки возвращаются в JSON, а не HTML debug page | Planned |
| TC-037 | P1 | Contract | Проверить обработку `Accept` header: отправить запрос с `Accept: application/xml`, когда API фактически возвращает JSON | API либо возвращает `406 Not Acceptable`, если XML не поддерживается, либо документированно возвращает JSON с `Content-Type: application/json`; не должно быть HTML/debug response или `5xx` | Planned / Requirement needed |
| TC-038 | P1 | Data | Создать двух пользователей и проверить `id` в create response | Оба `id` присутствуют, положительные и отличаются друг от друга. Это дополняет `TC-001`, где проверяется только успешное создание одного пользователя и наличие `id` | Automated |
| TC-039 | P1 | Concurrency | Два параллельных create с одинаковым username/email | Один запрос успешен, второй получает ошибку уникальности | Planned |
| TC-040 | P2 | Security | Передать XSS payload в username/email | Значение экранируется или безопасно отклоняется | Planned |
| TC-041 | P2 | Security | Проверить rate limiting | Массовые create-запросы ограничиваются | Manual/Planned |

## Автоматизированный набор

Автотесты покрывают наиболее приоритетные и стабильные проверки:

- основной контракт из условия задачи: создание пользователя через `multipart/form-data`
- появление созданного через `multipart/form-data` пользователя в `GET /user/get`
- базовую схему ответа `GET /user/get` без закрепления `password` как обязательного публичного поля
- обязательные поля в `multipart/form-data`
- уникальность `username` и `email`
- отсутствие plaintext password в ответе; раскрытие hash проверяется отдельным disabled-тестом как known defect
- формат `created_at`/`updated_at` и их равенство при создании пользователя
- положительность и уникальность `id`
- raw `application/json` как дополнительное фактически поддержанное поведение, но не как основной контракт

Для негативных проверок автотесты не завязаны на точные серверные фразы. Они проверяют `400`, `success=false`, непустое сообщение и наличие смыслового ключевого слова (`username`, `email`, `password`).

Тесты на известные дефекты и security requirement gaps добавлены как `@Disabled`, чтобы их можно было включить после исправления API или уточнения требований.
