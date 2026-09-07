package tui

import (
	"fmt"
	"math"
	"strconv"
	"strings"
	"time"
)

const osc11 = "\x1b]11;?\x1b\\"
const da1 = "\x1b[c"

type palette struct{ primary, accent, secondary, dim, divider, surface, status int }

var dark = palette{255, 252, 250, 245, 240, 236, 245}
var light = palette{235, 238, 241, 247, 250, 254, 241}

type theme struct {
	light, explicit, ready bool
	phase                  string
	deadline               time.Time
	generation             int
	fence                  []int
	fallback               bool
}

func newTheme(explicit, colorfgbg string, now time.Time) (*theme, string) {
	t := &theme{}
	switch strings.ToLower(explicit) {
	case "light":
		t.light = true
		t.explicit = true
		t.ready = true
	case "dark":
		t.explicit = true
		t.ready = true
	}
	if t.explicit {
		return t, ""
	}
	parts := strings.Split(colorfgbg, ";")
	if v, e := strconv.Atoi(parts[len(parts)-1]); e == nil {
		t.light = v >= 7 && v <= 15
	}
	t.phase = "startup"
	t.deadline = now.Add(200 * time.Millisecond)
	return t, osc11
}
func (t *theme) palette() palette {
	if t.light {
		return light
	}
	return dark
}
func (t *theme) tick(now time.Time) {
	if t.phase != "" && !now.Before(t.deadline) {
		if t.phase != "startup" {
			t.light = t.fallback
		}
		t.ready = true
		t.phase = ""
	}
}
func (t *theme) handle(e event, now time.Time) string {
	t.tick(now)
	if t.explicit {
		return ""
	}
	switch e.key {
	case "theme":
		if e.text != "?997;1n" && e.text != "?997;2n" {
			return ""
		}
		t.generation++
		t.fallback = e.text == "?997;2n"
		t.phase = "fence"
		t.deadline = now.Add(200 * time.Millisecond)
		t.fence = append(t.fence, t.generation)
		return da1
	case "da1":
		if len(t.fence) == 0 {
			return ""
		}
		gen := t.fence[0]
		t.fence = t.fence[1:]
		if gen == t.generation && t.phase == "fence" {
			t.phase = "sample"
			t.deadline = now.Add(200 * time.Millisecond)
			return osc11
		}
	case "osc":
		if t.phase != "startup" && t.phase != "sample" {
			return ""
		}
		if mode, ok := backgroundMode(e.text); ok {
			t.light = mode
			t.ready = true
			t.phase = ""
		}
	}
	return ""
}
func backgroundMode(s string) (bool, bool) {
	if !strings.HasPrefix(s, "11;rgb:") {
		return false, false
	}
	parts := strings.Split(strings.TrimPrefix(s, "11;rgb:"), "/")
	if len(parts) != 3 {
		return false, false
	}
	var rgb [3]float64
	for i, p := range parts {
		if len(p) < 1 || len(p) > 4 {
			return false, false
		}
		v, e := strconv.ParseUint(p, 16, 16)
		if e != nil {
			return false, false
		}
		x := float64(v) / float64((uint64(1)<<uint(len(p)*4))-1)
		if x <= .04045 {
			x /= 12.92
		} else {
			x = math.Pow((x+.055)/1.055, 2.4)
		}
		rgb[i] = x
	}
	return .2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2] > .5, true
}
func fg(index int) string { return fmt.Sprintf("\x1b[38;5;%dm", index) }
