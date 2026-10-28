# Схема данных

Формальная схема — [`schema/parkings.schema.json`](../schema/parkings.schema.json)
(JSON Schema 2020-12). Снимок проверяется по ней в `go test ./internal/snapshot`.

## Снимок каталога (`data/snapshot/parkings.json`)

```
Snapshot
  schema_version  1
  version         16 hex: sha256 от parkings (не зависит от времени сборки)
  generated_at    время сборки
  sources[]       Source
  counts          total, paid, free, with_geometry, paid_places, excluded
  parkings[]      Parking
```

### Source

| Поле | Значение |
| --- | --- |
| `id` | `parkingkrd` |
| `source_url` | страница источника, `https://parkingkrd.ru/` |
| `endpoint_url` | точный адрес запроса или `null` |
| `fetched_at` | время получения |
| `fetched_at_precision` | `exact` — импортёр получил сам; `not_later_than` — известна верхняя граница; `user_reported` — со слов человека |
| `raw_sha256` | хеш сырого ответа в `data/raw` |
| `verified_against_portal` | сверен ли снимок с живым порталом |

### Parking

| Поле | Тип | Источник |
| --- | --- | --- |
| `id` | `parkingkrd:1102` | наш стабильный ключ |
| `portal_id` | int | `id` портала; связь с табличкой не подтверждена |
| `kind` | `street` / `lot` / `free` | `parkingForm` 1 / 0 / 2 (вывод по данным) |
| `paid` | bool | `kind != free` |
| `address`, `description` | строки | как в источнике, пробелы нормализованы |
| `location` | `{lat, lon}` или `null` | `null`, если точка вне Краснодара |
| `segments` | `[[{lat,lon},{lat,lon}], …]` | `itudes` без вырожденных и битых участков |
| `geometry_status` | `segments` / `point_only` / `none` | что можно нарисовать |
| `tariff` | `price_per_hour_rub`, `paid_from`, `paid_to`, `text` | `cost`, `timeFrom`, `timeTo`, `tarif` |
| `capacity` | `total`, `disabled` | `maxPlaces`, `disabledPersonPlaces` |
| `free_places` | `value`, `updated_at`, `status` | `available` только при правдоподобном `placeUpdate`; сейчас везде `no_data` |
| `tariff_zone` | `null` | в источнике нет |
| `status` | `null` | в источнике нет |
| `issues` | коды замечаний | `location_out_of_bounds`, `tariff_text_mismatch`, `paid_with_zero_price`, … |

## Отчёт проверки (`validation-report.json`)

Блокирующие проверки (импорт не заменяет снимок при провале):
`total_length_matches` (признак пагинации), `fatal_share` (≤ 1 % исключённых),
`unique_ids`, `paid_geometry_share` (≥ 95 %), `control_cards`.
Предупреждения: `schema_required_fields`, `schema_unknown_fields`,
`duplicate_addresses`.

## Автомобиль (`shared`, этап II)

```
Vehicle  id, plate (нормализованный «А123ВС93»), title, nickname
```
Номер хранится только на устройстве; в логи — `Plate.masked`.
