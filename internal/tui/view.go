package tui

import (
	"fmt"
	"math"
	"strings"
	"unicode"

	"github.com/mattn/go-runewidth"
)

// All colors come from fxnk's indexed roles. The terminal owns the background.
type cell struct {
	text   string
	fg, bg int
	bold   bool
}
type canvas struct {
	width, height int
	rows          [][]cell
	palette       palette
}

func newCanvas(w, h int, p palette) *canvas {
	c := &canvas{width: w, height: h, palette: p, rows: make([][]cell, h)}
	for y := range c.rows {
		c.rows[y] = make([]cell, w)
		for x := range c.rows[y] {
			c.rows[y][x] = cell{" ", p.primary, -1, false}
		}
	}
	return c
}
func safe(text string) string {
	return strings.Map(func(r rune) rune {
		if unicode.IsControl(r) || unicode.In(r, unicode.Cf) {
			return -1
		}
		return r
	}, text)
}
func width(text string) int { return runewidth.StringWidth(safe(text)) }
func cut(text string, w int) string {
	if w <= 0 {
		return ""
	}
	return runewidth.Truncate(safe(text), w, "…")
}
func (c *canvas) text(x, y int, text string, color int, bold bool) {
	if y < 0 || y >= c.height || x >= c.width {
		return
	}
	for _, r := range safe(text) {
		w := runewidth.RuneWidth(r)
		if w == 0 {
			if x > 0 && x <= c.width {
				c.rows[y][x-1].text += string(r)
			}
			continue
		}
		if x+w > c.width {
			break
		}
		if x >= 0 {
			bg := c.rows[y][x].bg
			c.rows[y][x] = cell{string(r), color, bg, bold}
			for i := 1; i < w; i++ {
				c.rows[y][x+i] = cell{"", color, bg, bold}
			}
		}
		x += w
	}
}
func (c *canvas) fill(x, y, w int, color int) {
	if y < 0 || y >= c.height {
		return
	}
	for i := max(0, x); i < min(c.width, x+w); i++ {
		c.rows[y][i].bg = color
	}
}
func (c *canvas) rule(y int) {
	c.text(2, y, strings.Repeat("─", max(0, c.width-4)), c.palette.divider, false)
}
func (c *canvas) render() string {
	var b strings.Builder
	b.WriteString("\x1b[H")
	for y, row := range c.rows {
		b.WriteString("\x1b[0m\x1b[49m\x1b[2K")
		prev := cell{fg: -2, bg: -2}
		for _, v := range row {
			if v.text == "" {
				continue
			}
			if v.fg != prev.fg || v.bg != prev.bg || v.bold != prev.bold {
				b.WriteString("\x1b[0m")
				if v.bg < 0 {
					b.WriteString("\x1b[49m")
				} else {
					fmt.Fprintf(&b, "\x1b[48;5;%dm", v.bg)
				}
				b.WriteString(fg(v.fg))
				if v.bold {
					b.WriteString("\x1b[1m")
				}
				prev = v
			}
			b.WriteString(v.text)
		}
		if y < c.height-1 {
			b.WriteString("\r\n")
		}
	}
	b.WriteString("\x1b[0m")
	return b.String()
}
func bar(value float64, n int) string {
	n = max(1, n)
	filled := int(math.Round(value * float64(n)))
	filled = max(0, min(n, filled))
	return strings.Repeat("━", filled) + strings.Repeat("─", n-filled)
}
func (c *canvas) meter(x, y int, value float64, n, role int) {
	filled := max(0, min(n, int(math.Round(value*float64(n)))))
	c.text(x, y, strings.Repeat("━", filled), role, false)
	c.text(x+filled, y, strings.Repeat("─", n-filled), c.palette.divider, false)
}
func (a *App) View(w, h int, p palette, peak float64, output string) string {
	w = max(1, w)
	h = max(1, h)
	c := newCanvas(w, h, p)
	if w < 48 || h < 16 {
		c.text(1, 1, "noizey", p.primary, true)
		c.text(1, 3, "Resize to at least 48 × 16", p.secondary, false)
		c.text(1, 5, "Space play/pause · q quit", p.dim, false)
		return c.render()
	}
	c.text(2, 1, "noizey", p.primary, true)
	state := "○ Paused"
	if a.Playing {
		state = "● Playing"
	}
	c.text(w-width(state)-2, 1, state, p.accent, true)
	mixY, tabsY, top := 5, 8, 10
	if h < 28 {
		mixY, tabsY, top = 3, 5, 7
	} else {
		c.rule(3)
	}
	c.text(2, mixY, cut(a.State.Mix.Name, w-26), p.primary, true)
	master := fmt.Sprintf("master %3.0f%%", a.State.Mix.Master*100)
	c.text(w-width(master)-2, mixY, master, p.secondary, true)
	layerWord := "layers"
	if len(a.State.Mix.Layers) == 1 {
		layerWord = "layer"
	}
	summary := fmt.Sprintf("%d %s · offline synthesis", len(a.State.Mix.Layers), layerWord)
	if a.Timer.Remaining > 0 {
		sec := int(math.Ceil(a.Timer.Remaining.Seconds()))
		summary += fmt.Sprintf(" · sleep %02d:%02d", sec/60, sec%60)
		if !a.Playing {
			summary += " paused"
		}
		if a.Timer.Remaining.Seconds() <= 30 {
			summary += " · fading"
		}
	}
	c.text(2, mixY+1, cut(summary, w-4), p.dim, false)
	if w >= 72 && h >= 28 {
		c.meter(w-22, 7, a.State.Mix.Master, 20, p.accent)
	}
	tabs := []string{"1 Mix", "2 Sounds", "3 Presets", "4 Settings"}
	x := 2
	for i, t := range tabs {
		color := p.dim
		if i == a.page {
			color = p.primary
		}
		c.text(x, tabsY, t, color, i == a.page)
		x += width(t) + 3
	}
	c.rule(tabsY + 1)
	bottom := h - 5
	if a.editing != "" {
		a.drawEditor(c, top, bottom)
	} else {
		a.drawPage(c, top, bottom)
	}
	c.rule(h - 5)
	notice := a.notice
	role := p.secondary
	if a.failure {
		notice = "× " + notice
		role = p.accent
	}
	c.text(2, h-4, cut(notice, w-4), role, a.failure)
	controls := "Space play/pause · ←/→ level · [/] master · ? help · q quit"
	if a.editing != "" {
		controls = "Enter confirm · Esc cancel · Ctrl-U clear"
	} else if w < 76 {
		controls = "Space play · [/] master · ? help · q quit"
	}
	c.text(2, h-3, cut(controls, w-4), p.primary, false)
	foot := "48 kHz stereo · " + output
	if a.Playing {
		foot += " · output " + bar(math.Min(1, peak*3), 8)
	}
	c.text(2, h-2, cut(foot, w-4), p.status, false)
	return c.render()
}
func (a *App) drawPage(c *canvas, top, bottom int) {
	p := c.palette
	w := c.width
	count := a.count()
	a.clampSelection()
	title, hint := "LAYERS", "Enter mute · d remove · s save · t sleep"
	if a.page == 1 {
		title, hint = "SOUND LIBRARY", "Enter add / mute · / search · d remove"
		if a.filter != "" {
			title += " / " + a.filter
		}
	}
	if a.page == 2 {
		title, hint = "PRESETS", "Enter load · s save current mix · d delete custom"
	}
	if a.page == 3 {
		title, hint = "SETTINGS", "Enter change · e export · i restore"
	}
	c.text(2, top, cut(title, w-14), p.dim, true)
	if count > 0 {
		page := fmt.Sprintf("%d / %d", a.selected+1, count)
		c.text(w-width(page)-2, top, page, p.dim, false)
	}
	available := max(1, bottom-top-3)
	if a.selected < a.offset {
		a.offset = a.selected
	}
	if a.selected >= a.offset+available {
		a.offset = a.selected - available + 1
	}
	for row, index := 0, a.offset; row < available && index < count; row, index = row+1, index+1 {
		y := top + 2 + row
		selected := index == a.selected
		role := p.secondary
		if selected {
			role = p.primary
			c.fill(2, y, w-4, p.surface)
			c.text(2, y, "▎", 4, false)
		}
		switch a.page {
		case 0:
			sound := a.layers()[index]
			l := a.State.Mix.Layers[sound.ID]
			marker := "●"
			if !l.Enabled {
				marker = "○"
			}
			c.text(4, y, marker, role, false)
			c.text(7, y, cut(sound.Name, 21), role, selected)
			level := fmt.Sprintf("%3.0f%%", l.Volume*100)
			if !l.Enabled {
				level = "mute"
			}
			sliderWidth := max(4, min(24, w-42))
			c.meter(30, y, l.Volume, sliderWidth, role)
			c.text(31+sliderWidth, y, level, role, false)
			if w > 94 {
				c.text(63, y, cut(sound.Description, w-65), p.dim, false)
			}
		case 1:
			sound := a.sounds()[index]
			marker := "+"
			if l, ok := a.State.Mix.Layers[sound.ID]; ok {
				marker = "●"
				if !l.Enabled {
					marker = "○"
				}
			}
			c.text(4, y, marker, role, false)
			c.text(7, y, cut(sound.Name, 22), role, selected)
			if w >= 80 {
				c.text(31, y, cut(sound.Description, w-46), p.dim, false)
			}
			c.text(w-11, y, sound.Category, p.dim, false)
		case 2:
			preset := a.State.AllPresets()[index]
			marker := " "
			if preset.ID == a.State.Mix.ActivePreset {
				marker = "✓"
			}
			c.text(4, y, marker, role, false)
			nameWidth := w - 20
			if w >= 76 {
				nameWidth = 29
			}
			c.text(7, y, cut(preset.Name, nameWidth), role, selected)
			note := preset.Note
			if note == "" {
				note = fmt.Sprintf("%d sound layers", len(preset.Layers))
			}
			if w >= 76 {
				c.text(38, y, cut(note, w-52), p.dim, false)
			}
			kind := "factory"
			if strings.HasPrefix(preset.ID, "custom_") {
				kind = "custom"
			}
			c.text(w-10, y, kind, p.dim, false)
		case 3:
			names := []string{"Continue after output changes", "Export settings backup", "Restore settings backup"}
			c.text(5, y, names[index], role, selected)
			if index == 0 {
				value := "off"
				if a.State.StayRunning {
					value = "on"
				}
				c.text(w-7, y, value, role, true)
			}
		}
	}
	if count == 0 {
		c.text(4, top+2, "No sounds here yet", p.primary, true)
		text := "Press 2 to add a sound or 3 to choose a preset."
		if a.page == 1 {
			text = "Try another search. Esc clears the filter."
		}
		if top+4 < bottom-1 {
			c.text(4, top+4, cut(text, w-8), p.secondary, false)
		}
	}
	c.text(2, bottom-1, cut(hint, w-4), p.dim, false)
}
func (a *App) drawEditor(c *canvas, top, bottom int) {
	p := c.palette
	w := c.width
	title, detail := "", ""
	switch a.editing {
	case "save":
		title = "Save current mix"
		detail = "Name this preset. Your master level stays separate."
	case "timer":
		title = "Sleep timer"
		detail = "15 · 30 · 45 · 60 · 90 · 120 minutes · 0 off"
	case "search":
		title = "Search sounds"
		detail = fmt.Sprintf("%d matches · Enter to browse", len(a.sounds()))
	case "export":
		title = "Export settings backup"
		detail = "Android-compatible .noizey file · choose a new path"
	case "import":
		title = "Restore settings backup"
		detail = "Path to a .noizey file from the mobile app or this TUI"
	case "restore":
		title = "Replace current settings?"
		detail = fmt.Sprintf("%s · %d layers · %d custom presets", a.pending.Mix.Name, len(a.pending.Mix.Layers), len(a.pending.Presets))
	case "delete":
		title = "Delete this custom preset?"
		detail = string(a.text)
	case "help":
		c.text(2, top, "Keyboard controls · ↑/↓ scroll", p.primary, true)
		lines := []string{"Space  play / pause     x  stop and clear timer", "1–4 / Tab  views      ↑/↓ or j/k  select", "←/→ or h/l  layer level     [/] or -/+  master", "Enter / m  add or mute     d  remove / delete", "s  save preset     t  sleep timer     /  search", "e  export backup   i  restore backup   q  quit", "Names & paths: arrows, Home/End, Ctrl-U/W, paste", "Playback continues while this terminal process runs.", "Timers pause with playback; final 30 seconds fade.", "Output changes are checked every two seconds."}
		for i, line := range lines[a.helpOffset:] {
			if top+2+i >= bottom {
				break
			}
			c.text(2, top+2+i, cut(line, w-4), p.secondary, false)
		}
		return
	}
	detailY, inputY := top+2, top+4
	if bottom-top < 7 {
		detailY, inputY = top+1, top+2
	}
	c.text(2, top, title, p.primary, true)
	c.text(2, detailY, cut(detail, w-4), p.secondary, false)
	if a.editing == "restore" || a.editing == "delete" {
		hint := "Enter deletes · Esc keeps it"
		if a.editing == "restore" {
			hint = "Enter restores · existing settings saved as .previous"
		}
		c.text(2, inputY, cut(hint, w-4), p.accent, true)
		return
	}
	// Horizontally scroll input so the insertion point stays visible.
	visible := a.text
	cursor := a.cursor
	for width(string(visible[:cursor])) > w-9 {
		visible = visible[1:]
		cursor--
	}
	c.text(2, inputY, "❯", p.primary, true)
	c.text(4, inputY, string(visible[:cursor]), p.primary, true)
	cursorX := 4 + width(string(visible[:cursor]))
	c.text(cursorX, inputY, "▎", 4, false)
	c.text(cursorX+1, inputY, cut(string(visible[cursor:]), w-cursorX-3), p.primary, true)
	if a.editing == "timer" && top+6 < bottom {
		c.text(2, top+6, "Pauses with playback · fades the final 30 seconds", p.dim, false)
	}
}
