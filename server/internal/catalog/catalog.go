// Package catalog описывает нашу модель каталога парковок: то, что импортёр
// пишет в снимок, сервер отдаёт клиентам, а приложение хранит офлайн.
//
// Модель намеренно не повторяет поля портала один в один. Каждое значение,
// которое мы не можем подтвердить, хранится как отсутствующее (null) со
// статусом, а не как выдуманное число.
package catalog

import "time"

// SchemaVersion меняется при несовместимых изменениях формата снимка.
const SchemaVersion = 1

// Kind — тип парковки.
type Kind string

const (
	// KindStreet — участок улицы (на портале parkingForm=1).
	KindStreet Kind = "street"
	// KindLot — площадка (parkingForm=0).
	KindLot Kind = "lot"
	// KindFree — бесплатная парковка (parkingForm=2). Вывод сделан по данным:
	// у всех таких записей стоимость 0; подтверждения от портала нет.
	KindFree Kind = "free"
)

// Snapshot — весь каталог одной версии.
type Snapshot struct {
	SchemaVersion int `json:"schema_version"`
	// Version — sha256 от содержимого parkings; не зависит от generated_at,
	// поэтому повторный импорт тех же данных даёт ту же версию.
	Version     string    `json:"version"`
	GeneratedAt time.Time `json:"generated_at"`
	Sources     []Source  `json:"sources"`
	Counts      Counts    `json:"counts"`
	Parkings    []Parking `json:"parkings"`
}

// Counts — сводка для экрана «О данных» и для проверки импорта.
type Counts struct {
	Total        int `json:"total"`
	Paid         int `json:"paid"`
	Free         int `json:"free"`
	WithGeometry int `json:"with_geometry"`
	PaidPlaces   int `json:"paid_places"`
	Excluded     int `json:"excluded"`
}

// Source — откуда и когда взяты данные.
type Source struct {
	ID                    string    `json:"id"`
	Publisher             string    `json:"publisher"`
	SourceURL             string    `json:"source_url"`
	EndpointURL           *string   `json:"endpoint_url"`
	FetchedAt             time.Time `json:"fetched_at"`
	FetchedAtPrecision    string    `json:"fetched_at_precision"` // exact | not_later_than
	RawSHA256             string    `json:"raw_sha256"`
	Provenance            string    `json:"provenance,omitempty"`
	VerifiedAgainstPortal bool      `json:"verified_against_portal"`
	Note                  string    `json:"note,omitempty"`
}

// Parking — одна парковка каталога.
type Parking struct {
	// ID — наш стабильный идентификатор: "<источник>:<id в источнике>".
	ID string `json:"id"`
	// PortalID — поле id из ответа портала. Совпадает ли оно с номером
	// парковки на табличке и в SMS, не подтверждено.
	PortalID int  `json:"portal_id"`
	Kind     Kind `json:"kind"`
	Paid     bool `json:"paid"`

	Address     string `json:"address"`
	Description string `json:"description,omitempty"`

	// Location — опорная точка из источника. null, если координаты негодные.
	Location *Point `json:"location"`
	// Segments — линии участков вдоль дороги ровно так, как их отдал
	// источник. Пустой список значит «геометрии нет»: мы её не дорисовываем.
	Segments       [][2]Point     `json:"segments"`
	GeometryStatus GeometryStatus `json:"geometry_status"`

	Tariff     Tariff     `json:"tariff"`
	Capacity   Capacity   `json:"capacity"`
	FreePlaces FreePlaces `json:"free_places"`

	// TariffZone — территориальная/тарифная зона. В ответе портала такого
	// поля нет, поэтому всегда null, пока не найден источник.
	TariffZone *string `json:"tariff_zone"`
	// Status — состояние работы парковки (действует/закрыта). Портал его
	// не отдаёт.
	Status *string `json:"status"`

	InBuilding  bool `json:"in_building"`
	PhotosCount int  `json:"photos_count"`

	// Issues — замечания проверки, которые не мешают показу.
	Issues []string `json:"issues"`
	Source string   `json:"source"` // ссылка на Source.ID
}

// Point — координата WGS84.
type Point struct {
	Lat float64 `json:"lat"`
	Lon float64 `json:"lon"`
}

// GeometryStatus объясняет, что есть в Segments.
type GeometryStatus string

const (
	GeometrySegments  GeometryStatus = "segments"   // есть линии
	GeometryPointOnly GeometryStatus = "point_only" // только точка
	GeometryNone      GeometryStatus = "none"       // ни линий, ни годной точки
)

// Tariff — тариф как его публикует источник.
type Tariff struct {
	// PricePerHourRub — null, если цены нет в источнике.
	PricePerHourRub *float64 `json:"price_per_hour_rub"`
	// PaidFrom/PaidTo — часы платного периода "08:00"/"19:00".
	// "00:00"–"00:00" источник пишет для круглосуточных.
	PaidFrom *string `json:"paid_from"`
	PaidTo   *string `json:"paid_to"`
	Text     string  `json:"text"`
}

// Capacity — вместимость по данным источника.
type Capacity struct {
	Total    *int `json:"total"`
	Disabled *int `json:"disabled"`
}

// FreePlaces — свободные места. Value заполняется только при достоверной
// отметке времени обновления.
type FreePlaces struct {
	Value     *int       `json:"value"`
	UpdatedAt *time.Time `json:"updated_at"`
	Status    string     `json:"status"` // available | no_data
	Note      string     `json:"note,omitempty"`
}
