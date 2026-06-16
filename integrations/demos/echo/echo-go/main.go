package main

import (
	"encoding/json"
	"io"
	"net/http"
	"strings"
)

type pluginEvent struct {
	Type    string                 `json:"type"`
	Text    string                 `json:"text"`
	Payload map[string]interface{} `json:"payload"`
}

type pluginMessage struct {
	Text   string `json:"text"`
	Format string `json:"format"`
}

type pluginResponse struct {
	Messages []pluginMessage `json:"messages"`
}

func main() {
	http.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})
	http.HandleFunc("/v1/plugin/handle", handlePlugin)
	_ = http.ListenAndServe(":8080", nil)
}

func handlePlugin(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	body, _ := io.ReadAll(r.Body)
	var event pluginEvent
	_ = json.Unmarshal(body, &event)
	text := strings.TrimSpace(strings.ToLower(event.Text))
	if text == "ping" {
		writeJSON(w, pluginResponse{Messages: []pluginMessage{{Text: "pong (echo-go)", Format: "markdown"}}})
		return
	}
	if event.Type == "slash" && strings.HasPrefix(event.Text, "/echo ") {
		writeJSON(w, pluginResponse{Messages: []pluginMessage{{Text: strings.TrimSpace(event.Text[6:]), Format: "markdown"}}})
		return
	}
	writeJSON(w, pluginResponse{Messages: []pluginMessage{{Text: "Echo Go sidecar. Try: ping, /echo text", Format: "markdown"}}})
}

func writeJSON(w http.ResponseWriter, v pluginResponse) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(v)
}
