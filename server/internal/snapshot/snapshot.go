// Package snapshot собирает каталог и читает/пишет его файлы.
package snapshot

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/khudob1n/krasnodar-parking-demo/server/internal/catalog"
)

// Имена файлов снимка.
const (
	ParkingsFile = "parkings.json"
	GeoJSONFile  = "parkings.geojson"
	ReportFile   = "validation-report.json"
)

// Build считает сводку и версию.
func Build(parkings []catalog.Parking, sources []catalog.Source, excluded int, generatedAt time.Time) (catalog.Snapshot, error) {
	if parkings == nil {
		parkings = []catalog.Parking{}
	}
	s := catalog.Snapshot{
		SchemaVersion: catalog.SchemaVersion,
		GeneratedAt:   generatedAt.UTC().Truncate(time.Second),
		Sources:       sources,
		Parkings:      parkings,
	}
	for _, p := range parkings {
		s.Counts.Total++
		if p.Paid {
			s.Counts.Paid++
			if p.Capacity.Total != nil {
				s.Counts.PaidPlaces += *p.Capacity.Total
			}
		} else {
			s.Counts.Free++
		}
		if p.GeometryStatus != catalog.GeometryNone {
			s.Counts.WithGeometry++
		}
	}
	s.Counts.Excluded = excluded
	b, err := json.Marshal(parkings)
	if err != nil {
		return catalog.Snapshot{}, err
	}
	sum := sha256.Sum256(b)
	s.Version = hex.EncodeToString(sum[:8])
	return s, nil
}

// Write пишет снимок, GeoJSON и отчёт в каталог dir.
func Write(dir string, s catalog.Snapshot, report any) error {
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return err
	}
	files := map[string]any{
		ParkingsFile: s,
		GeoJSONFile:  s.ToGeoJSON(),
		ReportFile:   report,
	}
	for name, v := range files {
		if err := writeJSON(filepath.Join(dir, name), v); err != nil {
			return fmt.Errorf("%s: %w", name, err)
		}
	}
	return nil
}

// Load читает parkings.json.
func Load(path string) (catalog.Snapshot, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return catalog.Snapshot{}, err
	}
	var s catalog.Snapshot
	if err := json.Unmarshal(b, &s); err != nil {
		return catalog.Snapshot{}, fmt.Errorf("%s: %w", path, err)
	}
	if s.SchemaVersion != catalog.SchemaVersion {
		return catalog.Snapshot{}, fmt.Errorf("%s: schema_version %d, ожидали %d", path, s.SchemaVersion, catalog.SchemaVersion)
	}
	return s, nil
}

func writeJSON(path string, v any) error {
	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetEscapeHTML(false)
	enc.SetIndent("", "  ")
	if err := enc.Encode(v); err != nil {
		return err
	}
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, buf.Bytes(), 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}
