package audio

import (
	"encoding/binary"
	"math"
	"os"
	"sync"
	"testing"
	"time"

	"noizey/internal/model"
)

func TestEveryGeneratorIsFiniteStereoAndAudible(t *testing.T) {
	for _, sound := range model.Sounds {
		t.Run(sound.ID, func(t *testing.T) {
			g := newGenerator(sound.ID)
			energy, difference := 0.0, 0.0
			for i := 0; i < SampleRate*12; i++ {
				frame := g.next()
				for _, v := range frame {
					if math.IsNaN(v) || math.IsInf(v, 0) || math.Abs(v) > 1 {
						t.Fatalf("Invalid sample at %d: %v", i, frame)
					}
					energy += v * v
				}
				difference += math.Abs(frame[0] - frame[1])
			}
			if energy < 1 || difference < 1 {
				t.Fatalf("Generator silent or not stereo: energy=%g difference=%g", energy, difference)
			}
		})
	}
}
func samples(buf []byte) []float64 {
	out := make([]float64, len(buf)/4)
	for i := range out {
		out[i] = float64(math.Float32frombits(binary.LittleEndian.Uint32(buf[i*4:])))
	}
	return out
}
func TestMixerLimitsCrossfadesAndPause(t *testing.T) {
	r := NewRenderer()
	m := model.Default().Mix
	m.Master = 1
	for _, s := range model.Sounds {
		m.Layers[s.ID] = model.Layer{Volume: 1, Enabled: true}
	}
	r.Set(m, 1, true)
	buf := make([]byte, SampleRate*8)
	r.Render(buf)
	for _, v := range samples(buf) {
		if math.Abs(v) > 1 || math.IsNaN(v) {
			t.Fatal("Limiter failed")
		}
	}
	before := r.gains[0]
	m.Layers = map[string]model.Layer{"white": {Volume: 1, Enabled: true}}
	r.Set(m, 1, true)
	r.Render(make([]byte, 8))
	if r.gains[0] <= 0 || math.Abs(r.gains[0]-before) > .001 {
		t.Fatal("Preset transition jumped")
	}
	r.Set(m, 1, false)
	r.Render(buf)
	for _, v := range samples(buf[len(buf)-4800:]) {
		if v != 0 {
			t.Fatal("Pause failed to fade to exact silence")
		}
	}
	if r.Frames() != SampleRate*2+1 {
		t.Fatal("Frame accounting incorrect")
	}
}
func TestMuteMasterAndFadeReachSilence(t *testing.T) {
	for _, kind := range []string{"mute", "master", "fade"} {
		t.Run(kind, func(t *testing.T) {
			r := NewRenderer()
			m := model.Default().Mix
			fade := 1.0
			switch kind {
			case "mute":
				m.Layers["brown"] = model.Layer{Volume: 1, Enabled: false}
			case "master":
				m.Master = 0
			case "fade":
				fade = 0
			}
			r.Set(m, fade, true)
			buf := make([]byte, SampleRate*8)
			r.Render(buf)
			for _, v := range samples(buf) {
				if v != 0 {
					t.Fatal("Expected silence")
				}
			}
		})
	}
}
func TestConcurrentControls(t *testing.T) {
	r := NewRenderer()
	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		for i := 0; i < 100; i++ {
			m := model.Default().Mix
			m.Master = float64(i%100) / 100
			r.Set(m, 1, i%2 == 0)
			_ = r.Peak()
			_ = r.Frames()
		}
	}()
	buf := make([]byte, 1024*8)
	for i := 0; i < 100; i++ {
		r.Render(buf)
	}
	wg.Wait()
}
func BenchmarkFullMix(b *testing.B) {
	r := NewRenderer()
	m := model.Default().Mix
	for _, s := range model.Sounds {
		m.Layers[s.ID] = model.Layer{Volume: .5, Enabled: true}
	}
	r.Set(m, 1, true)
	buf := make([]byte, SampleRate*8)
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		r.Render(buf)
	}
}

func TestNativePlayback(t *testing.T) {
	if os.Getenv("NOIZEY_AUDIO_TEST") != "1" {
		t.Skip("Set NOIZEY_AUDIO_TEST=1 for a quiet physical-output probe")
	}
	e := New()
	defer e.Close()
	m := model.Default().Mix
	m.Master = .03
	e.Update(m, 1, true)
	if err := e.Start(); err != nil {
		t.Fatal(err)
	}
	defer e.Pause()
	t.Logf("Native output: %s", e.Output)
	deadline := time.Now().Add(3 * time.Second)
	for e.Renderer.Frames() < SampleRate/4 && time.Now().Before(deadline) {
		time.Sleep(10 * time.Millisecond)
	}
	if e.Renderer.Frames() < SampleRate/4 || e.Renderer.Peak() <= 0 {
		t.Fatal("Device did not consume audible PCM")
	}
	if changed, err := e.RouteChanged(); err != nil || changed {
		t.Fatal("Unexpected route change", err)
	}
	// A second nonexclusive stream must coexist without stopping the first.
	second := New()
	defer second.Close()
	second.Update(m, 1, true)
	if err := second.Start(); err != nil {
		t.Fatal("Shared output failed", err)
	}
	second.Pause()
	if !e.device.IsStarted() {
		t.Fatal("Second stream interrupted first")
	}
	e.Pause()
	before := e.Renderer.Frames()
	time.Sleep(30 * time.Millisecond)
	if e.device.IsStarted() || e.Renderer.Frames() != before {
		t.Fatal("Pause left the device running")
	}
	if err := e.Start(); err != nil {
		t.Fatal("Audio restart failed", err)
	}
}
