// Команда server отдаёт снимок каталога мобильному приложению.
//
//	go run ./cmd/server -snapshot ../data/snapshot/parkings.json -addr :8080
package main

import (
	"context"
	"errors"
	"flag"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/khudob1n/krasnodar-parking-demo/server/internal/api"
	"github.com/khudob1n/krasnodar-parking-demo/server/internal/snapshot"
)

func main() {
	addr := flag.String("addr", ":8080", "адрес HTTP")
	path := flag.String("snapshot", "../data/snapshot/parkings.json", "файл снимка")
	flag.Parse()
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))

	snap, err := snapshot.Load(*path)
	if err != nil {
		log.Error("снимок не загружен", "err", err)
		os.Exit(1)
	}
	srv, err := api.New(snap, log)
	if err != nil {
		log.Error("сервер не собран", "err", err)
		os.Exit(1)
	}
	hs := &http.Server{Addr: *addr, Handler: srv.Handler(), ReadHeaderTimeout: 5 * time.Second}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go func() {
		log.Info("слушаю", "addr", *addr, "catalog_version", snap.Version, "parkings", snap.Counts.Total)
		if err := hs.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Error("http", "err", err)
			stop()
		}
	}()
	<-ctx.Done()
	shutdown, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	hs.Shutdown(shutdown)
}
