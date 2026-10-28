package catalog

import "time"

// FeatureCollection — минимальный GeoJSON (RFC 7946) для снимка.
type FeatureCollection struct {
	Type     string    `json:"type"`
	Version  string    `json:"version"`
	Features []Feature `json:"features"`
}

// Feature — объект GeoJSON.
type Feature struct {
	Type       string         `json:"type"`
	ID         string         `json:"id"`
	Geometry   Geometry       `json:"geometry"`
	Properties map[string]any `json:"properties"`
}

// Geometry — Point или MultiLineString. Порядок координат GeoJSON: [lon, lat].
type Geometry struct {
	Type        string `json:"type"`
	Coordinates any    `json:"coordinates"`
}

// ToGeoJSON строит слой для карты. Парковки без геометрии в слой не попадают:
// их видно в списке, но на карте их не рисуем.
func (s Snapshot) ToGeoJSON() FeatureCollection {
	src := make(map[string]Source, len(s.Sources))
	for _, x := range s.Sources {
		src[x.ID] = x
	}
	fc := FeatureCollection{Type: "FeatureCollection", Version: s.Version, Features: []Feature{}}
	for _, p := range s.Parkings {
		var g Geometry
		switch p.GeometryStatus {
		case GeometrySegments:
			lines := make([][][2]float64, 0, len(p.Segments))
			for _, seg := range p.Segments {
				lines = append(lines, [][2]float64{{seg[0].Lon, seg[0].Lat}, {seg[1].Lon, seg[1].Lat}})
			}
			g = Geometry{Type: "MultiLineString", Coordinates: lines}
		case GeometryPointOnly:
			g = Geometry{Type: "Point", Coordinates: [2]float64{p.Location.Lon, p.Location.Lat}}
		default:
			continue
		}
		props := map[string]any{
			"portal_id":          p.PortalID,
			"kind":               p.Kind,
			"paid":               p.Paid,
			"address":            p.Address,
			"price_per_hour_rub": p.Tariff.PricePerHourRub,
			"paid_from":          p.Tariff.PaidFrom,
			"paid_to":            p.Tariff.PaidTo,
			"capacity_total":     p.Capacity.Total,
			"tariff_zone":        p.TariffZone,
		}
		if x, ok := src[p.Source]; ok {
			props["source_url"] = x.SourceURL
			props["fetched_at"] = x.FetchedAt.Format(time.RFC3339)
			props["fetched_at_precision"] = x.FetchedAtPrecision
		}
		fc.Features = append(fc.Features, Feature{Type: "Feature", ID: p.ID, Geometry: g, Properties: props})
	}
	return fc
}
