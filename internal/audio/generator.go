// Package audio ports Android's procedural DSP to a 48 kHz desktop mixer.
package audio

import "math"

const SampleRate = 48000
const tau = 2 * math.Pi

type random uint32

func (r *random) unit() float64 {
	v := uint32(*r)
	v ^= v << 13
	v ^= v >> 17
	v ^= v << 5
	*r = random(v)
	return float64(v>>8) / 16777216
}
func (r *random) bipolar() float64             { return r.unit()*2 - 1 }
func (r *random) between(a, b float64) float64 { return a + (b-a)*r.unit() }
func clip(v, limit float64) float64            { return math.Max(-limit, math.Min(limit, v)) }
func phase(p *float64, hz float64) float64 {
	*p += tau * hz / SampleRate
	*p = math.Mod(*p, tau)
	return *p
}

type channel struct {
	rng       random
	v         [8]float64
	phase     float64
	frequency float64
}
type generator struct {
	id                     string
	c                      [2]channel
	phase                  float64
	countdown, age, chirps int
	pan                    float64
}

func newGenerator(id string) *generator {
	seeds := map[string][2]uint32{
		"white": {0x13579BDF, 0x2468ACE}, "brown": {0x0BADC0DE, 0x51A7E123}, "pink": {0x10293847, 0x56473829},
		"blue": {0x1122AA55, 0x55AA2211}, "violet": {0x1122AA55, 0x55AA2211}, "gray": {0x22FF11AA, 0x33EE44BB}, "green": {0x5555AAAA, 0x77773333},
		"deep_fan": {0x40302010, 0x80706050}, "cabin_hum": {0x10203040, 0x50607080},
		"soft_rain": {0x19770214, 0x20010909}, "rain_window": {0x19770214, 0x20010909}, "heavy_rain": {0x19770214, 0x20010909},
		"distant_thunder": {0x0DDC0FFE, 0x0DDC0FFF}, "ocean": {0x0CEA0123, 0x0CEA0456},
		"stream": {0x57EA0123, 0x57EA0456}, "waterfall": {0x7A7EFA11, 0x7A7EFA22}, "wind_trees": {0x711D0123, 0x711D0456},
		"forest_night": {0xF0AE5711, 0xF0AE5722}, "fireplace": {0xF1AE0123, 0xF1AE0456},
	}
	seed, ok := seeds[id]
	if !ok {
		panic("missing generator: " + id)
	}
	g := &generator{id: id, age: -1, countdown: SampleRate * 2}
	for i := range g.c {
		g.c[i].rng = random(seed[i])
		g.c[i].frequency = 1800 + float64(i)*400
	}
	if id == "forest_night" {
		g.countdown = SampleRate / 2
	}
	return g
}
func (g *generator) next() (out [2]float64) {
	switch g.id {
	case "distant_thunder":
		return g.thunder()
	case "forest_night":
		return g.forest()
	case "deep_fan":
		phase(&g.phase, 72)
	case "cabin_hum":
		phase(&g.phase, 57)
	case "ocean":
		phase(&g.phase, .085)
	case "wind_trees":
		phase(&g.phase, .035)
	}
	for i := range g.c {
		c := &g.c[i]
		v := &c.v
		r := &c.rng
		switch g.id {
		case "white":
			out[i] = r.bipolar() * .58
		case "brown":
			v[0] = clip(v[0]*.9965+r.bipolar()*.032, .42)
			out[i] = v[0] * 1.72
		case "pink":
			w := r.bipolar()
			v[0] = .99886*v[0] + w*.0555179
			v[1] = .99332*v[1] + w*.0750759
			v[2] = .969*v[2] + w*.1538520
			v[3] = .8665*v[3] + w*.3104856
			v[4] = .55*v[4] + w*.5329522
			v[5] = -.7616*v[5] - w*.0168980
			out[i] = clip((v[0]+v[1]+v[2]+v[3]+v[4]+v[5]+v[6]+w*.5362)*.105, .9)
			v[6] = w * .115926
		case "blue", "violet":
			w := r.bipolar()
			d := w - v[0]
			v[0] = w
			out[i] = d * .34
			if g.id == "violet" {
				out[i] = (d - v[1]) * .19
				v[1] = d
			}
		case "gray":
			w := r.bipolar()
			v[0] = v[0]*.997 + w*.026
			b := w - v[1]
			v[1] = w
			out[i] = clip(w*.24+v[0]*.82+b*.10, .85)
		case "green":
			w := r.bipolar()
			v[0] += (w - v[0]) * .075
			v[1] += (w - v[1]) * .0018
			out[i] = clip((v[0]-v[1])*2.7+v[1]*.7, .86)
		case "deep_fan", "cabin_hum":
			smoothing, gain, motor := .018, 3.4, .07
			if g.id == "cabin_hum" {
				smoothing, gain, motor = .009, 2.9, .11
			}
			v[0] += (r.bipolar() - v[0]) * smoothing
			m := math.Sin(g.phase) * motor
			if i == 1 {
				m *= .96
			}
			out[i] = clip(v[0]*gain+m+math.Sin(g.phase*2)*.035, .9)
		case "soft_rain", "rain_window", "heavy_rain":
			density, chance, decay, dropMax := .48, .00035*.48, .992, .38
			if g.id == "heavy_rain" {
				density, chance = 1, .00035
			}
			if g.id == "rain_window" {
				density, chance, decay, dropMax = .58, .0016, .9962, .72
			}
			w := r.bipolar()
			v[0] += (w - v[0]) * .035
			if r.unit() < chance {
				v[1] = r.between(.18, dropMax)
				c.frequency = r.between(900, 3100)
			}
			out[i] = clip((w-v[0]*.65)*(.20+density*.22)+math.Sin(phase(&c.phase, c.frequency))*v[1], .95)
			v[1] *= decay
		case "ocean":
			v[0] += (r.bipolar() - v[0]) * .018
			wave := (math.Sin(g.phase+float64(i)*.34) + 1) * .5
			out[i] = clip(v[0]*3.6*(.16+.84*wave*wave), .9)
		case "stream", "waterfall":
			speed, gain, whiteGain := .075, 2.7, .07
			if g.id == "waterfall" {
				speed, gain, whiteGain = .12, 2.25, .18
			}
			w := r.bipolar()
			v[0] += (w - v[0]) * speed
			v[1] += (w - v[1]) * .005
			o := (v[0]-v[1])*gain + w*whiteGain
			if g.id == "stream" {
				if r.unit() < .00045 {
					v[2] = r.between(.12, .36)
				}
				o += math.Sin(phase(&c.phase, 720+float64(i)*90)) * v[2]
				v[2] *= .997
			}
			out[i] = clip(o, .92)
		case "wind_trees":
			v[0] += (r.bipolar() - v[0]) * .011
			v[1] += (r.bipolar() - v[1]) * .00022
			movement := math.Max(.1, math.Min(.95, .48+.34*math.Sin(g.phase+float64(i)*.55)+v[1]*1.4))
			out[i] = clip(v[0]*4.2*movement, .88)
		case "fireplace":
			v[0] += (r.bipolar() - v[0]) * .005
			if r.unit() < .0012 {
				crackle := r.between(.18, .85)
				if r.unit() <= .5 {
					crackle = -crackle
				}
				v[1] += crackle
			}
			v[1] *= .972
			out[i] = clip(v[0]*1.8+v[1], .92)
		}
	}
	return
}
func (g *generator) thunder() (out [2]float64) {
	r := &g.c[0].rng
	if g.age < 0 {
		g.countdown--
		if g.countdown <= 0 {
			g.age = 0
			g.countdown = int(SampleRate * r.between(12, 30))
		}
	}
	if g.age < 0 {
		return
	}
	seconds := float64(g.age) / SampleRate
	envelope := (1 - math.Exp(-seconds/.22)) * math.Exp(-seconds/3.4)
	g.c[0].v[0] += (r.bipolar() - g.c[0].v[0]) * .0035
	g.c[1].v[0] += (r.bipolar() - g.c[1].v[0]) * .0031
	phase(&g.phase, 34+4*math.Sin(seconds*1.7))
	out[0] = clip((math.Sin(g.phase)*.32+g.c[0].v[0]*2.8)*envelope, .9)
	out[1] = clip((math.Sin(g.phase*1.013)*.30+g.c[1].v[0]*2.8)*envelope, .9)
	g.age++
	if seconds > 8 {
		g.age = -1
	}
	return
}
func (g *generator) forest() (out [2]float64) {
	insect := 0.0
	r := &g.c[0].rng
	for i := range g.c {
		c := &g.c[i]
		c.v[0] += (c.rng.bipolar() - c.v[0]) * .006
	}
	if g.chirps > 0 {
		pulse := 0.0
		if (g.chirps/(SampleRate/70))%2 == 0 {
			pulse = 1
		}
		insect = math.Sin(phase(&g.phase, 4100)) * .16 * pulse
		g.chirps--
	} else {
		g.countdown--
		if g.countdown <= 0 {
			g.chirps = int(SampleRate * r.between(.12, .42))
			g.countdown = int(SampleRate * r.between(.35, 2.2))
			g.pan = r.between(-.75, .75)
		}
	}
	out[0] = clip(g.c[0].v[0]*1.35+insect*(1-g.pan)*.5, .75)
	out[1] = clip(g.c[1].v[0]*1.35+insect*(1+g.pan)*.5, .75)
	return
}
