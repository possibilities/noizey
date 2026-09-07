package model

import (
	"math"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestBackupRoundTripAndAndroidFieldFormat(t *testing.T) {
	s := Default()
	s.Mix.Master = .4321
	s.Mix.Layers = map[string]Layer{"pink": {.62, true}, "soft_rain": {.28, false}}
	if err := s.SavePreset("Rain desk 夜", time.UnixMilli(123)); err != nil {
		t.Fatal(err)
	}
	s.StayRunning = true
	decoded, err := Decode(Encode(s))
	if err != nil {
		t.Fatal(err)
	}
	if decoded.Mix.Name != s.Mix.Name || decoded.Mix.Master != .4321 || decoded.Mix.Layers["soft_rain"].Enabled || decoded.Presets[0].CreatedAt != 123 || !decoded.StayRunning {
		t.Fatalf("Lost settings: %+v", decoded)
	}
	if Encode(decoded) != Encode(s) {
		t.Fatal("Backup round trip changed canonical output")
	}
	// Kotlin's encoder uses URL-safe unpadded base64 and four-decimal layers.
	if !strings.Contains(Encode(s), "mix.layers=cGluaywwLjYyMDAsMXxzb2Z0X3JhaW4sMC4yODAwLDA\n") {
		t.Fatal("Android wire format changed")
	}
	// Android preserves insertion order, so valid input need not be sorted.
	data := strings.Replace(Encode(s), encodeText(encodeLayers(s.Mix.Layers)), encodeText("soft_rain,0.2800,0|pink,0.6200,1"), 1)
	if _, err := Decode(data); err != nil {
		t.Fatal(err)
	}
}
func TestRejectMalformedBackups(t *testing.T) {
	good := Encode(Default())
	mutations := []string{"not a backup", good + "mix.master=0.1\n", strings.Repeat("x", MaxBackup+1)}
	for _, v := range []string{"NaN", "Infinity", "-0.1", "1.1"} {
		mutations = append(mutations, strings.Replace(good, "mix.master=0.3800", "mix.master="+v, 1))
	}
	for _, v := range []string{"unknown,0.1000,1", "brown,0.1,1", "brown,0.1000,2", "brown,0.1000,1|brown,0.2000,1", "brown,NaN,1"} {
		mutations = append(mutations, strings.Replace(good, encodeText("brown,0.7200,1"), encodeText(v), 1))
	}
	mutations = append(mutations, strings.Replace(good, "presets.count=0", "presets.count=1001", 1), strings.Replace(good, "mix.activePreset="+encodeText("pure_brown"), "mix.activePreset="+encodeText("custom_missing"), 1), strings.Replace(good, "mix.name="+encodeText("Pure brown"), "mix.name=!!!", 1))
	for i, bad := range mutations {
		if _, err := Decode(bad); err == nil {
			t.Errorf("Accepted malformed backup %d", i)
		}
	}
}
func TestStorePreservesDataAndLocks(t *testing.T) {
	path := filepath.Join(t.TempDir(), "settings.noizey")
	store, s, err := OpenStore(path)
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	if _, _, err := OpenStore(path); err == nil {
		t.Fatal("Concurrent store acquired lock")
	}
	if err := store.Save(s); err != nil {
		t.Fatal(err)
	}
	next := s
	next.Apply(BuiltIns[1])
	if err := store.Restore(next, s); err != nil {
		t.Fatal(err)
	}
	old, err := ReadBackup(path + ".previous")
	if err != nil || old.Mix.Name != s.Mix.Name {
		t.Fatal("Previous settings not retained", err)
	}
	if err := WriteBackup(path, s, false); err == nil {
		t.Fatal("Export overwrote an existing file")
	}
	store.Close()
	if err := os.WriteFile(path, []byte("damaged"), 0600); err != nil {
		t.Fatal(err)
	}
	if _, _, err := OpenStore(path); err == nil {
		t.Fatal("Corrupt settings were ignored")
	}
	b, _ := os.ReadFile(path)
	if string(b) != "damaged" {
		t.Fatal("Corrupt settings were overwritten")
	}
}
func TestPresetIsolationAndSilentMix(t *testing.T) {
	s := Default()
	s.Mix.Layers["brown"] = Layer{.1, false}
	s.Apply(BuiltIns[0])
	if s.Mix.Layers["brown"].Volume != .72 {
		t.Fatal("Factory preset was mutated")
	}
	if err := s.SavePreset("Desk", time.Now()); err != nil {
		t.Fatal(err)
	}
	id := s.Mix.ActivePreset
	s.Mix.Layers["brown"] = Layer{.2, true}
	if s.Presets[0].Layers["brown"].Volume != .72 {
		t.Fatal("Saved preset aliases current mix")
	}
	s.DeletePreset(id)
	if s.Mix.ActivePreset != "" || s.Mix.Layers["brown"].Volume != .2 {
		t.Fatal("Delete changed live layers")
	}
	s.Mix.Layers = map[string]Layer{}
	if _, err := Decode(Encode(s)); err != nil {
		t.Fatal(err)
	}
	if err := s.SavePreset("empty", time.Now()); err == nil {
		t.Fatal("Saved an empty preset")
	}
	if Clamp(math.NaN()) != 0 || Clamp(math.Inf(1)) != 0 {
		t.Fatal("Nonfinite gain escaped normalization")
	}
}
func TestTimerPausesAndFades(t *testing.T) {
	now := time.Now()
	timer := Timer{}
	timer.Set(15*time.Minute, true, now)
	timer.Play(false, now.Add(10*time.Minute))
	timer.Tick(now.Add(time.Hour))
	if timer.Remaining != 5*time.Minute {
		t.Fatal("Timer advanced while paused")
	}
	timer.Play(true, now.Add(time.Hour))
	timer.Tick(now.Add(time.Hour + 4*time.Minute + 45*time.Second))
	if math.Abs(timer.Fade()-.5) > .00001 {
		t.Fatal("Expected half gain at 15 seconds")
	}
	if !timer.Tick(now.Add(time.Hour+5*time.Minute)) || timer.Remaining != 0 || timer.Running {
		t.Fatal("Timer did not finish")
	}
	if timer.Tick(now.Add(2 * time.Hour)) {
		t.Fatal("Timer finished twice")
	}
}
