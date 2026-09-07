package model

import (
	"crypto/rand"
	_ "embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"math"
	"strings"
	"time"
	"unicode/utf16"
)

type Sound struct {
	ID          string  `json:"id"`
	Name        string  `json:"name"`
	Description string  `json:"description"`
	Category    string  `json:"category"`
	Volume      float64 `json:"volume"`
}
type Layer struct {
	Volume  float64 `json:"volume"`
	Enabled bool    `json:"enabled"`
}
type Mix struct {
	Name         string
	Master       float64
	Layers       map[string]Layer
	ActivePreset string
}
type Preset struct {
	ID        string           `json:"id"`
	Name      string           `json:"name"`
	Note      string           `json:"note"`
	Layers    map[string]Layer `json:"layers"`
	CreatedAt int64            `json:"createdAt"`
}
type Snapshot struct {
	Mix         Mix
	Presets     []Preset
	StayRunning bool
}

//go:embed catalog.json
var catalog []byte
var Sounds []Sound
var BuiltIns []Preset
var ByID = map[string]Sound{}

func init() {
	var c struct {
		Sounds  []Sound
		Presets []Preset
	}
	if err := json.Unmarshal(catalog, &c); err != nil {
		panic(err)
	}
	Sounds, BuiltIns = c.Sounds, c.Presets
	for _, s := range Sounds {
		ByID[s.ID] = s
	}
}
func Default() Snapshot {
	s := Snapshot{Mix: Mix{Master: .38}}
	s.Apply(BuiltIns[0])
	return s
}
func Clamp(v float64) float64 {
	if math.IsNaN(v) || math.IsInf(v, 0) {
		return 0
	}
	return math.Max(0, math.Min(1, v))
}
func Clone(layers map[string]Layer) map[string]Layer {
	out := make(map[string]Layer, len(layers))
	for k, v := range layers {
		out[k] = v
	}
	return out
}
func (s *Snapshot) Apply(p Preset) {
	s.Mix.Name = p.Name
	s.Mix.ActivePreset = p.ID
	s.Mix.Layers = Clone(p.Layers)
}
func (s *Snapshot) Edited() { s.Mix.Name = "Custom mix"; s.Mix.ActivePreset = "" }
func ValidName(name string) bool {
	return strings.TrimSpace(name) != "" && len(utf16.Encode([]rune(name))) <= 120
}
func (s *Snapshot) SavePreset(name string, now time.Time) error {
	name = strings.TrimSpace(name)
	if !ValidName(name) {
		return errors.New("Use a name of 1–120 characters")
	}
	if len(s.Mix.Layers) == 0 {
		return errors.New("Add a sound before saving a preset")
	}
	if len(s.Presets) >= 1000 {
		return errors.New("The portable backup supports up to 1,000 custom presets")
	}
	var id [8]byte
	if _, err := rand.Read(id[:]); err != nil {
		return err
	}
	p := Preset{ID: "custom_" + hex.EncodeToString(id[:]), Name: name, Layers: Clone(s.Mix.Layers), CreatedAt: now.UnixMilli()}
	s.Presets = append(s.Presets, p)
	s.Apply(p)
	return nil
}
func (s *Snapshot) DeletePreset(id string) {
	for i, p := range s.Presets {
		if p.ID == id {
			s.Presets = append(s.Presets[:i], s.Presets[i+1:]...)
			if s.Mix.ActivePreset == id {
				s.Edited()
			}
			return
		}
	}
}
func (s Snapshot) AllPresets() []Preset { return append(append([]Preset{}, BuiltIns...), s.Presets...) }

// Timer follows playback time, including a gentle final 30-second fade.
type Timer struct {
	Remaining time.Duration
	Running   bool
	last      time.Time
}

func (t *Timer) Set(d time.Duration, playing bool, now time.Time) {
	t.Remaining = d
	t.Running = playing
	t.last = now
}
func (t *Timer) Tick(now time.Time) bool {
	if !t.Running || t.Remaining <= 0 {
		t.last = now
		return false
	}
	t.Remaining -= max(0, now.Sub(t.last))
	t.last = now
	if t.Remaining <= 0 {
		t.Remaining = 0
		t.Running = false
		return true
	}
	return false
}
func (t *Timer) Play(playing bool, now time.Time) { t.Tick(now); t.Running = playing; t.last = now }
func (t Timer) Fade() float64 {
	if t.Remaining <= 0 {
		return 1
	}
	return math.Min(1, t.Remaining.Seconds()/30)
}
