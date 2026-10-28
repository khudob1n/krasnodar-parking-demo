package api

import (
	"compress/gzip"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/khudob1n/krasnodar-parking-demo/server/internal/catalog"
	"github.com/khudob1n/krasnodar-parking-demo/server/internal/snapshot"
)

func fixture(t *testing.T) *Server {
	t.Helper()
	price := 60.0
	ps := []catalog.Parking{
		{ID: "parkingkrd:1102", PortalID: 1102, Kind: catalog.KindStreet, Paid: true,
			Address:  "Чётная сторона ул. им. Гоголя, от ул. Красноармейской до ул. Красной",
			Location: &catalog.Point{Lat: 45.026653, Lon: 38.972143}, GeometryStatus: catalog.GeometrySegments,
			Segments: [][2]catalog.Point{{{Lat: 45.026684, Lon: 38.972087}, {Lat: 45.026543, Lon: 38.972943}}},
			Tariff:   catalog.Tariff{PricePerHourRub: &price}},
		{ID: "parkingkrd:1103", PortalID: 1103, Kind: catalog.KindStreet, Paid: true,
			Address:  "Чётная сторона ул.Комсомольская, от ул.Красной до ул.Красноармейской",
			Location: &catalog.Point{Lat: 45.019142, Lon: 38.969612}, GeometryStatus: catalog.GeometryPointOnly},
		{ID: "parkingkrd:12001", PortalID: 12001, Kind: catalog.KindFree,
			Address:  "г.Краснодар, По обеим сторонам ул. Московской",
			Location: &catalog.Point{Lat: 45.05096, Lon: 39.003017}, GeometryStatus: catalog.GeometryPointOnly},
	}
	snap, err := snapshot.Build(ps, []catalog.Source{{ID: "parkingkrd", SourceURL: "https://parkingkrd.ru/", FetchedAt: time.Date(2026, 10, 28, 18, 56, 0, 0, time.UTC)}}, 0, time.Now())
	if err != nil {
		t.Fatal(err)
	}
	srv, err := New(snap, slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatal(err)
	}
	return srv
}

func get(t *testing.T, h http.Handler, url string, hdr map[string]string) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(http.MethodGet, url, nil)
	for k, v := range hdr {
		req.Header.Set(k, v)
	}
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	return rec
}

func TestParkingsETagAndGzip(t *testing.T) {
	s := fixture(t)
	h := s.Handler()
	rec := get(t, h, "/api/v1/parkings", map[string]string{"Accept-Encoding": "gzip"})
	if rec.Code != 200 || rec.Header().Get("Content-Encoding") != "gzip" {
		t.Fatalf("code=%d enc=%q", rec.Code, rec.Header().Get("Content-Encoding"))
	}
	zr, err := gzip.NewReader(rec.Body)
	if err != nil {
		t.Fatal(err)
	}
	var snap catalog.Snapshot
	if err := json.NewDecoder(zr).Decode(&snap); err != nil {
		t.Fatal(err)
	}
	if len(snap.Parkings) != 3 || snap.Sources[0].SourceURL == "" {
		t.Fatalf("%+v", snap.Counts)
	}
	etag := rec.Header().Get("ETag")
	if etag == "" {
		t.Fatal("нет ETag")
	}
	if rec := get(t, h, "/api/v1/parkings", map[string]string{"If-None-Match": etag}); rec.Code != http.StatusNotModified || rec.Body.Len() != 0 {
		t.Fatalf("ожидали 304, получили %d", rec.Code)
	}
}

func TestParkingByID(t *testing.T) {
	h := fixture(t).Handler()
	for _, id := range []string{"1102", "parkingkrd:1102"} {
		if rec := get(t, h, "/api/v1/parkings/"+id, nil); rec.Code != 200 {
			t.Fatalf("%s: %d", id, rec.Code)
		}
	}
	if rec := get(t, h, "/api/v1/parkings/9", nil); rec.Code != 404 {
		t.Fatalf("ожидали 404: %d", rec.Code)
	}
}

func TestGeoJSON(t *testing.T) {
	rec := get(t, fixture(t).Handler(), "/api/v1/parkings.geojson", nil)
	var fc catalog.FeatureCollection
	if err := json.Unmarshal(rec.Body.Bytes(), &fc); err != nil {
		t.Fatal(err)
	}
	if len(fc.Features) != 3 || fc.Features[0].Geometry.Type != "MultiLineString" || fc.Features[1].Geometry.Type != "Point" {
		t.Fatalf("%+v", fc.Features)
	}
}

func TestSearch(t *testing.T) {
	s := fixture(t)
	cases := []struct {
		q    string
		want []int
	}{
		{"гоголя", []int{1102}},
		{"Красной", []int{1102, 1103}},
		{"1103", []int{1103}},
		{"красной комсомольская", []int{1103}},
		{"московской", nil}, // бесплатные по умолчанию не ищем
		{"   ", nil},
	}
	for _, c := range cases {
		got := Search(s.snap.Parkings, c.q, true, 10)
		if len(got) != len(c.want) {
			t.Fatalf("%q: %d результатов, ожидали %v", c.q, len(got), c.want)
		}
		for i := range got {
			if got[i].PortalID != c.want[i] {
				t.Fatalf("%q: #%d = %d, ожидали %d", c.q, i, got[i].PortalID, c.want[i])
			}
		}
	}
	if got := Search(s.snap.Parkings, "московской", false, 10); len(got) != 1 {
		t.Fatal("с paid=false бесплатные находятся")
	}
}

func TestNearby(t *testing.T) {
	h := fixture(t).Handler()
	rec := get(t, h, "/api/v1/nearby?lat=45.0266&lon=38.9725&radius=300", nil)
	var body struct {
		Items []Hit `json:"items"`
	}
	json.Unmarshal(rec.Body.Bytes(), &body)
	if len(body.Items) != 1 || body.Items[0].Parking.PortalID != 1102 || *body.Items[0].Distance > 50 {
		t.Fatalf("%+v", body.Items)
	}
	if rec := get(t, h, "/api/v1/nearby?lat=x", nil); rec.Code != 400 {
		t.Fatalf("без координат 400, получили %d", rec.Code)
	}
}

func TestMeta(t *testing.T) {
	rec := get(t, fixture(t).Handler(), "/api/v1/catalog/meta", nil)
	var m Meta
	json.Unmarshal(rec.Body.Bytes(), &m)
	if m.Version == "" || m.Disclaimer == "" || len(m.Sources) != 1 {
		t.Fatalf("%+v", m)
	}
}
