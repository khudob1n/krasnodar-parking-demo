# Парковки Краснодара — демо

Демонстрационная версия приложения для муниципальных платных парковок
Краснодара: Android на Jetpack Compose и сервер каталога на Go.

> Это не официальный сервис города. Демо не проводит платежи и не оплачивает
> муниципальную парковку. Данные о парковках взяты из публичного ответа
> портала parkingkrd.ru и могут быть неполными или устаревшими.

## Структура

```
data/
  raw/          сырые ответы портала + *.meta.json (source_url, fetched_at, sha256)
  reference/    контрольные карточки из независимых публикаций
  snapshot/     результат импорта: parkings.json, parkings.geojson, validation-report.json
schema/         JSON Schema снимка — договор между сервером и клиентами
server/         Go: импортёр (cmd/importer) и сервер каталога (cmd/server)
android/
  shared/       Kotlin Multiplatform: модели, разбор каталога, поиск, форматирование,
                офлайн-кэш. Без android.* — сюда подключится iOS
  app/          Android-приложение (Compose, MapLibre)
docs/
  research/     исследование публичных данных
  data-model.md схема данных
  offline-map.md как встроить базовую карту города
```

Границы модулей: платформенные вещи (файлы, сеть, карта, уведомления) живут в
`app`, правила и данные — в `shared`. Сервер знает только снимок каталога.

## Быстрый старт

Требуется Go 1.24+, JDK 17+, для Android — Android SDK (compileSdk 36).

```bash
# 1. Импорт из сохранённого ответа портала (без сети, воспроизводимо)
cd server
go run ./cmd/importer -input ../data/raw/parkingkrd-zones-2026-10-28.json
# или с портала:
go run ./cmd/importer -url https://parkingkrd.ru/signingproxy/parkings

# 2. Сервер каталога
go run ./cmd/server -addr :8080
curl localhost:8080/api/v1/catalog/meta

# 3. Тесты
go test ./...
cd ../android
./gradlew -PwithAndroid=false :shared:jvmTest   # общая логика, без Android SDK
./gradlew :app:assembleDebug                    # APK, нужен Android SDK
```

В эмуляторе приложение ходит на `http://10.0.2.2:8080`; адрес меняется
свойством `catalogBaseUrl` в `android/gradle.properties`. Без сервера
приложение работает на встроенном каталоге.

## API сервера

| Метод | Что отдаёт |
| --- | --- |
| `GET /healthz` | живость и версия каталога |
| `GET /api/v1/catalog/meta` | версия, дата сборки, источники с `source_url` и `fetched_at`, счётчики |
| `GET /api/v1/parkings` | весь каталог; `ETag` = версия, `If-None-Match` → 304; gzip |
| `GET /api/v1/parkings.geojson` | слой для карты (только парковки с геометрией) |
| `GET /api/v1/parkings/{id}` | парковка по `portal_id` или `parkingkrd:<id>` |
| `GET /api/v1/search?q=&limit=&paid=` | поиск по адресу и номеру |
| `GET /api/v1/nearby?lat=&lon=&radius=` | ближайшие платные — только варианты, зону подтверждает пользователь |

## Принципы достоверности

- Нет значения в источнике — показываем «Нет данных».
- Тариф, часы и места показываются с источником и датой получения.
- Номер парковки — `id` из ответа портала; его совпадение с табличкой не
  подтверждено, и приложение так и пишет.
- Линии участков рисуются только из данных источника, без догадок.
- Никаких очередей денежных списаний; сохранённое намерение не равно оплате.

Лицензии: Onest — SIL OFL 1.1 (`android/app/src/main/assets/licenses`),
MapLibre Native — BSD-2.
