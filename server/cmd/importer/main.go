// Команда importer собирает снимок каталога из публичного ответа портала
// parkingkrd.ru.
//
// Из сохранённого файла (воспроизводимо, без сети):
//
//	go run ./cmd/importer -input ../data/raw/parkingkrd-zones-2026-10-13.json
//
// С портала (адрес запроса нужно подтвердить, см. docs/research/parkingkrd.md):
//
//	go run ./cmd/importer -url 'https://parkingkrd.ru/<публичный адрес списка>'
//
// Сырой ответ при загрузке сохраняется в data/raw вместе с .meta.json, чтобы
// следующий импорт можно было повторить из файла.
package main

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/khudob1n/krasnodar-parking-demo/server/internal/catalog"
	"github.com/khudob1n/krasnodar-parking-demo/server/internal/portal"
	"github.com/khudob1n/krasnodar-parking-demo/server/internal/snapshot"
	"github.com/khudob1n/krasnodar-parking-demo/server/internal/validate"
)

// rawMeta — описание происхождения сырого файла (*.meta.json).
type rawMeta struct {
	File                  string  `json:"file"`
	SHA256                string  `json:"sha256"`
	Publisher             string  `json:"publisher"`
	SourceURL             string  `json:"source_url"`
	EndpointURL           *string `json:"endpoint_url"`
	FetchedAt             string  `json:"fetched_at"`
	FetchedAtPrecision    string  `json:"fetched_at_precision"`
	Provenance            string  `json:"provenance,omitempty"`
	VerifiedAgainstPortal bool    `json:"verified_against_portal"`
	EndpointNote          string  `json:"endpoint_note,omitempty"`
}

func main() {
	var (
		input    = flag.String("input", "", "сохранённый ответ портала (JSON); рядом ищется <имя>.meta.json")
		url      = flag.String("url", "", "публичный адрес списка парковок на портале; взаимоисключающе с -input")
		rawDir   = flag.String("raw-dir", "../data/raw", "куда сохранить сырой ответ при -url")
		cards    = flag.String("cards", "../data/reference/control-cards.json", "контрольные карточки")
		out      = flag.String("out", "../data/snapshot", "каталог для снимка")
		sourceID = flag.String("source-id", "parkingkrd", "префикс наших id")
		now      = flag.String("generated-at", "", "время сборки RFC3339 (для воспроизводимых сборок); по умолчанию — сейчас")
	)
	flag.Parse()
	log := slog.New(slog.NewTextHandler(os.Stderr, nil))
	if err := run(log, *input, *url, *rawDir, *cards, *out, *sourceID, *now); err != nil {
		log.Error("импорт не выполнен", "err", err)
		os.Exit(1)
	}
}

func run(log *slog.Logger, input, url, rawDir, cardsPath, out, sourceID, now string) error {
	if (input == "") == (url == "") {
		return fmt.Errorf("укажите ровно один из -input или -url")
	}
	var raw []byte
	var meta rawMeta
	var err error
	if url != "" {
		raw, meta, err = fetchAndStore(url, rawDir)
	} else {
		raw, meta, err = readStored(input)
	}
	if err != nil {
		return err
	}
	fetchedAt, err := time.Parse(time.RFC3339, meta.FetchedAt)
	if err != nil {
		return fmt.Errorf("fetched_at %q: %w", meta.FetchedAt, err)
	}

	resp, schema, err := portal.Decode(raw)
	if err != nil {
		return err
	}
	parkings, problems := portal.Convert(resp, sourceID)

	src := catalog.Source{
		ID:                    sourceID,
		Publisher:             meta.Publisher,
		SourceURL:             meta.SourceURL,
		EndpointURL:           meta.EndpointURL,
		FetchedAt:             fetchedAt.UTC(),
		FetchedAtPrecision:    meta.FetchedAtPrecision,
		RawSHA256:             meta.SHA256,
		Provenance:            meta.Provenance,
		VerifiedAgainstPortal: meta.VerifiedAgainstPortal,
		Note:                  meta.EndpointNote,
	}
	generated := time.Now()
	if now != "" {
		if generated, err = time.Parse(time.RFC3339, now); err != nil {
			return fmt.Errorf("-generated-at: %w", err)
		}
	}
	excluded := len(resp.Data) - len(parkings)
	snap, err := snapshot.Build(parkings, []catalog.Source{src}, excluded, generated)
	if err != nil {
		return err
	}

	cards, err := validate.LoadCards(cardsPath)
	if err != nil {
		return fmt.Errorf("контрольные карточки: %w", err)
	}
	rep := validate.Run(snap, schema, problems, cards, validate.DefaultLimits)
	for _, c := range rep.Checks {
		level := slog.LevelInfo
		if !c.OK {
			level = slog.LevelWarn
		}
		log.Log(context.Background(), level, "проверка", "name", c.Name, "ok", c.OK, "blocking", c.Blocking, "detail", c.Detail)
	}
	if failed := rep.Failed(); len(failed) > 0 {
		names := make([]string, len(failed))
		for i, f := range failed {
			names[i] = f.Name
		}
		// Отчёт всё равно пишем рядом, чтобы было видно, что сломалось,
		// но снимок не заменяем.
		_ = os.MkdirAll(out, 0o755)
		b, _ := json.MarshalIndent(rep, "", "  ")
		_ = os.WriteFile(filepath.Join(out, "validation-report.failed.json"), b, 0o644)
		return fmt.Errorf("не прошли блокирующие проверки: %s", strings.Join(names, ", "))
	}
	if err := snapshot.Write(out, snap, rep); err != nil {
		return err
	}
	log.Info("снимок записан", "dir", out, "version", snap.Version, "total", snap.Counts.Total,
		"paid", snap.Counts.Paid, "with_geometry", snap.Counts.WithGeometry, "excluded", snap.Counts.Excluded)
	return nil
}

func readStored(path string) ([]byte, rawMeta, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, rawMeta{}, err
	}
	metaPath := strings.TrimSuffix(path, filepath.Ext(path)) + ".meta.json"
	mb, err := os.ReadFile(metaPath)
	if err != nil {
		return nil, rawMeta{}, fmt.Errorf("нет описания происхождения %s: %w", metaPath, err)
	}
	var meta rawMeta
	if err := json.Unmarshal(mb, &meta); err != nil {
		return nil, rawMeta{}, fmt.Errorf("%s: %w", metaPath, err)
	}
	sum := sha256.Sum256(raw)
	if got := hex.EncodeToString(sum[:]); meta.SHA256 != "" && got != meta.SHA256 {
		return nil, rawMeta{}, fmt.Errorf("sha256 %s не совпадает с %s из %s", got, meta.SHA256, metaPath)
	}
	if meta.SourceURL == "" || meta.FetchedAt == "" {
		return nil, rawMeta{}, fmt.Errorf("%s: нужны source_url и fetched_at", metaPath)
	}
	return raw, meta, nil
}

func fetchAndStore(url, rawDir string) ([]byte, rawMeta, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()
	f, err := portal.Fetch(ctx, nil, url)
	if err != nil {
		return nil, rawMeta{}, err
	}
	sum := sha256.Sum256(f.Body)
	name := "parkingkrd-" + f.FetchedAt.Format("20060102T150405Z") + ".json"
	endpoint := url
	meta := rawMeta{
		File:               name,
		SHA256:             hex.EncodeToString(sum[:]),
		Publisher:          "parkingkrd.ru (портал «Городские парковки»)",
		SourceURL:          "https://parkingkrd.ru/",
		EndpointURL:        &endpoint,
		FetchedAt:          f.FetchedAt.Format(time.RFC3339),
		FetchedAtPrecision: "exact",
		Provenance:         "importer -url, User-Agent " + portal.UserAgent,
	}
	if err := os.MkdirAll(rawDir, 0o755); err != nil {
		return nil, rawMeta{}, err
	}
	if err := os.WriteFile(filepath.Join(rawDir, name), f.Body, 0o644); err != nil {
		return nil, rawMeta{}, err
	}
	mb, _ := json.MarshalIndent(meta, "", "  ")
	if err := os.WriteFile(filepath.Join(rawDir, strings.TrimSuffix(name, ".json")+".meta.json"), append(mb, '\n'), 0o644); err != nil {
		return nil, rawMeta{}, err
	}
	return f.Body, meta, nil
}
