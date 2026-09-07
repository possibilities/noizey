package tui

import (
	"strings"
	"time"
	"unicode"
	"unicode/utf8"
)

type event struct {
	key, text string
	pasted    bool
}
type input struct {
	pending string
	since   time.Time
	pasting bool
	paste   strings.Builder
}

func (p *input) feed(data string, now time.Time) []event {
	p.pending += data
	p.since = now
	var out []event
	for p.pending != "" {
		s := p.pending
		if p.pasting {
			end := strings.Index(s, "\x1b[201~")
			if end < 0 {
				if len(s) > 6 {
					if p.paste.Len() < 16384 {
						p.paste.WriteString(s[:len(s)-6])
					}
					p.pending = s[len(s)-6:]
				}
				break
			}
			if p.paste.Len() < 16384 {
				p.paste.WriteString(s[:end])
			}
			out = append(out, event{text: p.paste.String(), pasted: true})
			p.paste.Reset()
			p.pasting = false
			p.pending = s[end+6:]
			continue
		}
		if strings.HasPrefix(s, "\x1b[200~") {
			p.pasting = true
			p.pending = s[6:]
			continue
		}
		if s[0] == 27 {
			if len(s) < 2 {
				break
			}
			if s[1] == ']' {
				end, n := strings.IndexByte(s, 7), 1
				st := strings.Index(s, "\x1b\\")
				if st >= 0 && (end < 0 || st < end) {
					end, n = st, 2
				}
				if end < 0 {
					if len(s) > 4096 {
						p.pending = ""
					}
					break
				}
				out = append(out, event{key: "osc", text: s[2:end]})
				p.pending = s[end+n:]
				continue
			}
			if s[1] == '[' || s[1] == 'O' {
				end := 2
				for end < len(s) && (s[end] < 0x40 || s[end] > 0x7e) {
					end++
				}
				if end == len(s) {
					if len(s) > 4096 {
						p.pending = ""
					}
					break
				}
				seq := s[2 : end+1]
				p.pending = s[end+1:]
				keys := map[string]string{"A": "up", "B": "down", "C": "right", "D": "left", "H": "home", "F": "end", "1~": "home", "4~": "end", "7~": "home", "8~": "end", "3~": "delete", "5~": "pageup", "6~": "pagedown", "Z": "backtab"}
				if key := keys[seq]; key != "" {
					out = append(out, event{key: key})
				} else if strings.HasPrefix(seq, "?997;") && strings.HasSuffix(seq, "n") {
					out = append(out, event{key: "theme", text: seq})
				} else if strings.HasPrefix(seq, "?") && strings.HasSuffix(seq, "c") {
					out = append(out, event{key: "da1"})
				}
				continue
			}
			// Consume Alt-key input as a unit, without triggering an ordinary command.
			if !utf8.FullRuneInString(s[1:]) {
				break
			}
			_, n := utf8.DecodeRuneInString(s[1:])
			p.pending = s[n+1:]
			continue
		}
		if !utf8.FullRuneInString(s) {
			break
		}
		r, n := utf8.DecodeRuneInString(s)
		p.pending = s[n:]
		keys := map[rune]string{3: "quit", 13: "enter", 10: "enter", 9: "tab", 127: "backspace", 8: "backspace", 1: "home", 5: "end", 21: "clear", 23: "wordback"}
		if key := keys[r]; key != "" {
			out = append(out, event{key: key})
		} else if !unicode.IsControl(r) {
			out = append(out, event{text: string(r)})
		}
	}
	return out
}
func (p *input) timeout(now time.Time) []event {
	if p.pending == "\x1b" && !p.pasting && now.Sub(p.since) > 40*time.Millisecond {
		p.pending = ""
		return []event{{key: "escape"}}
	}
	return nil
}
