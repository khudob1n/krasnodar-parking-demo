// Package validate проверяет собранный каталог целиком: структуру,
// дубликаты, координаты и контрольные карточки, сверенные с независимыми
// источниками.
package validate

import (
	"encoding/json"
	"fmt"
	"os"
	"sort"
	"strings"
	"time"

	"github.com/khudob1n/krasnodar-parking-demo/server/internal/catalog"
	"github.com/khudob1n/krasnodar-parking-demo/server/internal/portal"
)

// ControlCard — ожидаемые значения одной парковки из внешнего источника.
// Сравниваются только заданные поля.
type ControlCard struct {
	PortalID        int      `json:"portal_id"`
	AddressContains []string `json:"address_contains"`
	PricePerHourRub *float64 `json:"price_per_hour_rub,omitempty"`
	CapacityTotal   *int     `json:"capacity_total,omitempty"`
	PaidFrom        *string  `json:"paid_from,omitempty"`
	PaidTo          *string  `json:"paid_to,omitempty"`
	SourceURL       string   `json:"source_url"`
	SourcePublished string   `json:"source_published"`
	SourceKind      string   `json:"source_kind"`
	Note            string   `json:"note,omitempty"`
}

// CardResult — итог сверки карточки.
type CardResult struct {
	PortalID   int      `json:"portal_id"`
	OK         bool     `json:"ok"`
	Mismatches []string `json:"mismatches"`
	SourceURL  string   `json:"source_url"`
	Published  string   `json:"source_published"`
}

// Check — проверка уровня каталога.
type Check struct {
	Name   string `json:"name"`
	OK     bool   `json:"ok"`
	Detail string `json:"detail"`
	// Blocking — провал останавливает импорт.
	Blocking bool `json:"blocking"`
}

// Report пишется рядом со снимком как validation-report.json.
type Report struct {
	GeneratedAt    time.Time           `json:"generated_at"`
	SnapshotVer    string              `json:"snapshot_version"`
	Schema         portal.SchemaReport `json:"schema"`
	ProblemsByCode map[string]int      `json:"problems_by_code"`
	Problems       []portal.Problem    `json:"problems"`
	Excluded       []int               `json:"excluded_portal_ids"`
	Checks         []Check             `json:"checks"`
	ControlCards   []CardResult        `json:"control_cards"`
	Counts         catalog.Counts      `json:"counts"`
}

// Failed — есть ли блокирующие провалы.
func (r Report) Failed() []Check {
	var out []Check
	for _, c := range r.Checks {
		if c.Blocking && !c.OK {
			out = append(out, c)
		}
	}
	return out
}

// LoadCards читает контрольные карточки.
func LoadCards(path string) ([]ControlCard, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var cards []ControlCard
	if err := json.Unmarshal(b, &cards); err != nil {
		return nil, fmt.Errorf("%s: %w", path, err)
	}
	return cards, nil
}

// Limits — пороги проверок уровня каталога.
type Limits struct {
	// MaxFatalShare — допустимая доля исключённых записей.
	MaxFatalShare float64
	// MinPaidGeometryShare — минимальная доля платных парковок с линией или точкой.
	MinPaidGeometryShare float64
}

// DefaultLimits подобраны по снимку от 13.10.2026: там исключается 0 записей,
// а без геометрии остаётся около 2% платных.
var DefaultLimits = Limits{MaxFatalShare: 0.01, MinPaidGeometryShare: 0.95}

// Run собирает отчёт.
func Run(s catalog.Snapshot, schema portal.SchemaReport, problems []portal.Problem, cards []ControlCard, lim Limits) Report {
	rep := Report{
		GeneratedAt:    s.GeneratedAt,
		SnapshotVer:    s.Version,
		Schema:         schema,
		ProblemsByCode: map[string]int{},
		Problems:       problems,
		Excluded:       []int{},
		Counts:         s.Counts,
	}
	if rep.Problems == nil {
		rep.Problems = []portal.Problem{}
	}
	excluded := map[int]bool{}
	for _, p := range problems {
		rep.ProblemsByCode[p.Code]++
		if p.Fatal && !excluded[p.PortalID] {
			excluded[p.PortalID] = true
			rep.Excluded = append(rep.Excluded, p.PortalID)
		}
	}
	sort.Ints(rep.Excluded)

	add := func(name string, ok, blocking bool, format string, a ...any) {
		rep.Checks = append(rep.Checks, Check{Name: name, OK: ok, Blocking: blocking, Detail: fmt.Sprintf(format, a...)})
	}

	add("schema_required_fields", len(schema.RecordsInvalid) == 0, false,
		"записей без обязательных полей: %d", len(schema.RecordsInvalid))
	add("schema_unknown_fields", len(schema.UnknownFields) == 0, false,
		"новые поля источника: %v", keys(schema.UnknownFields))
	if schema.TotalLength != nil {
		add("total_length_matches", *schema.TotalLength == schema.Records, true,
			"totalLength=%d, записей=%d (расхождение — признак пагинации)", *schema.TotalLength, schema.Records)
	} else {
		add("total_length_matches", false, false, "источник не отдал totalLength; полноту выгрузки проверить нечем")
	}
	share := 0.0
	if schema.Records > 0 {
		share = float64(len(rep.Excluded)) / float64(schema.Records)
	}
	add("fatal_share", share <= lim.MaxFatalShare, true, "исключено %d из %d (%.2f%%)", len(rep.Excluded), schema.Records, share*100)

	ids := map[string]bool{}
	dups := 0
	for _, p := range s.Parkings {
		if ids[p.ID] {
			dups++
		}
		ids[p.ID] = true
	}
	add("unique_ids", dups == 0, true, "дубликатов id в каталоге: %d", dups)

	addrDup := duplicateAddresses(s.Parkings)
	add("duplicate_addresses", len(addrDup) == 0, false, "одинаковый адрес и тип у разных id: %d групп %v", len(addrDup), trim(addrDup, 10))

	paid, paidGeo := 0, 0
	for _, p := range s.Parkings {
		if p.Paid {
			paid++
			if p.GeometryStatus != catalog.GeometryNone {
				paidGeo++
			}
		}
	}
	gs := 0.0
	if paid > 0 {
		gs = float64(paidGeo) / float64(paid)
	}
	add("paid_geometry_share", gs >= lim.MinPaidGeometryShare, true, "платных с геометрией %d из %d (%.1f%%)", paidGeo, paid, gs*100)

	byPortal := map[int]catalog.Parking{}
	for _, p := range s.Parkings {
		byPortal[p.PortalID] = p
	}
	cardsOK := true
	for _, c := range cards {
		res := checkCard(c, byPortal)
		cardsOK = cardsOK && res.OK
		rep.ControlCards = append(rep.ControlCards, res)
	}
	add("control_cards", cardsOK && len(cards) > 0, true, "карточек: %d, совпали: %d", len(cards), countOK(rep.ControlCards))
	return rep
}

func checkCard(c ControlCard, by map[int]catalog.Parking) CardResult {
	res := CardResult{PortalID: c.PortalID, SourceURL: c.SourceURL, Published: c.SourcePublished, Mismatches: []string{}}
	p, ok := by[c.PortalID]
	if !ok {
		res.Mismatches = append(res.Mismatches, "парковки нет в каталоге")
		return res
	}
	low := strings.ToLower(p.Address + " " + p.Description)
	for _, part := range c.AddressContains {
		if !strings.Contains(low, strings.ToLower(part)) {
			res.Mismatches = append(res.Mismatches, fmt.Sprintf("в адресе нет %q", part))
		}
	}
	if c.PricePerHourRub != nil && (p.Tariff.PricePerHourRub == nil || *p.Tariff.PricePerHourRub != *c.PricePerHourRub) {
		res.Mismatches = append(res.Mismatches, fmt.Sprintf("тариф %v, ожидали %v", deref(p.Tariff.PricePerHourRub), *c.PricePerHourRub))
	}
	if c.CapacityTotal != nil && (p.Capacity.Total == nil || *p.Capacity.Total != *c.CapacityTotal) {
		res.Mismatches = append(res.Mismatches, fmt.Sprintf("мест %v, ожидали %d", deref(p.Capacity.Total), *c.CapacityTotal))
	}
	if c.PaidFrom != nil && (p.Tariff.PaidFrom == nil || *p.Tariff.PaidFrom != *c.PaidFrom) {
		res.Mismatches = append(res.Mismatches, fmt.Sprintf("начало %v, ожидали %s", deref(p.Tariff.PaidFrom), *c.PaidFrom))
	}
	if c.PaidTo != nil && (p.Tariff.PaidTo == nil || *p.Tariff.PaidTo != *c.PaidTo) {
		res.Mismatches = append(res.Mismatches, fmt.Sprintf("окончание %v, ожидали %s", deref(p.Tariff.PaidTo), *c.PaidTo))
	}
	res.OK = len(res.Mismatches) == 0
	return res
}

func duplicateAddresses(ps []catalog.Parking) []string {
	groups := map[string][]int{}
	for _, p := range ps {
		k := string(p.Kind) + "|" + strings.ToLower(strings.NewReplacer("ё", "е", ".", " ", ",", " ").Replace(p.Address))
		k = strings.Join(strings.Fields(k), " ")
		groups[k] = append(groups[k], p.PortalID)
	}
	var out []string
	for _, ids := range groups {
		if len(ids) > 1 {
			sort.Ints(ids)
			out = append(out, fmt.Sprint(ids))
		}
	}
	sort.Strings(out)
	return out
}

func keys(m map[string]int) []string {
	out := make([]string, 0, len(m))
	for k := range m {
		out = append(out, k)
	}
	sort.Strings(out)
	return out
}

func trim(s []string, n int) []string {
	if len(s) > n {
		return s[:n]
	}
	return s
}

func countOK(rs []CardResult) int {
	n := 0
	for _, r := range rs {
		if r.OK {
			n++
		}
	}
	return n
}

func deref[T any](p *T) any {
	if p == nil {
		return "нет данных"
	}
	return *p
}
