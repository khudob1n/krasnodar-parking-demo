package snapshot

import (
	"encoding/json"
	"os"
	"testing"

	"github.com/santhosh-tekuri/jsonschema/v6"
)

// Снимок в data/snapshot соответствует JSON Schema, по которой его читают клиенты.
func TestSnapshotMatchesSchema(t *testing.T) {
	const (
		schemaPath = "../../../schema/parkings.schema.json"
		dataPath   = "../../../data/snapshot/" + ParkingsFile
	)
	if _, err := os.Stat(dataPath); err != nil {
		t.Skip("снимка нет:", err)
	}
	c := jsonschema.NewCompiler()
	c.AssertFormat()
	sch, err := c.Compile(schemaPath)
	if err != nil {
		t.Fatal(err)
	}
	f, err := os.Open(dataPath)
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	doc, err := jsonschema.UnmarshalJSON(f)
	if err != nil {
		t.Fatal(err)
	}
	if err := sch.Validate(doc); err != nil {
		t.Fatalf("снимок не соответствует схеме: %v", err)
	}

	// И обратная проверка: схема действительно что-то запрещает.
	var bad map[string]any
	b, _ := os.ReadFile(dataPath)
	json.Unmarshal(b, &bad)
	p := bad["parkings"].([]any)[0].(map[string]any)
	p["free_places"] = map[string]any{"value": 37, "updated_at": nil, "status": "no_data"}
	if err := sch.Validate(bad); err == nil {
		t.Fatal("схема пропустила свободные места без статуса available")
	}
}
