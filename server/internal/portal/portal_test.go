package portal

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/khudob1n/krasnodar-parking-demo/server/internal/catalog"
)

const sample = `{"data":[
 {"id":1102,"address":"Чётная сторона ул. им. Гоголя,  от ул. Красноармейской до ул. Красной","latitude":45.026653,"longitude":38.972143,
  "timeFrom":"08:00","timeTo":"19:00","cost":60.0,"maxPlaces":37,"disabledPersonPlaces":4,"freeCount":37,
  "description":"Чётная сторона ул. им. Гоголя, от ул. Красноармейской до ул. Красной",
  "itudes":[{"startLatitude":45.026684,"startLongitude":38.972087,"stopLatitude":45.026543,"stopLongitude":38.972943}],
  "parkingForm":1,"tarif":"60.0 руб/час c 08:00 до 19:00\n0 руб/час c 19:00 до 08:00","placeUpdate":"1900-01-01T00:29:43.000+03:00","photosCount":4,"inBuilding":false},
 {"id":1158,"address":"Территория Привокзальной Площади","latitude":45.019072,"longitude":38.988583,"timeFrom":"00:00","timeTo":"00:00","cost":100.0,
  "maxPlaces":123,"disabledPersonPlaces":13,"description":"","itudes":[{"startLatitude":45.019072,"startLongitude":38.988583,"stopLatitude":45.019072,"stopLongitude":38.988583}],
  "parkingForm":0,"tarif":"100.0 руб/час круглосуточно","photosCount":0,"inBuilding":false},
 {"id":3333,"address":"3-я Линия Нефтяников 2","latitude":0,"longitude":0,"timeFrom":"08:00","timeTo":"20:00","cost":10.0,"description":"",
  "itudes":[],"parkingForm":1,"tarif":"10.0 руб/час c 08:00 до 20:00","photosCount":0,"inBuilding":false,"newField":1},
 {"id":12677,"address":"г.Краснодар, ул. Тестовая","latitude":42.848373,"longitude":74.270911,"timeFrom":"00:00","timeTo":"00:00","cost":0.0,"description":"",
  "itudes":[],"parkingForm":2,"tarif":"0.0 руб/час круглосуточно","photosCount":0,"inBuilding":false},
 {"id":1102,"address":"дубль","latitude":45.02,"longitude":38.97,"timeFrom":"08:00","timeTo":"19:00","cost":60.0,"description":"",
  "itudes":[],"parkingForm":1,"tarif":"60.0 руб/час c 08:00 до 20:00","photosCount":0,"inBuilding":false},
 {"id":5,"address":"x","latitude":45.02,"longitude":38.97,"timeFrom":"08:00","timeTo":"19:00","cost":1,"description":"",
  "itudes":[],"parkingForm":7,"tarif":"","photosCount":0,"inBuilding":false}
],"totalLength":6}`

func byID(ps []catalog.Parking) map[int]catalog.Parking {
	m := map[int]catalog.Parking{}
	for _, p := range ps {
		m[p.PortalID] = p
	}
	return m
}

func TestDecodeSchema(t *testing.T) {
	_, rep, err := Decode([]byte(sample))
	if err != nil {
		t.Fatal(err)
	}
	if rep.Records != 6 || rep.TotalLength == nil || *rep.TotalLength != 6 {
		t.Fatalf("records=%d total=%v", rep.Records, rep.TotalLength)
	}
	if rep.UnknownFields["newField"] != 1 {
		t.Fatalf("новое поле не замечено: %v", rep.UnknownFields)
	}
	if len(rep.RecordsInvalid) != 0 {
		t.Fatalf("лишние невалидные: %v", rep.RecordsInvalid)
	}
}

func TestDecodeMissingRequired(t *testing.T) {
	_, rep, err := Decode([]byte(`{"data":[{"id":1,"address":"a"}]}`))
	if err != nil {
		t.Fatal(err)
	}
	if len(rep.RecordsInvalid) != 1 || rep.MissingFields["latitude"] != 1 {
		t.Fatalf("%+v", rep)
	}
}

func TestDecodeRejectsNonCatalog(t *testing.T) {
	if _, _, err := Decode([]byte(`<html></html>`)); err == nil {
		t.Fatal("HTML должен давать ошибку")
	}
	if _, _, err := Decode([]byte(`{"items":[]}`)); err == nil {
		t.Fatal("без data должна быть ошибка")
	}
}

func TestConvert(t *testing.T) {
	resp, _, err := Decode([]byte(sample))
	if err != nil {
		t.Fatal(err)
	}
	ps, probs := Convert(resp, "parkingkrd")
	m := byID(ps)

	street := m[1102]
	if street.ID != "parkingkrd:1102" || street.Kind != catalog.KindStreet || !street.Paid {
		t.Fatalf("уличная: %+v", street)
	}
	if street.Address != "Чётная сторона ул. им. Гоголя, от ул. Красноармейской до ул. Красной" {
		t.Fatalf("пробелы не нормализованы: %q", street.Address)
	}
	if street.Description != "" {
		t.Fatal("описание, равное адресу, должно убираться")
	}
	if street.GeometryStatus != catalog.GeometrySegments || len(street.Segments) != 1 {
		t.Fatalf("геометрия: %v %v", street.GeometryStatus, street.Segments)
	}
	if street.FreePlaces.Status != "no_data" || street.FreePlaces.Value != nil {
		t.Fatalf("заглушка 1900 года не должна давать свободные места: %+v", street.FreePlaces)
	}
	if street.TariffZone != nil || street.Status != nil {
		t.Fatal("тарифной зоны и статуса у портала нет — должны быть null")
	}

	lot := m[1158]
	if lot.Kind != catalog.KindLot || lot.GeometryStatus != catalog.GeometryPointOnly || len(lot.Segments) != 0 {
		t.Fatalf("площадка с «участком» из одной точки: %+v", lot)
	}
	if lot.FreePlaces.Status != "no_data" {
		t.Fatal("без freeCount — нет данных")
	}

	bad := m[3333]
	if bad.Location != nil || bad.GeometryStatus != catalog.GeometryNone {
		t.Fatalf("координаты 0,0 нельзя показывать: %+v", bad)
	}
	if !contains(bad.Issues, "location_out_of_bounds") {
		t.Fatalf("issues: %v", bad.Issues)
	}

	far := m[12677]
	if far.Paid || far.Kind != catalog.KindFree || far.Location != nil {
		t.Fatalf("точка в Бишкеке должна отбрасываться: %+v", far)
	}

	if _, ok := m[5]; ok {
		t.Fatal("неизвестный parkingForm должен исключаться")
	}
	if len(ps) != 4 {
		t.Fatalf("ожидали 4 парковки, получили %d", len(ps))
	}
	if !hasProblem(probs, 1102, "duplicate_id", true) {
		t.Fatal("дубликат id не найден")
	}
	if !hasProblem(probs, 5, "unknown_parking_form", true) {
		t.Fatal("неизвестный тип не отмечен")
	}
	// Первая запись с id 1102 остаётся, дубль не заменяет её.
	if street.Address == "дубль" {
		t.Fatal("дубль перезаписал запись")
	}
}

func TestTariffTextMismatch(t *testing.T) {
	p := func(v float64) *float64 { return &v }
	s := func(v string) *string { return &v }
	cases := []struct {
		t    catalog.Tariff
		want bool
	}{
		{catalog.Tariff{PricePerHourRub: p(60), PaidFrom: s("08:00"), PaidTo: s("19:00"), Text: "60.0 руб/час c 08:00 до 19:00\n0 руб/час c 19:00 до 08:00"}, false},
		{catalog.Tariff{PricePerHourRub: p(60), PaidFrom: s("08:00"), PaidTo: s("19:00"), Text: "60.0 руб/час с 08:00 до 20:00"}, true},
		{catalog.Tariff{PricePerHourRub: p(50), PaidFrom: s("08:00"), PaidTo: s("19:00"), Text: "60.0 руб/час c 08:00 до 19:00"}, true},
		{catalog.Tariff{PricePerHourRub: p(100), PaidFrom: s("00:00"), PaidTo: s("00:00"), Text: "100.0 руб/час круглосуточно"}, false},
		{catalog.Tariff{Text: "что-то новое"}, true},
	}
	for i, c := range cases {
		if got := tariffTextMismatch(c.t) != ""; got != c.want {
			t.Errorf("#%d: got %v want %v", i, got, c.want)
		}
	}
}

func TestMeters(t *testing.T) {
	d := Meters(catalog.Point{Lat: 45.026684, Lon: 38.972087}, catalog.Point{Lat: 45.026543, Lon: 38.972943})
	if d < 60 || d > 75 {
		t.Fatalf("участок Гоголя около 69 м, получили %.1f", d)
	}
}

func TestFetchRefusesClosedAccess(t *testing.T) {
	for _, code := range []int{http.StatusUnauthorized, http.StatusForbidden, http.StatusTooManyRequests, http.StatusFound} {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if code == http.StatusFound {
				http.Redirect(w, r, "/login", code)
				return
			}
			w.WriteHeader(code)
		}))
		_, err := Fetch(context.Background(), nil, srv.URL)
		srv.Close()
		if err == nil {
			t.Fatalf("%d: ожидали отказ", code)
		}
	}
}

func TestFetchOK(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Cookie") != "" || r.Header.Get("Authorization") != "" {
			t.Error("импортёр не должен слать cookies и авторизацию")
		}
		if !strings.HasPrefix(r.Header.Get("User-Agent"), "krasnodar-parking-demo-importer") {
			t.Errorf("UA: %q", r.Header.Get("User-Agent"))
		}
		w.Write([]byte(sample))
	}))
	defer srv.Close()
	f, err := Fetch(context.Background(), nil, srv.URL)
	if err != nil {
		t.Fatal(err)
	}
	if f.FetchedAt.IsZero() || len(f.Body) != len(sample) {
		t.Fatal("нет времени получения или тела")
	}
}

func contains(xs []string, s string) bool {
	for _, x := range xs {
		if x == s {
			return true
		}
	}
	return false
}

func hasProblem(ps []Problem, id int, code string, fatal bool) bool {
	for _, p := range ps {
		if p.PortalID == id && p.Code == code && p.Fatal == fatal {
			return true
		}
	}
	return false
}
