package main

import (
	"errors"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"noizey/internal/audio"
	"noizey/internal/model"
	"noizey/internal/tui"
)

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, "noizey:", err)
		os.Exit(1)
	}
}
func run() error {
	var statePath, preset, importPath, exportPath string
	var play, headless, list bool
	var minutes int
	flag.StringVar(&statePath, "state", "", "settings file (default: user config directory/noizey/settings.noizey)")
	flag.StringVar(&preset, "preset", "", "load a preset by id or exact name")
	flag.BoolVar(&play, "play", false, "start playback immediately")
	flag.BoolVar(&headless, "headless", false, "play without a UI until interrupted or the sleep timer ends")
	flag.IntVar(&minutes, "timer", 0, "sleep after 15, 30, 45, 60, 90, or 120 minutes")
	flag.StringVar(&importPath, "import", "", "restore an Android-compatible settings backup and exit")
	flag.StringVar(&exportPath, "export", "", "write a settings backup to a new file and exit")
	flag.BoolVar(&list, "list-presets", false, "list factory and saved presets and exit")
	flag.Usage = func() {
		fmt.Fprint(flag.CommandLine.Output(), "Noizey · offline procedural sound mixer\n\nRun: go run .  (or build with go build -o noizey .)\n\nSpace play/pause · 1–4 views · ? help · q quit\n\n")
		flag.PrintDefaults()
	}
	flag.Parse()
	if flag.NArg() > 0 {
		return errors.New("Unexpected arguments; use --help for usage")
	}
	switch minutes {
	case 0, 15, 30, 45, 60, 90, 120:
	default:
		return errors.New("Sleep timer must be 15, 30, 45, 60, 90, or 120 minutes")
	}
	if importPath != "" && (exportPath != "" || preset != "" || list || play || headless || minutes != 0) {
		return errors.New("Use --import by itself with an optional --state")
	}
	if statePath == "" {
		dir, e := os.UserConfigDir()
		if e != nil {
			return e
		}
		statePath = filepath.Join(dir, "noizey", "settings.noizey")
	}
	store, state, err := model.OpenStore(statePath)
	if err != nil {
		return err
	}
	defer store.Close()
	if importPath != "" {
		imported, e := model.ReadBackup(importPath)
		if e != nil {
			return e
		}
		if e = store.Restore(imported, state); e != nil {
			return e
		}
		fmt.Println("Settings restored. Previous settings:", store.Path+".previous")
		return nil
	}
	if preset != "" {
		found := false
		for _, p := range state.AllPresets() {
			if p.ID == preset || p.Name == preset {
				state.Apply(p)
				found = true
				break
			}
		}
		if !found {
			return fmt.Errorf("Unknown preset %q; use --list-presets", preset)
		}
		if err = store.Save(state); err != nil {
			return err
		}
	}
	if exportPath != "" {
		if err = model.WriteBackup(exportPath, state, false); err != nil {
			return err
		}
		fmt.Println("Backup saved:", exportPath)
		return nil
	}
	if list {
		for _, p := range state.AllPresets() {
			fmt.Printf("%-24s %s\n", p.ID, printable(p.Name))
		}
		return nil
	}
	engine := audio.New()
	defer engine.Close()
	defer engine.Pause()
	app := tui.New(state, store, engine)
	if minutes > 0 {
		app.Timer.Set(time.Duration(minutes)*time.Minute, false, time.Now())
	}
	if play || headless {
		app.Start()
		if !app.Playing {
			return app.Err()
		}
	}
	if headless {
		fmt.Println("Noizey playing:", printable(state.Mix.Name), "· Ctrl-C to stop")
		signals := make(chan os.Signal, 1)
		signal.Notify(signals, os.Interrupt, syscall.SIGTERM, syscall.SIGHUP)
		defer signal.Stop(signals)
		ticker := time.NewTicker(100 * time.Millisecond)
		defer ticker.Stop()
		for {
			select {
			case <-signals:
				return nil
			case now := <-ticker.C:
				app.Tick(now)
				if !app.Playing {
					return app.Err()
				}
			}
		}
	}
	return tui.Run(app, engine.Renderer.Peak, func() string { return engine.Output })
}
func printable(s string) string { return fmt.Sprintf("%q", s) }
