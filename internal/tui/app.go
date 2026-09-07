package tui

import (
	"errors"
	"fmt"
	"math"
	"os"
	"path/filepath"
	"strings"
	"time"
	"unicode"

	"noizey/internal/model"
)

// Player keeps UI tests independent of a physical audio device.
type Player interface {
	Update(model.Mix, float64, bool)
	Start() error
	Pause()
	RouteChanged() (bool, error)
}
type App struct {
	State                  model.Snapshot
	store                  *model.Store
	player                 Player
	Playing                bool
	Timer                  model.Timer
	page, selected, offset int
	filter                 string
	editing                string
	text                   []rune
	cursor                 int
	notice                 string
	failure                bool
	pending                *model.Snapshot
	pendingID              string
	helpOffset             int
	now                    time.Time
	routeCheck             time.Time
}

func New(s model.Snapshot, store *model.Store, player Player) *App {
	return &App{State: s, store: store, player: player, notice: "Space to play · 2 to explore sounds", now: time.Now()}
}
func (a *App) Start() { a.setPlaying(true) }
func (a *App) Err() error {
	if a.failure {
		return errors.New(a.notice)
	}
	return nil
}
func (a *App) setPlaying(play bool) {
	if play == a.Playing {
		return
	}
	if play {
		a.player.Update(a.State.Mix, a.Timer.Fade(), true)
		if err := a.player.Start(); err != nil {
			a.message(err)
			return
		}
	} else {
		a.player.Pause()
	}
	a.Playing = play
	a.Timer.Play(play, time.Now())
	a.update()
	if play {
		a.note("Playing · mixes with other audio apps")
	} else {
		a.note("Paused")
	}
}
func (a *App) update()           { a.player.Update(a.State.Mix, a.Timer.Fade(), a.Playing) }
func (a *App) message(err error) { a.notice = err.Error(); a.failure = true }
func (a *App) note(text string)  { a.notice = text; a.failure = false }
func (a *App) save() {
	a.update()
	if a.store != nil {
		if e := a.store.Save(a.State); e != nil {
			a.message(fmt.Errorf("Could not save settings: %w", e))
		}
	}
}
func (a *App) Tick(now time.Time) {
	a.now = now
	if a.Timer.Tick(now) {
		a.setPlaying(false)
		a.note("✓ Sleep timer finished")
	}
	a.update()
	if a.Playing && now.Sub(a.routeCheck) > 2*time.Second {
		a.routeCheck = now
		changed, err := a.player.RouteChanged()
		if err != nil {
			a.setPlaying(false)
			a.message(err)
		} else if changed {
			a.setPlaying(false)
			if a.State.StayRunning {
				a.setPlaying(true)
				if a.Playing {
					a.note("Output changed · continued on the system default")
				}
			} else {
				a.note("Output changed · paused · Space to resume")
			}
		}
	}
}
func (a *App) layers() []model.Sound {
	var out []model.Sound
	for _, s := range model.Sounds {
		if _, ok := a.State.Mix.Layers[s.ID]; ok {
			out = append(out, s)
		}
	}
	return out
}
func (a *App) sounds() []model.Sound {
	var out []model.Sound
	for _, s := range model.Sounds {
		if strings.Contains(strings.ToLower(s.Name+" "+s.Category+" "+s.Description), strings.ToLower(a.filter)) {
			out = append(out, s)
		}
	}
	return out
}
func (a *App) count() int {
	switch a.page {
	case 0:
		return len(a.layers())
	case 1:
		return len(a.sounds())
	case 2:
		return len(a.State.AllPresets())
	default:
		return 3
	}
}
func (a *App) clampSelection() {
	a.selected = max(0, min(a.selected, a.count()-1))
	a.offset = min(a.offset, a.selected)
}
func (a *App) prompt(kind, text string) {
	a.helpOffset = 0
	a.editing = kind
	a.text = []rune(text)
	a.cursor = len(a.text)
	a.failure = false
}
func (a *App) editLayer(delta float64, toggle, remove bool) {
	var list []model.Sound
	if a.page == 0 {
		list = a.layers()
	} else if a.page == 1 {
		list = a.sounds()
	} else {
		return
	}
	if len(list) == 0 {
		return
	}
	a.clampSelection()
	s := list[a.selected]
	l, exists := a.State.Mix.Layers[s.ID]
	if remove {
		if !exists {
			return
		}
		delete(a.State.Mix.Layers, s.ID)
		a.note("Removed " + s.Name)
	} else {
		if !exists {
			l = model.Layer{Volume: s.Volume, Enabled: true}
			a.note("Added " + s.Name)
		} else if toggle {
			l.Enabled = !l.Enabled
			if l.Enabled {
				a.note("Enabled " + s.Name)
			} else {
				a.note("Muted " + s.Name)
			}
		}
		l.Volume = math.Round(model.Clamp(l.Volume+delta)*100) / 100
		if delta != 0 {
			a.note(fmt.Sprintf("%s · %.0f%%", s.Name, l.Volume*100))
		}
		a.State.Mix.Layers[s.ID] = l
	}
	a.State.Edited()
	a.clampSelection()
	a.save()
}
func (a *App) Handle(e event) bool {
	if e.key == "quit" {
		return true
	}
	if a.editing != "" {
		a.handleEdit(e)
		return false
	}
	if e.pasted {
		a.note("Paste into a name, search, or backup path field")
		return false
	}
	if e.key == "escape" {
		if a.filter != "" {
			a.filter = ""
			a.selected = 0
			a.offset = 0
		}
	}
	key := e.key
	if key == "" {
		key = e.text
	}
	switch key {
	case "q":
		return true
	case " ":
		a.setPlaying(!a.Playing)
		if !a.failure {
			if a.Playing {
				a.note("Playing · mixes with other audio apps")
			} else {
				a.note("Paused")
			}
		}
	case "x":
		a.setPlaying(false)
		a.Timer.Set(0, false, a.now)
		a.note("Stopped · sleep timer cleared")
	case "1", "2", "3", "4":
		a.page = int(key[0] - '1')
		a.selected = 0
		a.offset = 0
	case "tab":
		a.page = (a.page + 1) % 4
		a.selected = 0
		a.offset = 0
	case "backtab":
		a.page = (a.page + 3) % 4
		a.selected = 0
		a.offset = 0
	case "down", "j":
		a.selected++
	case "up", "k":
		a.selected--
	case "pagedown":
		a.selected += 10
	case "pageup":
		a.selected -= 10
	case "home", "g":
		a.selected = 0
	case "end", "G":
		a.selected = a.count() - 1
	case "left", "h":
		a.editLayer(-.05, false, false)
	case "right", "l":
		a.editLayer(.05, false, false)
	case "m":
		a.editLayer(0, true, false)
	case "delete", "d":
		if a.page == 2 {
			ps := a.State.AllPresets()
			if len(ps) > 0 {
				p := ps[a.selected]
				if strings.HasPrefix(p.ID, "custom_") {
					a.pendingID = p.ID
					a.prompt("delete", p.Name)
				} else {
					a.note("Factory presets are permanent")
				}
			}
		} else {
			a.editLayer(0, false, true)
		}
	case "enter":
		a.activate()
	case "[", "-":
		a.State.Mix.Master = math.Round(model.Clamp(a.State.Mix.Master-.02)*100) / 100
		a.save()
	case "]", "+", "=":
		a.State.Mix.Master = math.Round(model.Clamp(a.State.Mix.Master+.02)*100) / 100
		a.save()
	case "s":
		a.prompt("save", "")
	case "t":
		a.prompt("timer", "")
	case "/":
		a.page = 1
		a.selected = 0
		a.offset = 0
		a.prompt("search", a.filter)
	case "e":
		a.prompt("export", defaultBackup())
	case "i":
		a.prompt("import", "")
	case "?":
		a.prompt("help", "")
	}
	a.clampSelection()
	return false
}
func (a *App) activate() {
	switch a.page {
	case 0, 1:
		a.editLayer(0, true, false)
	case 2:
		p := a.State.AllPresets()[a.selected]
		a.State.Apply(p)
		a.note("Loaded " + p.Name)
		a.save()
	case 3:
		switch a.selected {
		case 0:
			a.State.StayRunning = !a.State.StayRunning
			a.save()
		case 1:
			a.prompt("export", defaultBackup())
		case 2:
			a.prompt("import", "")
		}
	}
}
func defaultBackup() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, "noizey-"+time.Now().Format("20060102-150405")+".noizey")
}
func expandPath(path string) string {
	path = strings.TrimSpace(path)
	if strings.HasPrefix(path, "~/") {
		home, _ := os.UserHomeDir()
		return filepath.Join(home, path[2:])
	}
	return path
}
func (a *App) handleEdit(e event) {
	if e.key == "escape" {
		a.editing = ""
		a.pending = nil
		a.failure = false
		return
	}
	if a.editing == "help" {
		if e.key == "down" || e.text == "j" {
			a.helpOffset = min(9, a.helpOffset+1)
		}
		if e.key == "up" || e.text == "k" {
			a.helpOffset = max(0, a.helpOffset-1)
		}
		if e.key == "enter" || e.text == "?" {
			a.editing = ""
		}
		return
	}
	if a.editing == "restore" || a.editing == "delete" {
		if e.key == "enter" {
			if a.editing == "restore" {
				if a.pending == nil {
					return
				}
				if a.store != nil {
					if err := a.store.Restore(*a.pending, a.State); err != nil {
						a.message(err)
						return
					}
				}
				a.State = *a.pending
				a.pending = nil
				a.Timer.Set(0, a.Playing, a.now)
				a.update()
				a.note("✓ Settings restored · previous settings kept alongside the state file")
			} else {
				a.State.DeletePreset(a.pendingID)
				a.note("Deleted preset · current layers kept")
				a.save()
			}
			a.editing = ""
			a.clampSelection()
		}
		return
	}
	switch e.key {
	case "enter":
		a.submit()
		return
	case "left":
		a.cursor = max(0, a.cursor-1)
	case "right":
		a.cursor = min(len(a.text), a.cursor+1)
	case "home":
		a.cursor = 0
	case "end":
		a.cursor = len(a.text)
	case "clear":
		a.text = nil
		a.cursor = 0
	case "backspace":
		if a.cursor > 0 {
			a.text = append(a.text[:a.cursor-1], a.text[a.cursor:]...)
			a.cursor--
		}
	case "delete":
		if a.cursor < len(a.text) {
			a.text = append(a.text[:a.cursor], a.text[a.cursor+1:]...)
		}
	case "wordback":
		start := a.cursor
		for start > 0 && unicode.IsSpace(a.text[start-1]) {
			start--
		}
		for start > 0 && !unicode.IsSpace(a.text[start-1]) {
			start--
		}
		a.text = append(a.text[:start], a.text[a.cursor:]...)
		a.cursor = start
	default:
		if e.text != "" {
			clean := []rune(safe(e.text))
			if len(a.text)+len(clean) <= 4096 {
				tail := append([]rune{}, a.text[a.cursor:]...)
				a.text = append(a.text[:a.cursor], clean...)
				a.text = append(a.text, tail...)
				a.cursor += len(clean)
			}
		}
	}
	if a.editing == "search" {
		a.filter = string(a.text)
		a.selected = 0
		a.offset = 0
	}
}
func (a *App) submit() {
	text := string(a.text)
	switch a.editing {
	case "save":
		if err := a.State.SavePreset(text, time.Now()); err != nil {
			a.message(err)
			return
		}
		a.note("✓ Saved " + a.State.Mix.Name)
		a.save()
	case "timer":
		var minutes int
		if _, e := fmt.Sscanf(text, "%d", &minutes); e != nil || !validMinutes(text, minutes) {
			a.message(fmt.Errorf("Choose 15, 30, 45, 60, 90, 120 minutes, or 0 for off"))
			return
		}
		a.Timer.Set(time.Duration(minutes)*time.Minute, a.Playing, time.Now())
		a.note("Sleep timer off")
		if minutes > 0 {
			a.note(fmt.Sprintf("Sleep timer · %d minutes · final 30 seconds fade", minutes))
		}
		a.update()
	case "search":
	case "export":
		if e := model.WriteBackup(expandPath(text), a.State, false); e != nil {
			a.message(e)
			return
		}
		a.note("✓ Backup saved to " + text)
	case "import":
		s, e := model.ReadBackup(expandPath(text))
		if e != nil {
			a.message(e)
			return
		}
		a.pending = &s
		a.prompt("restore", s.Mix.Name)
		return
	}
	a.editing = ""
}
func validMinutes(text string, n int) bool {
	if strings.TrimSpace(text) != fmt.Sprint(n) {
		return false
	}
	switch n {
	case 0, 15, 30, 45, 60, 90, 120:
		return true
	}
	return false
}
