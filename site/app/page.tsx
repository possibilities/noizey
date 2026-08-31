import { PrivacyPolicyContent, privacyEffectiveDate } from "./privacy-content";

const supportEmail = "mikebannister@gmail.com";

const soundRows = [
  { name: "Brown noise", width: "82%" },
  { name: "Soft rain", width: "64%" },
  { name: "Distant thunder", width: "28%" },
  { name: "Deep fan", width: "48%" },
];

export default function Home() {
  return (
    <main>
      <nav className="nav" aria-label="Primary navigation">
        <a className="brand" href="#top" aria-label="Noizey home">
          <span className="brand-mark" aria-hidden="true">
            <span />
          </span>
          <span>Noizey</span>
        </a>
        <div className="nav-links">
          <a href="/privacy">Privacy</a>
          <a href="#support">Support</a>
        </div>
      </nav>

      <section className="hero" id="top">
        <div className="hero-copy">
          <p className="eyebrow">Offline sound mixer for Android</p>
          <h1>Make your<br />own quiet.</h1>
          <p className="lede">
            Layer endlessly generated noise and procedural soundscapes into a
            mix that fits the moment. No accounts, ads, tracking, or network
            access.
          </p>
          <div className="launch-card" aria-label="Google Play launch details">
            <span className="launch-dot" aria-hidden="true" />
            <span>Coming to Google Play</span>
            <span className="launch-divider" aria-hidden="true" />
            <strong>$1.99 once</strong>
          </div>
        </div>

        <div className="mixer-shell" aria-label="Stylized Noizey mixer preview">
          <div className="mixer-topline">
            <span className="tiny-brand">NOIZEY</span>
            <span className="playing"><i /> PLAYING</span>
          </div>
          <div className="mixer-orbit" aria-hidden="true">
            <span className="orbit-middle" />
            <span className="orbit-core" />
          </div>
          <div className="mix-name">
            <span>Current mix</span>
            <strong>Deep Focus</strong>
          </div>
          <div className="sound-rows">
            {soundRows.map((sound) => (
              <div className="sound-row" key={sound.name}>
                <div className="sound-label">
                  <span>{sound.name}</span>
                  <span>{sound.width}</span>
                </div>
                <div className="sound-track">
                  <span style={{ width: sound.width }} />
                </div>
              </div>
            ))}
          </div>
          <div className="mixer-controls" aria-hidden="true">
            <span>−</span><span className="pause">Ⅱ</span><span>+</span>
          </div>
        </div>
      </section>

      <section className="proof" aria-label="Noizey principles">
        <article>
          <span className="proof-number">01</span>
          <h2>Generated live</h2>
          <p>Every sound is synthesized on your phone, with no downloaded loops or seams.</p>
        </article>
        <article>
          <span className="proof-number">02</span>
          <h2>Built to coexist</h2>
          <p>Mix Noizey under music, podcasts, or video instead of interrupting them.</p>
        </article>
        <article>
          <span className="proof-number">03</span>
          <h2>Pay once</h2>
          <p>One small purchase. No subscription, account, ads, or in-app upsell.</p>
        </article>
      </section>

      <section className="details" aria-labelledby="details-title">
        <div>
          <p className="eyebrow">Shape the room around you</p>
          <h2 id="details-title">Noise with texture,<br />not repetition.</h2>
        </div>
        <div className="details-copy">
          <p>
            Blend seven colors of noise with rain, thunder, ocean, stream,
            waterfall, wind, forest night, fireplace, deep fan, and cabin hum.
            Tune each layer independently, save custom presets, and let playback
            continue with the screen off.
          </p>
          <ul>
            <li>Independent layer and master levels</li>
            <li>15–120 minute sleep timer with a gentle fade</li>
            <li>Portable settings backup and restore</li>
            <li>Android 8 and newer</li>
          </ul>
        </div>
      </section>

      <section className="policy" id="privacy" aria-labelledby="privacy-title">
        <div className="section-heading">
          <p className="eyebrow">Plain-language policy</p>
          <h2 id="privacy-title">Privacy</h2>
          <p className="updated">Effective {privacyEffectiveDate}</p>
        </div>
        <PrivacyPolicyContent />
      </section>

      <section className="support" id="support" aria-labelledby="support-title">
        <div>
          <p className="eyebrow">Questions, bugs, or feedback</p>
          <h2 id="support-title">Talk to a human.</h2>
        </div>
        <div className="support-action">
          <p>
            Tell us what happened, which Android device you use, and what you
            expected. We&apos;ll help.
          </p>
          <a href={`mailto:${supportEmail}`}>{supportEmail}</a>
        </div>
      </section>

      <footer>
        <a className="brand brand-small" href="#top">
          <span className="brand-mark" aria-hidden="true"><span /></span>
          <span>Noizey</span>
        </a>
        <p>© 2026 Noizey Studio · Portland, Maine</p>
      </footer>
    </main>
  );
}
