package tui

import (
	"errors"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
	"time"

	"noizey/internal/model"
)

type fakePlayer struct {
	playing, changed bool
	err              error
	fade             float64
}

func (p *fakePlayer) Update(_ model.Mix, fade float64, _ bool) { p.fade = fade }
func (p *fakePlayer) Start() error {
	if p.err == nil {
		p.playing = true
	}
	return p.err
}
func (p *fakePlayer) Pause()                      { p.playing = false }
func (p *fakePlayer) RouteChanged() (bool, error) { return p.changed, p.err }
func key(a *App, k string) {
	if len(k) == 1 {
		a.Handle(event{text: k})
	} else {
		a.Handle(event{key: k})
	}
}

func TestMixerAndPresetInteraction(t *testing.T) {
	p := &fakePlayer{}
	a := New(model.Default(), nil, p)
	key(a, " ")
	if !a.Playing {
		t.Fatal("Space did not play")
	}
	key(a, "2")
	key(a, "down")
	key(a, "enter")
	key(a, "left")
	if l := a.State.Mix.Layers["pink"]; !l.Enabled || l.Volume != .5 {
		t.Fatal("Could not add and adjust pink", l)
	}
	key(a, "m")
	if a.State.Mix.Layers["pink"].Enabled {
		t.Fatal("Mute failed")
	}
	key(a, "s")
	a.Handle(event{text: "Desk q x", pasted: true})
	if !a.Playing {
		t.Fatal("Text input invoked transport commands")
	}
	key(a, "enter")
	if len(a.State.Presets) != 1 || a.State.Mix.Name != "Desk q x" {
		t.Fatal("Preset not saved")
	}
	key(a, "3")
	key(a, "end")
	key(a, "d")
	key(a, "escape")
	if len(a.State.Presets) != 1 {
		t.Fatal("Escape deleted a preset")
	}
	key(a, "d")
	key(a, "enter")
	if len(a.State.Presets) != 0 || len(a.State.Mix.Layers) != 2 {
		t.Fatal("Delete should retain mix")
	}
	key(a, "x")
	if a.Playing || a.Timer.Remaining != 0 {
		t.Fatal("Stop failed")
	}
}
func TestSearchEmptyAndPasteSafety(t *testing.T) {
	a := New(model.Default(), nil, &fakePlayer{})
	a.Handle(event{text: "qd", pasted: true})
	if len(a.layers()) != 1 {
		t.Fatal("Paste executed a hotkey")
	}
	key(a, "/")
	a.Handle(event{text: "nonexistent"})
	key(a, "enter")
	key(a, "enter")
	key(a, "d")
	key(a, "right")
	if a.count() != 0 {
		t.Fatal("Search should be empty")
	}
	key(a, "escape")
	if a.count() != 19 {
		t.Fatal("Escape did not clear search")
	}
}
func TestTimerAndRouteBehavior(t *testing.T) {
	p := &fakePlayer{}
	a := New(model.Default(), nil, p)
	key(a, "t")
	a.Handle(event{text: "15"})
	key(a, "enter")
	key(a, " ")
	now := time.Now()
	a.Timer.Set(20*time.Second, true, now)
	a.routeCheck = now
	a.Tick(now.Add(5 * time.Second))
	if p.fade != .5 {
		t.Fatalf("Final fade not applied: %v", p.fade)
	}
	key(a, " ")
	remaining := a.Timer.Remaining
	a.Tick(now.Add(time.Hour))
	if a.Timer.Remaining != remaining {
		t.Fatal("Paused timer advanced")
	}
	key(a, " ")
	a.Timer.Set(0, a.Playing, now)
	p.changed = true
	a.Tick(now.Add(2 * time.Hour))
	if a.Playing {
		t.Fatal("Output change should pause")
	}
	a.Timer.Set(0, false, now)
	a.State.StayRunning = true
	key(a, " ")
	a.Tick(now.Add(3 * time.Hour))
	if !a.Playing {
		t.Fatal("Continue preference ignored")
	}
	p.err = errors.New("device failed")
	a.Tick(now.Add(4 * time.Hour))
	if a.Playing || !a.failure {
		t.Fatal("Device failure hidden")
	}
	p.err = nil
	p.changed = false
	a.Start()
	if !a.Playing || a.Err() != nil {
		t.Fatal("Successful reconnect kept a stale failure")
	}
	a.Timer.Set(time.Second, true, now)
	a.Tick(now.Add(time.Second))
	if a.Playing || a.Timer.Remaining != 0 || !strings.Contains(a.notice, "Sleep timer finished") {
		t.Fatal("Sleep timer failed to stop playback")
	}
}
func TestImportValidatesBeforeReplacingAndKeepsPrevious(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "state")
	store, s, err := model.OpenStore(path)
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	a := New(s, store, &fakePlayer{})
	backup := filepath.Join(dir, "backup.noizey")
	other := model.Default()
	other.Apply(model.BuiltIns[1])
	if e := model.WriteBackup(backup, other, false); e != nil {
		t.Fatal(e)
	}
	a.prompt("import", backup)
	key(a, "enter")
	if a.editing != "restore" || a.State.Mix.Name != "Pure brown" {
		t.Fatal("Import skipped preview")
	}
	key(a, "enter")
	saved, e := model.ReadBackup(path)
	if e != nil || saved.Mix.Name != "Deep sleep" {
		t.Fatal("Restore not saved", e)
	}
	previous, e := model.ReadBackup(path + ".previous")
	if e != nil || previous.Mix.Name != "Pure brown" {
		t.Fatal("Previous state missing")
	}
	bad := filepath.Join(dir, "bad")
	_ = os.WriteFile(bad, []byte("bad"), 0600)
	a.prompt("import", bad)
	key(a, "enter")
	if !a.failure || a.pending != nil || a.State.Mix.Name != "Deep sleep" {
		t.Fatal("Bad import changed current state")
	}
}
func TestInputFragmentationAndProtocolOwnership(t *testing.T) {
	var p input
	now := time.Now()
	raw := "\x1b[A\x1b]11;rgb:ffff/ffff/ffff\x1b\\\x1b[?997;2n\x1b[?1;2c\x1b[200~q\n夜\x1b[201~"
	var got []event
	for _, b := range []byte(raw) {
		got = append(got, p.feed(string([]byte{b}), now)...)
	}
	want := []string{"up", "osc", "theme", "da1", ""}
	if len(got) != len(want) {
		t.Fatalf("Wrong events: %+v", got)
	}
	for i, k := range want {
		if got[i].key != k {
			t.Fatalf("Unexpected event: %+v", got[i])
		}
	}
	if !got[4].pasted || got[4].text != "q\n夜" {
		t.Fatal("Paste was split into hotkeys")
	}
	p.feed("\x1b", now)
	if out := p.timeout(now.Add(50 * time.Millisecond)); len(out) != 1 || out[0].key != "escape" {
		t.Fatal("Standalone escape missing")
	}
}
func TestThemeStartupAndLiveFencing(t *testing.T) {
	now := time.Now()
	th, q := newTheme("", "15;0", now)
	if q != osc11 || th.ready {
		t.Fatal("Startup must wait for one background query")
	}
	th.tick(now.Add(201 * time.Millisecond))
	th.handle(event{key: "osc", text: "11;rgb:ffff/ffff/ffff"}, now.Add(time.Second))
	if th.light {
		t.Fatal("Late startup response recolored frame")
	}
	now = now.Add(2 * time.Second)
	if q = th.handle(event{key: "theme", text: "?997;2n"}, now); q != da1 {
		t.Fatal("No live fence")
	}
	th.handle(event{key: "osc", text: "11;rgb:0000/0000/0000"}, now)
	if q = th.handle(event{key: "da1"}, now); q != osc11 {
		t.Fatal("Fence did not open background sample")
	}
	th.handle(event{key: "theme", text: "?997;1n"}, now)
	th.handle(event{key: "osc", text: "11;rgb:ffff/ffff/ffff"}, now)
	if th.light {
		t.Fatal("Stale in-flight sample applied")
	}
	th.handle(event{key: "da1"}, now)
	th.handle(event{key: "osc", text: "11;rgb:ffff/ffff/ffff"}, now)
	if th.palette() != light {
		t.Fatal("Complete light palette not applied")
	}
	explicit, q := newTheme("LiGhT", "0;0", now)
	if q != "" || !explicit.ready {
		t.Fatal("Explicit theme queried background")
	}
	explicit.handle(event{key: "theme", text: "?997;1n"}, now)
	if !explicit.light {
		t.Fatal("Explicit theme changed live")
	}
	for _, bad := range []string{"11;rgb:zz/ff/ff", "10;rgb:ff/ff/ff", "11;rgb:/ff/ff"} {
		if _, ok := backgroundMode(bad); ok {
			t.Fatal("Invalid OSC accepted")
		}
	}
}

var ansi = regexp.MustCompile(`\x1b\[[0-?]*[ -/]*[@-~]`)

func plain(s string) string { return ansi.ReplaceAllString(s, "") }
func TestLayoutWidthsAndCompactInput(t *testing.T) {
	a := New(model.Default(), nil, &fakePlayer{})
	a.State.Mix.Name = "Night 夜\x1b]52;c;danger\x07"
	for _, size := range [][2]int{{112, 34}, {80, 24}, {48, 16}, {32, 10}} {
		for _, p := range []palette{dark, light} {
			screen := plain(a.View(size[0], size[1], p, .1, "System default"))
			lines := strings.Split(screen, "\r\n")
			if len(lines) != size[1] {
				t.Fatal("Incorrect height")
			}
			for _, line := range lines {
				if width(line) != size[0] {
					t.Fatalf("Incorrect width %d for %dx%d: %q", width(line), size[0], size[1], line)
				}
			}
			if strings.Contains(screen, "\x1b") {
				t.Fatal("Imported name injected terminal escapes")
			}
		}
	}
	a.prompt("save", "Desk")
	screen := plain(a.View(48, 16, dark, 0, "System default"))
	if !strings.Contains(screen, "❯ Desk") {
		t.Fatal("Input lost in compact layout")
	}
	a.prompt("save", "夜Desk")
	a.cursor = 0
	screen = plain(a.View(48, 16, dark, 0, "System default"))
	if !strings.Contains(screen, "❯ ▎夜Desk") {
		t.Fatal("Input caret overwrote a wide character")
	}
	for _, line := range strings.Split(screen, "\r\n") {
		if width(line) != 48 {
			t.Fatal("Wide input changed terminal row width")
		}
	}
	a.page = 1
	a.editing = ""
	a.selected = 18
	screen = plain(a.View(48, 16, dark, 0, "System default"))
	if !strings.Contains(screen, "Fireplace") {
		t.Fatal("Selected row not scrolled into view")
	}
}
