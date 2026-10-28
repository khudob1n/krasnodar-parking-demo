package main

import (
	"encoding/json"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"testing"

	"github.com/khudob1n/krasnodar-parking-demo/server/internal/catalog"
	"github.com/khudob1n/krasnodar-parking-demo/server/internal/snapshot"
)

// Сквозная проверка на сохранённом ответе портала: импорт воспроизводим и
// даёт ожидаемые числа.
func TestImportStoredSnapshot(t *testing.T) {
	input := "../../../data/raw/parkingkrd-zones-2026-10-28.json"
	if _, err := os.Stat(input); err != nil {
		t.Skip("нет сохранённого ответа:", err)
	}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	outA, outB := t.TempDir(), t.TempDir()
	cards := "../../../data/reference/control-cards.json"
	for _, out := range []string{outA, outB} {
		if err := run(log, input, "", "", cards, out, "parkingkrd", "2026-10-28T00:00:00Z"); err != nil {
			t.Fatal(err)
		}
	}
	a, err := os.ReadFile(filepath.Join(outA, snapshot.ParkingsFile))
	if err != nil {
		t.Fatal(err)
	}
	b, _ := os.ReadFile(filepath.Join(outB, snapshot.ParkingsFile))
	if string(a) != string(b) {
		t.Fatal("два импорта одного файла дали разный снимок")
	}

	s, err := snapshot.Load(filepath.Join(outA, snapshot.ParkingsFile))
	if err != nil {
		t.Fatal(err)
	}
	if s.Counts.Total != 1716 || s.Counts.Paid != 323 || s.Counts.Excluded != 0 {
		t.Fatalf("counts: %+v", s.Counts)
	}
	src := s.Sources[0]
	if src.SourceURL == "" || src.FetchedAt.IsZero() || src.RawSHA256 == "" {
		t.Fatalf("нет source_url/fetched_at: %+v", src)
	}
	for _, p := range s.Parkings {
		if p.Location == nil {
			continue
		}
		if p.Location.Lat < 44.85 || p.Location.Lat > 45.3 || p.Location.Lon < 38.65 || p.Location.Lon > 39.4 {
			t.Fatalf("%s: точка вне Краснодара прошла в снимок", p.ID)
		}
		if p.FreePlaces.Value != nil {
			t.Fatalf("%s: в снимке нет достоверных свободных мест, а значение есть", p.ID)
		}
	}

	var fc catalog.FeatureCollection
	gb, _ := os.ReadFile(filepath.Join(outA, snapshot.GeoJSONFile))
	if err := json.Unmarshal(gb, &fc); err != nil {
		t.Fatal(err)
	}
	if fc.Type != "FeatureCollection" || len(fc.Features) != s.Counts.WithGeometry {
		t.Fatalf("GeoJSON: %s, %d объектов при %d с геометрией", fc.Type, len(fc.Features), s.Counts.WithGeometry)
	}
	for _, ft := range fc.Features {
		if ft.Properties["source_url"] == nil || ft.Properties["fetched_at"] == nil {
			t.Fatalf("%s: нет source_url/fetched_at в свойствах", ft.ID)
		}
	}
}

func TestImportRejectsTamperedRaw(t *testing.T) {
	dir := t.TempDir()
	raw := filepath.Join(dir, "x.json")
	os.WriteFile(raw, []byte(`{"data":[],"totalLength":0}`), 0o644)
	os.WriteFile(filepath.Join(dir, "x.meta.json"), []byte(`{"sha256":"00","source_url":"https://parkingkrd.ru/","fetched_at":"2026-10-28T00:00:00Z"}`), 0o644)
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	if err := run(log, raw, "", "", "", t.TempDir(), "p", ""); err == nil {
		t.Fatal("несовпадение sha256 должно останавливать импорт")
	}
}
