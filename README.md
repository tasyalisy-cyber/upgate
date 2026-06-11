# Upgate API tests

Kotlin/JUnit 5 автотесты для API регистрации пользователей.

## Что проверяется

- `GET /user/get` - получение списка зарегистрированных пользователей.
- `POST /user/create` - регистрация пользователя.

В условии задачи для `POST /user/create` указан `FormData` с полями `username`, `email`, `password`. Для HTTP-запроса это соответствует `multipart/form-data`, поэтому основной акцент автотестов сделан на этом формате.

При исследовании API также подтверждено, что endpoint принимает raw `application/json` и `application/x-www-form-urlencoded`. Raw JSON покрыт отдельным автотестом как фактическое поведение API, но не как основной контракт из задачи. `application/x-www-form-urlencoded` зафиксирован в тест-кейсах, но пока не автоматизирован.

API не содержит endpoint для удаления пользователей, поэтому автотесты создают уникальных пользователей при каждом запуске.
Для регулярных прогонов нужна отдельная стратегия очистки тестовых данных или изолированная тестовая БД.

Это live integration tests против предоставленного общего стенда. Mock/WireMock намеренно не используется: цель задания - проверить фактическое поведение API по выданному URL, а не локальную имитацию сервиса.

## Запуск

Требования:

- JDK 21+
- Gradle Wrapper из репозитория

Windows:

```powershell
.\gradlew.bat test -DbaseUrl=http://18.194.45.232:3333
```

Linux/macOS:

```bash
./gradlew test -DbaseUrl=http://18.194.45.232:3333
```

Или через переменную окружения:

```powershell
$env:BASE_URL = "http://18.194.45.232:3333"
.\gradlew.bat test
```

Если `baseUrl`/`BASE_URL` не заданы, тесты завершаются с явной ошибкой конфигурации.

Multipart request body сейчас собирается минимальным builder'ом для текстовых полей `username`, `email`, `password`. Если API начнет принимать файлы или binary parts, builder стоит заменить на специализированную multipart-библиотеку.

## Состав проекта

- [docs/test-cases.md](docs/test-cases.md) - исследование API, чек-лист, приоритеты, найденные дефекты
- [src/test/kotlin/qa/upgate/tests/UserApiTest.kt](src/test/kotlin/qa/upgate/tests/UserApiTest.kt) - Kotlin/JUnit 5 сценарии, сгруппированные через `@Nested`
- [src/test/kotlin/qa/upgate/support](src/test/kotlin/qa/upgate/support) - общая тестовая инфраструктура: конфигурация и extension assertions
- [src/test/kotlin/qa/upgate/fixtures/UserFixtures.kt](src/test/kotlin/qa/upgate/fixtures/UserFixtures.kt) - генерация payload и API fixture helper для создания пользователя через live API
- [src/main/kotlin/qa/upgate/api](src/main/kotlin/qa/upgate/api) - HTTP client, response wrapper, multipart builder
- [src/main/kotlin/qa/upgate/model](src/main/kotlin/qa/upgate/model) - request/response DTO
- [src/test/kotlin/qa/upgate/diagnostics](src/test/kotlin/qa/upgate/diagnostics) - diagnostics extension and HTTP exchange logging

## Известные дефекты

- `GET /user/get` возвращает поле `password` с хешем пароля для каждого пользователя.
- `POST /user/create` возвращает поле `details.password` с хешем пароля.
- Email невалидного формата, например `not-an-email`, принимается с `200 OK`.
- `GET /user/get` доступен без авторизации; если список пользователей должен быть закрытым, это security defect.
- Query-параметры `GET /user/get` могут приводить к `500` и раскрытию SQL details.

Тесты на известные дефекты и security requirement gaps добавлены с `@Disabled`, чтобы основной live-прогон фиксировал текущее состояние API без ожидаемо красных проверок.
Зеленый happy-path проверяет, что API не возвращает исходный plaintext password, но не требует наличия password hash в ответе.

## Последний локальный прогон

После локального запуска HTML-отчет Gradle доступен в `build/reports/tests/test/index.html`. XML-результаты JUnit находятся в `build/test-results/test`.
TBD Разметка и репортинг в Allure

## Диагностика

При падении теста JUnit extension выводит в лог последние HTTP request/response для этого теста. Значения `password` редактируются перед выводом.
