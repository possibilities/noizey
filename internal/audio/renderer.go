package audio

import (
	"encoding/binary"
	"math"
	"sync/atomic"

	"noizey/internal/model"
)

type targets struct {
	gains   [19]float64
	master  float64
	playing bool
}

// Only the audio callback touches DSP state; the UI publishes immutable targets.
type Renderer struct {
	generators        [19]*generator
	gains             [19]float64
	master, transport float64
	target            atomic.Pointer[targets]
	peak              atomic.Uint64
	frames            atomic.Uint64
}

func NewRenderer() *Renderer {
	r := &Renderer{}
	for i, s := range model.Sounds {
		r.generators[i] = newGenerator(s.ID)
	}
	r.target.Store(&targets{})
	return r
}
func (r *Renderer) Set(m model.Mix, fade float64, playing bool) {
	t := &targets{master: model.Clamp(m.Master) * model.Clamp(fade), playing: playing}
	for i, s := range model.Sounds {
		if l := m.Layers[s.ID]; l.Enabled {
			t.gains[i] = model.Clamp(l.Volume)
		}
	}
	r.target.Store(t)
}
func (r *Renderer) Peak() float64  { return math.Float64frombits(r.peak.Load()) }
func (r *Renderer) Frames() uint64 { return r.frames.Load() }

// Render writes interleaved float32 little-endian stereo without allocating.
func (r *Renderer) Render(out []byte) {
	t := r.target.Load()
	peak := 0.0
	layerSmooth := 1 - math.Exp(-1.0/(SampleRate*.16))
	masterSmooth := 1 - math.Exp(-1.0/(SampleRate*.08))
	for pos := 0; pos+8 <= len(out); pos += 8 {
		if t.playing {
			r.transport = math.Min(1, r.transport+1.0/2400)
		} else {
			r.transport = math.Max(0, r.transport-1.0/2400)
		}
		r.master += (t.master - r.master) * masterSmooth
		sum := [2]float64{}
		if r.transport > 0 {
			for i, g := range r.generators {
				r.gains[i] += (t.gains[i] - r.gains[i]) * layerSmooth
				if r.gains[i] > .00005 || t.gains[i] > 0 {
					frame := g.next()
					sum[0] += frame[0] * r.gains[i]
					sum[1] += frame[1] * r.gains[i]
				}
			}
		}
		for ch := range sum {
			sample := math.Tanh(sum[ch]*.82) * r.master * r.transport
			peak = math.Max(peak, math.Abs(sample))
			binary.LittleEndian.PutUint32(out[pos+ch*4:], math.Float32bits(float32(sample)))
		}
	}
	r.peak.Store(math.Float64bits(peak))
	r.frames.Add(uint64(len(out) / 8))
}
