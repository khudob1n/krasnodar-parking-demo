// Package portal читает публичный ответ портала parkingkrd.ru со списком
// парковок и переводит его в модель каталога.
//
// Формат описан по сохранённому снимку (см. docs/research/parkingkrd.md):
//
//	{"data": [{"id": 1102, "address": "...", "latitude": 45.02, ...}], "totalLength": 1716}
//
// Пакет не знает ни про авторизацию, ни про платёжные методы портала и не
// должен их касаться.
package portal

import (
	"bytes"
	"encoding/json"
	"fmt"
	"math"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/khudob1n/krasnodar-parking-demo/server/internal/catalog"
)

// Response — корень ответа.
type Response struct {
	Data        []Record `json:"data"`
	TotalLength *int     `json:"totalLength"`
}

// Record повторяет поля портала с их названиями.
type Record struct {
	ID                   int       `json:"id"`
	Address              string    `json:"address"`
	Latitude             float64   `json:"latitude"`
	Longitude            float64   `json:"longitude"`
	TimeFrom             string    `json:"timeFrom"`
	TimeTo               string    `json:"timeTo"`
	Cost                 *float64  `json:"cost"`
	MaxPlaces            *int      `json:"maxPlaces"`
	DisabledPersonPlaces *int      `json:"disabledPersonPlaces"`
	FreeCount            *int      `json:"freeCount"`
	Description          string    `json:"description"`
	Itudes               []Segment `json:"itudes"`
	ParkingForm          int       `json:"parkingForm"`
	Tarif                string    `json:"tarif"`
	PlaceUpdate          string    `json:"placeUpdate"`
	PhotosCount          int       `json:"photosCount"`
	InBuilding           bool      `json:"inBuilding"`
}

// Segment — участок в терминах портала.
type Segment struct {
	StartLatitude  float64 `json:"startLatitude"`
	StartLongitude float64 `json:"startLongitude"`
	StopLatitude   float64 `json:"stopLatitude"`
	StopLongitude  float64 `json:"stopLongitude"`
}

// RequiredFields — поля, без которых запись не принимаем.
var RequiredFields = []string{"id", "address", "latitude", "longitude", "timeFrom", "timeTo", "cost", "parkingForm", "tarif", "itudes"}

// KnownFields — все поля, которые встречались в снимке. Новые поля портала
// попадут в отчёт как предупреждение: это признак смены формата.
var KnownFields = map[string]bool{
	"id": true, "address": true, "latitude": true, "longitude": true, "timeFrom": true, "timeTo": true,
	"cost": true, "maxPlaces": true, "disabledPersonPlaces": true, "freeCount": true, "description": true,
	"itudes": true, "parkingForm": true, "tarif": true, "placeUpdate": true, "photosCount": true, "inBuilding": true,
}

// SchemaReport — итог проверки структуры до разбора значений.
type SchemaReport struct {
	Records        int            `json:"records"`
	TotalLength    *int           `json:"total_length"`
	MissingFields  map[string]int `json:"missing_fields"`  // поле -> сколько записей без него
	UnknownFields  map[string]int `json:"unknown_fields"`  // новое поле -> в скольких записях
	RecordsInvalid []int          `json:"records_invalid"` // индексы записей без обязательных полей
}

// Decode разбирает ответ и одновременно проверяет его структуру.
func Decode(raw []byte) (Response, SchemaReport, error) {
	var loose struct {
		Data        []map[string]json.RawMessage `json:"data"`
		TotalLength *int                         `json:"totalLength"`
	}
	if err := json.Unmarshal(raw, &loose); err != nil {
		return Response{}, SchemaReport{}, fmt.Errorf("ответ не JSON нужной формы: %w", err)
	}
	if loose.Data == nil {
		return Response{}, SchemaReport{}, fmt.Errorf("в ответе нет массива data")
	}
	rep := SchemaReport{
		Records:       len(loose.Data),
		TotalLength:   loose.TotalLength,
		MissingFields: map[string]int{},
		UnknownFields: map[string]int{},
	}
	for i, rec := range loose.Data {
		bad := false
		for _, f := range RequiredFields {
			if v, ok := rec[f]; !ok || bytes.Equal(v, []byte("null")) {
				rep.MissingFields[f]++
				bad = true
			}
		}
		for f := range rec {
			if !KnownFields[f] {
				rep.UnknownFields[f]++
			}
		}
		if bad {
			rep.RecordsInvalid = append(rep.RecordsInvalid, i)
		}
	}
	var resp Response
	if err := json.Unmarshal(raw, &resp); err != nil {
		return Response{}, rep, fmt.Errorf("типы полей не совпали с ожидаемыми: %w", err)
	}
	return resp, rep, nil
}

// Bounds — прямоугольник, в котором ожидаем координаты Краснодара с
// пригородами. Всё, что снаружи, считаем ошибкой координат.
type Bounds struct{ MinLat, MaxLat, MinLon, MaxLon float64 }

// Krasnodar — с запасом вокруг городского округа.
var Krasnodar = Bounds{MinLat: 44.85, MaxLat: 45.30, MinLon: 38.65, MaxLon: 39.40}

func (b Bounds) contains(lat, lon float64) bool {
	return lat >= b.MinLat && lat <= b.MaxLat && lon >= b.MinLon && lon <= b.MaxLon
}

// Пороги проверки геометрии.
const (
	maxSegmentMeters      = 3000
	maxSegmentOffsetMeter = 2000
)

// Problem — замечание к записи. Fatal исключает запись из каталога.
type Problem struct {
	PortalID int    `json:"portal_id"`
	Index    int    `json:"index"`
	Code     string `json:"code"`
	Detail   string `json:"detail"`
	Fatal    bool   `json:"fatal"`
}

// Convert переводит записи портала в парковки каталога. Записи с фатальными
// проблемами не попадают в результат, но остаются в списке проблем.
func Convert(resp Response, sourceID string) ([]catalog.Parking, []Problem) {
	var out []catalog.Parking
	var problems []Problem
	seen := map[int]int{}
	for i, r := range resp.Data {
		p, probs := convertOne(r, sourceID)
		for j := range probs {
			probs[j].Index = i
		}
		if first, dup := seen[r.ID]; dup {
			probs = append(probs, Problem{PortalID: r.ID, Index: i, Code: "duplicate_id", Fatal: true,
				Detail: fmt.Sprintf("id уже встречался в записи #%d", first)})
		} else {
			seen[r.ID] = i
		}
		problems = append(problems, probs...)
		if hasFatal(probs) {
			continue
		}
		out = append(out, p)
	}
	sort.Slice(out, func(a, b int) bool { return out[a].PortalID < out[b].PortalID })
	return out, problems
}

func hasFatal(ps []Problem) bool {
	for _, p := range ps {
		if p.Fatal {
			return true
		}
	}
	return false
}

func convertOne(r Record, sourceID string) (catalog.Parking, []Problem) {
	var probs []Problem
	add := func(code string, fatal bool, format string, a ...any) {
		probs = append(probs, Problem{PortalID: r.ID, Code: code, Fatal: fatal, Detail: fmt.Sprintf(format, a...)})
	}

	p := catalog.Parking{
		ID:          fmt.Sprintf("%s:%d", sourceID, r.ID),
		PortalID:    r.ID,
		Address:     normalizeSpaces(r.Address),
		Description: normalizeSpaces(r.Description),
		InBuilding:  r.InBuilding,
		PhotosCount: r.PhotosCount,
		Source:      sourceID,
		Segments:    [][2]catalog.Point{},
		Issues:      []string{},
	}
	if p.Description == p.Address {
		p.Description = ""
	}
	if r.ID <= 0 {
		add("bad_id", true, "id=%d", r.ID)
	}
	if p.Address == "" {
		add("empty_address", true, "пустой адрес")
	}

	switch r.ParkingForm {
	case 0:
		p.Kind, p.Paid = catalog.KindLot, true
	case 1:
		p.Kind, p.Paid = catalog.KindStreet, true
	case 2:
		p.Kind, p.Paid = catalog.KindFree, false
	default:
		add("unknown_parking_form", true, "parkingForm=%d", r.ParkingForm)
	}

	// Тариф.
	if r.Cost != nil {
		if *r.Cost < 0 || math.IsNaN(*r.Cost) {
			add("bad_cost", true, "cost=%v", *r.Cost)
		} else {
			c := *r.Cost
			p.Tariff.PricePerHourRub = &c
		}
	}
	p.Tariff.Text = strings.TrimSpace(r.Tarif)
	if from, ok := clock(r.TimeFrom); ok {
		p.Tariff.PaidFrom = &from
	} else {
		add("bad_time_from", false, "timeFrom=%q", r.TimeFrom)
	}
	if to, ok := clock(r.TimeTo); ok {
		p.Tariff.PaidTo = &to
	} else {
		add("bad_time_to", false, "timeTo=%q", r.TimeTo)
	}
	if p.Kind == catalog.KindFree && p.Tariff.PricePerHourRub != nil && *p.Tariff.PricePerHourRub > 0 {
		add("free_with_price", false, "parkingForm=2, но cost=%v", *p.Tariff.PricePerHourRub)
	}
	if p.Paid && p.Tariff.PricePerHourRub != nil && *p.Tariff.PricePerHourRub == 0 {
		add("paid_with_zero_price", false, "платная парковка с ценой 0")
	}
	if msg := tariffTextMismatch(p.Tariff); msg != "" {
		add("tariff_text_mismatch", false, "%s", msg)
	}

	// Вместимость: отрицательные значения считаем отсутствием данных.
	if r.MaxPlaces != nil && *r.MaxPlaces >= 0 {
		v := *r.MaxPlaces
		p.Capacity.Total = &v
	}
	if r.DisabledPersonPlaces != nil && *r.DisabledPersonPlaces >= 0 {
		v := *r.DisabledPersonPlaces
		p.Capacity.Disabled = &v
	}
	if p.Capacity.Total != nil && p.Capacity.Disabled != nil && *p.Capacity.Disabled > *p.Capacity.Total {
		add("disabled_gt_total", false, "мест для инвалидов %d больше общего числа %d", *p.Capacity.Disabled, *p.Capacity.Total)
	}

	p.FreePlaces = freePlaces(r)

	// Координаты и геометрия.
	if Krasnodar.contains(r.Latitude, r.Longitude) {
		p.Location = &catalog.Point{Lat: r.Latitude, Lon: r.Longitude}
	} else {
		add("location_out_of_bounds", false, "точка %.6f, %.6f вне Краснодара", r.Latitude, r.Longitude)
	}
	for k, s := range r.Itudes {
		a := catalog.Point{Lat: s.StartLatitude, Lon: s.StartLongitude}
		b := catalog.Point{Lat: s.StopLatitude, Lon: s.StopLongitude}
		switch {
		case a == b:
			// Площадки портал отдаёт «участком» из одной точки. Это не линия.
			continue
		case !Krasnodar.contains(a.Lat, a.Lon) || !Krasnodar.contains(b.Lat, b.Lon):
			add("segment_out_of_bounds", false, "участок #%d вне Краснодара", k)
			continue
		}
		if d := Meters(a, b); d > maxSegmentMeters {
			add("segment_too_long", false, "участок #%d длиной %.0f м отброшен", k, d)
			continue
		}
		if p.Location != nil {
			if d := Meters(*p.Location, a); d > maxSegmentOffsetMeter {
				add("segment_far_from_location", false, "участок #%d в %.0f м от точки парковки", k, d)
			}
		}
		p.Segments = append(p.Segments, [2]catalog.Point{a, b})
	}
	switch {
	case len(p.Segments) > 0:
		p.GeometryStatus = catalog.GeometrySegments
	case p.Location != nil:
		p.GeometryStatus = catalog.GeometryPointOnly
	default:
		p.GeometryStatus = catalog.GeometryNone
	}

	for _, pr := range probs {
		if !pr.Fatal {
			p.Issues = append(p.Issues, pr.Code)
		}
	}
	return p, probs
}

// freePlaces: портал отдаёт freeCount, но в снимке он почти всегда равен
// maxPlaces, а placeUpdate — заглушка 1900-01-01. Такие значения не являются
// данными о свободных местах.
func freePlaces(r Record) catalog.FreePlaces {
	noData := catalog.FreePlaces{Status: "no_data"}
	if r.FreeCount == nil {
		noData.Note = "источник не отдал freeCount"
		return noData
	}
	if r.PlaceUpdate == "" {
		noData.Note = "нет времени обновления placeUpdate"
		return noData
	}
	t, err := time.Parse("2006-01-02T15:04:05.000-07:00", r.PlaceUpdate)
	if err != nil {
		noData.Note = "placeUpdate в неизвестном формате"
		return noData
	}
	if t.Year() < 2020 {
		noData.Note = "placeUpdate — заглушка " + t.Format("2006-01-02")
		return noData
	}
	v := *r.FreeCount
	t = t.UTC()
	return catalog.FreePlaces{Value: &v, UpdatedAt: &t, Status: "available"}
}

var clockRe = regexp.MustCompile(`^([01]\d|2[0-3]):[0-5]\d$`)

func clock(s string) (string, bool) {
	s = strings.TrimSpace(s)
	return s, clockRe.MatchString(s)
}

// Первая строка тарифа: "60.0 руб/час c 08:00 до 19:00" или
// "100.0 руб/час круглосуточно". Буква «c» бывает и латинской, и русской.
var tariffLineRe = regexp.MustCompile(`^(\d+(?:[.,]\d+)?)\s*руб/час\s*(?:[cс]\s*(\d{2}:\d{2})\s*до\s*(\d{2}:\d{2})|(круглосуточно))`)

func tariffTextMismatch(t catalog.Tariff) string {
	if t.Text == "" {
		return ""
	}
	first := strings.SplitN(t.Text, "\n", 2)[0]
	m := tariffLineRe.FindStringSubmatch(strings.TrimSpace(first))
	if m == nil {
		return "текст тарифа в неизвестном формате"
	}
	price, _ := strconv.ParseFloat(strings.ReplaceAll(m[1], ",", "."), 64)
	if t.PricePerHourRub != nil && price != *t.PricePerHourRub {
		return fmt.Sprintf("в тексте %v ₽/ч, в поле cost %v", price, *t.PricePerHourRub)
	}
	if m[4] == "" && t.PaidFrom != nil && t.PaidTo != nil && (m[2] != *t.PaidFrom || m[3] != *t.PaidTo) {
		return fmt.Sprintf("в тексте %s–%s, в полях %s–%s", m[2], m[3], *t.PaidFrom, *t.PaidTo)
	}
	return ""
}

var spaces = regexp.MustCompile(`\s+`)

func normalizeSpaces(s string) string { return strings.TrimSpace(spaces.ReplaceAllString(s, " ")) }

// Meters — расстояние по сфере, метры.
func Meters(a, b catalog.Point) float64 {
	const r = 6371000.0
	la1, la2 := a.Lat*math.Pi/180, b.Lat*math.Pi/180
	dla := la2 - la1
	dlo := (b.Lon - a.Lon) * math.Pi / 180
	h := math.Sin(dla/2)*math.Sin(dla/2) + math.Cos(la1)*math.Cos(la2)*math.Sin(dlo/2)*math.Sin(dlo/2)
	return 2 * r * math.Asin(math.Sqrt(h))
}
