package portal

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"time"
)

// UserAgent честно называет импортёр. Мы делаем один публичный запрос без
// cookies и авторизации.
const UserAgent = "krasnodar-parking-demo-importer/0.1 (+https://github.com/khudob1n)"

const maxBody = 64 << 20

// Fetched — сырой ответ и момент получения.
type Fetched struct {
	URL       string
	Body      []byte
	FetchedAt time.Time
	Status    int
	Headers   http.Header
}

// Fetch делает один GET к публичному адресу. Ответ 401/403/429 и любые
// перенаправления на вход считаются отказом: обходить их нельзя.
func Fetch(ctx context.Context, client *http.Client, url string) (Fetched, error) {
	if client == nil {
		client = &http.Client{
			Timeout: 60 * time.Second,
			// Перенаправления не выполняем: за ними обычно стоит вход.
			CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
		}
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return Fetched{}, err
	}
	req.Header.Set("User-Agent", UserAgent)
	req.Header.Set("Accept", "application/json")
	resp, err := client.Do(req)
	if err != nil {
		return Fetched{}, fmt.Errorf("запрос %s: %w", url, err)
	}
	defer resp.Body.Close()
	switch {
	case resp.StatusCode == http.StatusUnauthorized, resp.StatusCode == http.StatusForbidden:
		return Fetched{}, fmt.Errorf("%s ответил %d: доступ закрыт, импорт остановлен", url, resp.StatusCode)
	case resp.StatusCode == http.StatusTooManyRequests:
		return Fetched{}, fmt.Errorf("%s ответил 429: превышен лимит, повторите позже", url)
	case resp.StatusCode != http.StatusOK:
		return Fetched{}, fmt.Errorf("%s ответил %d", url, resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, maxBody+1))
	if err != nil {
		return Fetched{}, fmt.Errorf("чтение ответа: %w", err)
	}
	if len(body) > maxBody {
		return Fetched{}, fmt.Errorf("ответ больше %d байт", maxBody)
	}
	return Fetched{URL: url, Body: body, FetchedAt: time.Now().UTC(), Status: resp.StatusCode, Headers: resp.Header.Clone()}, nil
}
