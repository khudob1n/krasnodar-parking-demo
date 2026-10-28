package validate

import (
	"strconv"
	"testing"
	"time"

	"github.com/khudob1n/krasnodar-parking-demo/server/internal/catalog"
	"github.com/khudob1n/krasnodar-parking-demo/server/internal/portal"
	"github.com/khudob1n/krasnodar-parking-demo/server/internal/snapshot"
)

func f(v float64) *float64 { return &v }
func i(v int) *int         { return &v }

func build(t *testing.T, ps []catalog.Parking) catalog.Snapshot {
	t.Helper()
	s, err := snapshot.Build(ps, nil, 0, time.Unix(0, 0))
	if err != nil {
		t.Fatal(err)
	}
	return s
}

func parking(id int, addr string, price float64, places int) catalog.Parking {
	return catalog.Parking{
		ID: "t:" + strconv.Itoa(id), PortalID: id, Kind: catalog.KindStreet, Paid: true, Address: addr,
		Location: &catalog.Point{Lat: 45, Lon: 39}, GeometryStatus: catalog.GeometryPointOnly,
		Tariff: catalog.Tariff{PricePerHourRub: f(price)}, Capacity: catalog.Capacity{Total: i(places)},
	}
}

func TestControlCards(t *testing.T) {
	s := build(t, []catalog.Parking{
		parking(1446, "По обеим сторонам ул. им. Дзержинского, от ул. Дальней до ул. Шоссе Нефтяников", 40, 179),
		parking(1447, "Нечётная сторона ул. им. Калинина", 60, 17),
	})
	cards := []ControlCard{
		{PortalID: 1446, AddressContains: []string{"дзержинского", "Дальней"}, PricePerHourRub: f(40), CapacityTotal: i(179)},
		{PortalID: 1447, AddressContains: []string{"Калинина"}, PricePerHourRub: f(50)},
		{PortalID: 9999},
	}
	total := 2
	rep := Run(s, portal.SchemaReport{Records: 2, TotalLength: &total}, nil, cards, DefaultLimits)
	if !rep.ControlCards[0].OK {
		t.Fatalf("1446 должна совпасть: %v", rep.ControlCards[0].Mismatches)
	}
	if rep.ControlCards[1].OK || rep.ControlCards[2].OK {
		t.Fatal("расхождения не найдены")
	}
	failed := rep.Failed()
	if len(failed) != 1 || failed[0].Name != "control_cards" {
		t.Fatalf("ожидали провал только control_cards: %+v", failed)
	}
}

func TestPaginationAndFatalShare(t *testing.T) {
	s := build(t, []catalog.Parking{parking(1, "a", 40, 1)})
	total := 1716
	probs := []portal.Problem{{PortalID: 2, Code: "bad_id", Fatal: true}, {PortalID: 2, Code: "empty_address", Fatal: true}}
	rep := Run(s, portal.SchemaReport{Records: 2, TotalLength: &total}, probs, []ControlCard{{PortalID: 1, PricePerHourRub: f(40)}}, DefaultLimits)
	names := map[string]bool{}
	for _, c := range rep.Failed() {
		names[c.Name] = true
	}
	if !names["total_length_matches"] || !names["fatal_share"] {
		t.Fatalf("пагинация и доля исключённых должны блокировать: %+v", rep.Failed())
	}
	if len(rep.Excluded) != 1 || rep.Excluded[0] != 2 {
		t.Fatalf("исключённые считаются по записи, не по проблеме: %v", rep.Excluded)
	}
}

func TestGeometryShareAndDuplicates(t *testing.T) {
	a := parking(1, "ул. Красная", 60, 1)
	b := parking(2, "ул.  Красная.", 60, 1)
	b.GeometryStatus = catalog.GeometryNone
	s := build(t, []catalog.Parking{a, b})
	total := 2
	rep := Run(s, portal.SchemaReport{Records: 2, TotalLength: &total}, nil, []ControlCard{{PortalID: 1}}, DefaultLimits)
	var geo, dup Check
	for _, c := range rep.Checks {
		switch c.Name {
		case "paid_geometry_share":
			geo = c
		case "duplicate_addresses":
			dup = c
		}
	}
	if geo.OK {
		t.Fatal("50% геометрии не должно проходить")
	}
	if dup.OK || dup.Blocking {
		t.Fatalf("дубли адресов — предупреждение: %+v", dup)
	}
}
