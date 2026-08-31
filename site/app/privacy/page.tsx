import type { Metadata } from "next";
import { PrivacyPolicyContent, privacyEffectiveDate } from "../privacy-content";

const supportEmail = "mikebannister@gmail.com";

export const metadata: Metadata = {
  title: "Privacy — Noizey",
  description:
    "Noizey's plain-language privacy policy: no accounts, ads, analytics, tracking, or data collection.",
  alternates: { canonical: "/privacy" },
};

export default function PrivacyPage() {
  return (
    <main>
      <nav className="nav" aria-label="Primary navigation">
        <a className="brand" href="/" aria-label="Noizey home">
          <span className="brand-mark" aria-hidden="true">
            <span />
          </span>
          <span>Noizey</span>
        </a>
        <div className="nav-links">
          <a href="/">Product</a>
          <a href={`mailto:${supportEmail}`}>Support</a>
        </div>
      </nav>

      <section className="policy privacy-page" aria-labelledby="privacy-title">
        <div className="section-heading">
          <p className="eyebrow">Plain-language policy</p>
          <h1 id="privacy-title">Privacy</h1>
          <p className="updated">Effective {privacyEffectiveDate}</p>
        </div>
        <PrivacyPolicyContent />
      </section>

      <footer>
        <a className="brand brand-small" href="/">
          <span className="brand-mark" aria-hidden="true"><span /></span>
          <span>Noizey</span>
        </a>
        <p>© 2026 Noizey Studio · Portland, Maine</p>
      </footer>
    </main>
  );
}
