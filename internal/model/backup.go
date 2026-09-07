package model

import (
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"unicode/utf8"

	"golang.org/x/sys/unix"
)

const MaxBackup = 1_000_000
const backupHeader = "NOIZEY_SETTINGS_V1"

var presetID = regexp.MustCompile(`^custom_[A-Za-z0-9._-]{0,121}$`)

func encodeText(s string) string { return base64.RawURLEncoding.EncodeToString([]byte(s)) }
func encodeLayers(layers map[string]Layer) string {
	keys := make([]string, 0, len(layers))
	for k := range layers {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	out := make([]string, 0, len(keys))
	for _, k := range keys {
		v := layers[k]
		enabled := 0
		if v.Enabled {
			enabled = 1
		}
		out = append(out, fmt.Sprintf("%s,%.4f,%d", k, v.Volume, enabled))
	}
	return strings.Join(out, "|")
}
func Encode(s Snapshot) string {
	var b strings.Builder
	fmt.Fprintln(&b, backupHeader)
	field := func(k, v string) { fmt.Fprintf(&b, "%s=%s\n", k, v) }
	field("mix.name", encodeText(s.Mix.Name))
	field("mix.master", fmt.Sprintf("%.4f", s.Mix.Master))
	field("mix.layers", encodeText(encodeLayers(s.Mix.Layers)))
	field("mix.activePreset", encodeText(s.Mix.ActivePreset))
	field("playback.stayRunningWhenHeadphonesUnplugged", strconv.FormatBool(s.StayRunning))
	field("presets.count", strconv.Itoa(len(s.Presets)))
	for i, p := range s.Presets {
		prefix := fmt.Sprintf("preset.%d.", i)
		field(prefix+"id", encodeText(p.ID))
		field(prefix+"name", encodeText(p.Name))
		field(prefix+"layers", encodeText(encodeLayers(p.Layers)))
		field(prefix+"createdAt", strconv.FormatInt(p.CreatedAt, 10))
	}
	return b.String()
}
func Decode(data string) (Snapshot, error) {
	var s Snapshot
	bad := func(reason string) (Snapshot, error) { return Snapshot{}, errors.New(reason) }
	if len(data) > MaxBackup {
		return bad("Backup is too large")
	}
	lines := strings.Split(strings.ReplaceAll(data, "\r\n", "\n"), "\n")
	if lines[0] != backupHeader {
		return bad("Not a Noizey settings backup")
	}
	fields := map[string]string{}
	for _, line := range lines[1:] {
		if strings.TrimSpace(line) == "" {
			continue
		}
		k, v, ok := strings.Cut(line, "=")
		if !ok || k == "" {
			return bad("Malformed backup field")
		}
		if _, ok := fields[k]; ok {
			return bad("Duplicate backup field: " + k)
		}
		fields[k] = v
	}
	var fieldErr error
	get := func(k string) string {
		v, ok := fields[k]
		if !ok {
			fieldErr = fmt.Errorf("Missing backup field: %s", k)
		}
		return v
	}
	text := func(k string) string {
		encoded := get(k)
		v, e := base64.RawURLEncoding.DecodeString(encoded)
		if e != nil {
			v, e = base64.URLEncoding.DecodeString(encoded)
		}
		if e != nil || !utf8.Valid(v) {
			fieldErr = fmt.Errorf("Invalid text: %s", k)
		}
		return string(v)
	}
	count, e := strconv.Atoi(get("presets.count"))
	if e != nil || count < 0 || count > 1000 {
		return bad("Invalid preset count")
	}
	valid := map[string]bool{"": true}
	for _, p := range BuiltIns {
		valid[p.ID] = true
	}
	for i := 0; i < count; i++ {
		prefix := fmt.Sprintf("preset.%d.", i)
		p := Preset{ID: text(prefix + "id"), Name: text(prefix + "name")}
		var err error
		p.Layers, err = decodeLayers(text(prefix + "layers"))
		if err != nil {
			return bad(err.Error())
		}
		p.CreatedAt, e = strconv.ParseInt(get(prefix+"createdAt"), 10, 64)
		if e != nil || p.CreatedAt < 0 || !presetID.MatchString(p.ID) || !ValidName(p.Name) || len(p.Layers) == 0 || valid[p.ID] {
			return bad("Invalid or duplicate custom preset")
		}
		valid[p.ID] = true
		s.Presets = append(s.Presets, p)
	}
	s.Mix.Name = text("mix.name")
	s.Mix.ActivePreset = text("mix.activePreset")
	s.Mix.Master, e = strconv.ParseFloat(get("mix.master"), 64)
	if e != nil || !level(s.Mix.Master) || !ValidName(s.Mix.Name) || !valid[s.Mix.ActivePreset] {
		return bad("Invalid mix name, master level, or active preset")
	}
	s.Mix.Layers, e = decodeLayers(text("mix.layers"))
	if e != nil {
		return bad(e.Error())
	}
	switch get("playback.stayRunningWhenHeadphonesUnplugged") {
	case "true":
		s.StayRunning = true
	case "false":
	default:
		return bad("Invalid playback preference")
	}
	if fieldErr != nil {
		return Snapshot{}, fieldErr
	}
	return s, nil
}
func level(v float64) bool { return v >= 0 && v <= 1 }
func decodeLayers(value string) (map[string]Layer, error) {
	out := map[string]Layer{}
	if value == "" {
		return out, nil
	}
	for _, entry := range strings.Split(value, "|") {
		parts := strings.Split(entry, ",")
		if len(parts) != 3 {
			return nil, errors.New("Malformed sound layers")
		}
		id := parts[0]
		v, e := strconv.ParseFloat(parts[1], 64)
		_, known := ByID[id]
		_, dup := out[id]
		if !known || dup || e != nil || !level(v) || fmt.Sprintf("%.4f", v) != parts[1] || (parts[2] != "0" && parts[2] != "1") {
			return nil, errors.New("Malformed sound layers")
		}
		out[id] = Layer{v, parts[2] == "1"}
	}
	return out, nil
}
func ReadBackup(path string) (Snapshot, error) {
	f, e := os.Open(path)
	if e != nil {
		return Snapshot{}, e
	}
	defer f.Close()
	b, e := io.ReadAll(io.LimitReader(f, MaxBackup+1))
	if e != nil {
		return Snapshot{}, e
	}
	return Decode(string(b))
}

// Store locks the state for the session and uses atomic replacement on the same
// filesystem. Malformed existing data is reported, never silently overwritten.
type Store struct {
	Path string
	lock *os.File
}

func OpenStore(path string) (*Store, Snapshot, error) {
	if e := os.MkdirAll(filepath.Dir(path), 0700); e != nil {
		return nil, Snapshot{}, e
	}
	f, e := os.OpenFile(path+".lock", os.O_CREATE|os.O_RDWR, 0600)
	if e != nil {
		return nil, Snapshot{}, e
	}
	if e = unix.Flock(int(f.Fd()), unix.LOCK_EX|unix.LOCK_NB); e != nil {
		f.Close()
		return nil, Snapshot{}, errors.New("Noizey is already using this settings file")
	}
	store := &Store{path, f}
	s, e := ReadBackup(path)
	if os.IsNotExist(e) {
		s, e = Default(), nil
	}
	if e != nil {
		store.Close()
		return nil, Snapshot{}, fmt.Errorf("Settings left untouched at %s: %w", path, e)
	}
	return store, s, nil
}
func (s *Store) Close() {
	if s.lock != nil {
		_ = unix.Flock(int(s.lock.Fd()), unix.LOCK_UN)
		_ = s.lock.Close()
		s.lock = nil
	}
}
func WriteBackup(path string, s Snapshot, replace bool) error {
	data := Encode(s)
	if _, err := Decode(data); err != nil {
		return err
	}
	f, e := os.CreateTemp(filepath.Dir(path), ".noizey-*")
	if e != nil {
		return e
	}
	defer os.Remove(f.Name())
	if _, e = f.WriteString(data); e != nil {
		f.Close()
		return e
	}
	if e = f.Sync(); e != nil {
		f.Close()
		return e
	}
	if e = f.Close(); e != nil {
		return e
	}
	if replace {
		e = os.Rename(f.Name(), path)
	} else {
		e = os.Link(f.Name(), path)
	}
	if e != nil {
		return e
	}
	dir, e := os.Open(filepath.Dir(path))
	if e != nil {
		return e
	}
	defer dir.Close()
	return dir.Sync()
}
func (s *Store) Save(value Snapshot) error { return WriteBackup(s.Path, value, true) }
func (s *Store) Restore(value Snapshot, previous Snapshot) error {
	if e := WriteBackup(s.Path+".previous", previous, true); e != nil {
		return e
	}
	return s.Save(value)
}
