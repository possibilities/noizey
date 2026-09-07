package tui

import (
	"errors"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"golang.org/x/sys/unix"
	"golang.org/x/term"
)

// Run owns terminal modes and restores them on every normal exit or signal.
func Run(app *App, meter func() float64, output func() string) error {
	fd := int(os.Stdin.Fd())
	if !term.IsTerminal(fd) || !term.IsTerminal(int(os.Stdout.Fd())) {
		return errors.New("Noizey needs an interactive terminal; use --headless for playback without a UI")
	}
	old, err := term.MakeRaw(fd)
	if err != nil {
		return err
	}
	defer term.Restore(fd, old)
	fmt.Print("\x1b[?1049h\x1b[?25l\x1b[?7l\x1b[?2004h\x1b[?2031h")
	defer fmt.Print("\x1b[0m\x1b[?2031l\x1b[?2004l\x1b[?7h\x1b[?25h\x1b[?1049l")
	signals := make(chan os.Signal, 4)
	signal.Notify(signals, os.Interrupt, syscall.SIGTERM, syscall.SIGHUP, syscall.SIGWINCH)
	defer signal.Stop(signals)
	t, query := newTheme(os.Getenv("FX_THEME"), os.Getenv("COLORFGBG"), time.Now())
	fmt.Print(query)
	parser := &input{}
	buf := make([]byte, 4096)
	lastTick := time.Time{}
	lastPaint := time.Time{}
	previous := ""
	for {
		select {
		case s := <-signals:
			if s != syscall.SIGWINCH {
				return nil
			}
			previous = ""
		default:
		}
		now := time.Now()
		fds := []unix.PollFd{{Fd: int32(fd), Events: unix.POLLIN}}
		n, err := unix.Poll(fds, 25)
		now = time.Now()
		if err != nil && err != unix.EINTR {
			return err
		}
		var events []event
		if n > 0 && fds[0].Revents&(unix.POLLIN|unix.POLLHUP) != 0 {
			count, err := unix.Read(fd, buf)
			if err != nil {
				if err == unix.EINTR {
					continue
				}
				return err
			}
			if count == 0 {
				return nil
			}
			events = parser.feed(string(buf[:count]), now)
		} else {
			events = parser.timeout(now)
		}
		for _, e := range events {
			switch e.key {
			case "osc", "da1", "theme":
				fmt.Print(t.handle(e, now))
			default:
				if app.Handle(e) {
					return nil
				}
			}
		}
		if now.Sub(lastTick) >= 100*time.Millisecond {
			app.Tick(now)
			lastTick = now
		}
		t.tick(now)
		if t.ready && (len(events) > 0 || now.Sub(lastPaint) >= 100*time.Millisecond) {
			w, h, err := term.GetSize(int(os.Stdout.Fd()))
			if err != nil {
				return err
			}
			frame := app.View(min(w, 400), min(h, 160), t.palette(), meter(), output())
			if frame != previous {
				if _, err = fmt.Print("\x1b[?2026h" + frame + "\x1b[?2026l"); err != nil {
					return err
				}
				previous = frame
			}
			lastPaint = now
		}
	}
}
