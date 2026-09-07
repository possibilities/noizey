package audio

import (
	"fmt"
	"runtime"
	"time"

	"github.com/gen2brain/malgo"
	"noizey/internal/model"
)

type Engine struct {
	Renderer *Renderer
	context  *malgo.AllocatedContext
	device   *malgo.Device
	mix      model.Mix
	fade     float64
	route    string
	Output   string
}

func New() *Engine { return &Engine{Renderer: NewRenderer(), fade: 1, Output: "System default"} }
func (e *Engine) Update(m model.Mix, fade float64, playing bool) {
	e.mix = m
	e.fade = fade
	e.Renderer.Set(m, fade, playing)
}
func (e *Engine) Start() error {
	if e.device != nil {
		e.Close()
	}
	backends := []malgo.Backend{malgo.BackendPulseaudio, malgo.BackendAlsa, malgo.BackendJack}
	if runtime.GOOS == "darwin" {
		backends = []malgo.Backend{malgo.BackendCoreaudio}
	}
	// Explicit backends exclude miniaudio's silent null fallback.
	ctx, err := malgo.InitContext(backends, malgo.ContextConfig{}, nil)
	if err != nil {
		return fmt.Errorf("Audio output unavailable: %w", err)
	}
	e.context = ctx
	config := malgo.DefaultDeviceConfig(malgo.Playback)
	config.Playback.Format = malgo.FormatF32
	config.Playback.Channels = 2
	config.SampleRate = SampleRate
	config.PeriodSizeInMilliseconds = 20
	config.Pulse.StreamNamePlayback = "Noizey"
	config.Playback.ShareMode = malgo.Shared
	e.device, err = malgo.InitDevice(ctx.Context, config, malgo.DeviceCallbacks{Data: func(out, in []byte, frames uint32) { e.Renderer.Render(out) }})
	if err != nil {
		e.Close()
		return fmt.Errorf("Cannot open the default audio output: %w", err)
	}
	e.route, e.Output, _ = e.defaultRoute()
	e.Renderer.Set(e.mix, e.fade, true)
	if err = e.device.Start(); err != nil {
		e.Close()
		return fmt.Errorf("Cannot start audio: %w", err)
	}
	return nil
}
func (e *Engine) Pause() {
	e.Renderer.Set(e.mix, e.fade, false)
	if e.device != nil && e.device.IsStarted() {
		time.Sleep(80 * time.Millisecond)
		_ = e.device.Stop()
	}
}
func (e *Engine) Close() {
	if e.device != nil {
		e.device.Uninit()
		e.device = nil
	}
	if e.context != nil {
		_ = e.context.Uninit()
		e.context.Free()
		e.context = nil
	}
}
func (e *Engine) defaultRoute() (string, string, error) {
	list, err := e.context.Devices(malgo.Playback)
	if err != nil {
		return "", "System default", err
	}
	for _, d := range list {
		if d.IsDefault != 0 {
			return d.ID.String(), d.Name(), nil
		}
	}
	return "", "System default", nil
}

// RouteChanged is polled on the UI thread, never from the real-time callback.
func (e *Engine) RouteChanged() (bool, error) {
	if e.device == nil || !e.device.IsStarted() {
		return false, fmt.Errorf("Audio output stopped; press Space to reconnect")
	}
	route, name, err := e.defaultRoute()
	if err != nil {
		return false, err
	}
	changed := route != e.route
	e.route = route
	e.Output = name
	return changed, nil
}
