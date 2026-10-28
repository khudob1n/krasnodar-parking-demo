// Package api — HTTP-интерфейс каталога для мобильных клиентов.
//
// Сервер отдаёт только сохранённый снимок. Он ничего не знает о платежах и
// не изображает муниципальную систему.
package api

import (
	"bytes"
	"compress/gzip"
	"encoding/json"
	"log/slog"
	"math"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/khudob1n/krasnodar-parking-demo/server/internal/catalog"
	"github.com/khudob1n/krasnodar-parking-demo/server/internal/portal"
)

// Server держит снимок в памяти вместе с заранее сжатыми ответами.
type Server struct {
	snap    catalog.Snapshot
	byKey   map[string]int
	full    []byte
	fullGz  []byte
	geo     []byte
	geoGz   []byte
	log     *slog.Logger
	started time.Time
}

// New готовит сервер к работе со снимком.
func New(s catalog.Snapshot, log *slog.Logger) (*Server, error) {
	srv := &Server{snap: s, byKey: map[string]int{}, log: log, started: time.Now()}
	for i, p := range s.Parkings {
		srv.byKey[p.ID] = i
		srv.byKey[strconv.Itoa(p.PortalID)] = i
	}
	var err error
	if srv.full, srv.fullGz, err = encode(s); err != nil {
		return nil, err
	}
	if srv.geo, srv.geoGz, err = encode(s.ToGeoJSON()); err != nil {
		return nil, err
	}
	return srv, nil
}

// Handler возвращает маршруты.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", s.health)
	mux.HandleFunc("GET /api/v1/catalog/meta", s.meta)
	mux.HandleFunc("GET /api/v1/parkings", s.parkings)
	mux.HandleFunc("GET /api/v1/parkings.geojson", s.geojson)
	mux.HandleFunc("GET /api/v1/parkings/{id}", s.parking)
	mux.HandleFunc("GET /api/v1/search", s.search)
	mux.HandleFunc("GET /api/v1/nearby", s.nearby)
	return s.logged(mux)
}

func (s *Server) logged(h http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		rw := &statusWriter{ResponseWriter: w, code: 200}
		h.ServeHTTP(rw, r)
		// Запрос поиска может содержать что угодно, поэтому пишем только путь.
		s.log.Info("http", "method", r.Method, "path", r.URL.Path, "status", rw.code, "dur", time.Since(start).Round(time.Microsecond))
	})
}

type statusWriter struct {
	http.ResponseWriter
	code int
}

func (w *statusWriter) WriteHeader(c int) { w.code = c; w.ResponseWriter.WriteHeader(c) }

func (s *Server) health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "catalog_version": s.snap.Version})
}

// Meta — то, что клиент проверяет при каждом запуске, прежде чем качать каталог.
type Meta struct {
	SchemaVersion int              `json:"schema_version"`
	Version       string           `json:"version"`
	GeneratedAt   time.Time        `json:"generated_at"`
	Sources       []catalog.Source `json:"sources"`
	Counts        catalog.Counts   `json:"counts"`
	Disclaimer    string           `json:"disclaimer"`
}

// Disclaimer показывается в приложении рядом с данными.
const Disclaimer = "Демо-версия. Данные взяты из публичного снимка портала parkingkrd.ru и могут быть неполными или устаревшими. Это не официальный сервис города."

func (s *Server) meta(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, Meta{
		SchemaVersion: s.snap.SchemaVersion, Version: s.snap.Version, GeneratedAt: s.snap.GeneratedAt,
		Sources: s.snap.Sources, Counts: s.snap.Counts, Disclaimer: Disclaimer,
	})
}

func (s *Server) parkings(w http.ResponseWriter, r *http.Request) {
	s.serveCached(w, r, "application/json", s.full, s.fullGz)
}

func (s *Server) geojson(w http.ResponseWriter, r *http.Request) {
	s.serveCached(w, r, "application/geo+json", s.geo, s.geoGz)
}

func (s *Server) serveCached(w http.ResponseWriter, r *http.Request, ctype string, plain, gz []byte) {
	etag := `"` + s.snap.Version + `"`
	h := w.Header()
	h.Set("ETag", etag)
	h.Set("Cache-Control", "no-cache")
	h.Set("Vary", "Accept-Encoding")
	if match := r.Header.Get("If-None-Match"); match != "" && match == etag {
		w.WriteHeader(http.StatusNotModified)
		return
	}
	h.Set("Content-Type", ctype+"; charset=utf-8")
	body := plain
	if strings.Contains(r.Header.Get("Accept-Encoding"), "gzip") {
		h.Set("Content-Encoding", "gzip")
		body = gz
	}
	h.Set("Content-Length", strconv.Itoa(len(body)))
	w.WriteHeader(http.StatusOK)
	w.Write(body)
}

func (s *Server) parking(w http.ResponseWriter, r *http.Request) {
	i, ok := s.byKey[r.PathValue("id")]
	if !ok {
		writeError(w, http.StatusNotFound, "парковка не найдена")
		return
	}
	writeJSON(w, http.StatusOK, s.snap.Parkings[i])
}

// Hit — строка результата поиска.
type Hit struct {
	Parking  catalog.Parking `json:"parking"`
	Distance *float64        `json:"distance_m,omitempty"`
}

func (s *Server) search(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	limit := intParam(q.Get("limit"), 20, 1, 100)
	paidOnly := q.Get("paid") != "false"
	hits := Search(s.snap.Parkings, q.Get("q"), paidOnly, limit)
	out := make([]Hit, len(hits))
	for i, p := range hits {
		out[i] = Hit{Parking: p}
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": out, "catalog_version": s.snap.Version})
}

func (s *Server) nearby(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	lat, err1 := strconv.ParseFloat(q.Get("lat"), 64)
	lon, err2 := strconv.ParseFloat(q.Get("lon"), 64)
	if err1 != nil || err2 != nil || math.Abs(lat) > 90 || math.Abs(lon) > 180 {
		writeError(w, http.StatusBadRequest, "нужны lat и lon")
		return
	}
	radius := float64(intParam(q.Get("radius"), 500, 10, 5000))
	limit := intParam(q.Get("limit"), 10, 1, 50)
	here := catalog.Point{Lat: lat, Lon: lon}
	var out []Hit
	for _, p := range s.snap.Parkings {
		if !p.Paid {
			continue
		}
		d, ok := DistanceTo(p, here)
		if !ok || d > radius {
			continue
		}
		dd := math.Round(d)
		out = append(out, Hit{Parking: p, Distance: &dd})
	}
	sort.Slice(out, func(a, b int) bool { return *out[a].Distance < *out[b].Distance })
	if len(out) > limit {
		out = out[:limit]
	}
	if out == nil {
		out = []Hit{}
	}
	// Это только предложение вариантов: зону выбирает и подтверждает водитель.
	writeJSON(w, http.StatusOK, map[string]any{"items": out, "note": "Ближайшие варианты по геолокации. Зону подтверждает пользователь."})
}

// Search ищет по номеру из портала и словам адреса.
func Search(ps []catalog.Parking, query string, paidOnly bool, limit int) []catalog.Parking {
	tokens := strings.Fields(Normalize(query))
	if len(tokens) == 0 {
		return []catalog.Parking{}
	}
	type scored struct {
		p     catalog.Parking
		score int
	}
	var res []scored
	for _, p := range ps {
		if paidOnly && !p.Paid {
			continue
		}
		id := strconv.Itoa(p.PortalID)
		hay := Normalize(p.Address + " " + p.Description)
		score := 0
		for _, t := range tokens {
			switch {
			case t == id:
				score += 100
			case strings.Contains(hay, t):
				score += 10
			default:
				score = -1
			}
			if score < 0 {
				break
			}
		}
		if score > 0 {
			res = append(res, scored{p, score})
		}
	}
	sort.SliceStable(res, func(a, b int) bool {
		if res[a].score != res[b].score {
			return res[a].score > res[b].score
		}
		return res[a].p.PortalID < res[b].p.PortalID
	})
	out := make([]catalog.Parking, 0, min(limit, len(res)))
	for i := 0; i < len(res) && i < limit; i++ {
		out = append(out, res[i].p)
	}
	return out
}

var normalizer = strings.NewReplacer("ё", "е", ".", " ", ",", " ", "№", " ", "(", " ", ")", " ")

// Normalize приводит текст к виду для поиска: регистр, ё/е, без знаков.
func Normalize(s string) string {
	return strings.Join(strings.Fields(normalizer.Replace(strings.ToLower(s))), " ")
}

// DistanceTo — расстояние до ближайшей известной точки парковки: опорной
// точки или концов её участков.
func DistanceTo(p catalog.Parking, here catalog.Point) (float64, bool) {
	best, ok := math.Inf(1), false
	if p.Location != nil {
		best, ok = portal.Meters(*p.Location, here), true
	}
	for _, seg := range p.Segments {
		for _, pt := range seg {
			if d := portal.Meters(pt, here); d < best {
				best, ok = d, true
			}
		}
	}
	return best, ok
}

func intParam(s string, def, lo, hi int) int {
	v, err := strconv.Atoi(s)
	if err != nil {
		return def
	}
	return max(lo, min(hi, v))
}

func encode(v any) ([]byte, []byte, error) {
	plain, err := json.Marshal(v)
	if err != nil {
		return nil, nil, err
	}
	var buf bytes.Buffer
	zw, _ := gzip.NewWriterLevel(&buf, gzip.BestCompression)
	zw.Write(plain)
	if err := zw.Close(); err != nil {
		return nil, nil, err
	}
	return plain, buf.Bytes(), nil
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	enc := json.NewEncoder(w)
	enc.SetEscapeHTML(false)
	enc.Encode(v)
}

func writeError(w http.ResponseWriter, code int, msg string) {
	writeJSON(w, code, map[string]string{"error": msg})
}
