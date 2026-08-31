import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";

async function render(path = "/") {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request(`https://noizey.example${path}`, {
      headers: { accept: "text/html", host: "noizey.example" },
    }),
    {
      ASSETS: {
        fetch: async () => new Response("Not found", { status: 404 }),
      },
    },
    {
      waitUntil() {},
      passThroughOnException() {},
    },
  );
}

test("server-renders the Noizey product, privacy, and support page", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>Noizey[^<]*Make your own quiet<\/title>/i);
  assert.match(html, /Make your/);
  assert.match(html, /own quiet\./);
  assert.match(html, /Noizey does not collect your data/);
  assert.match(html, /Android backup and device transfer/);
  assert.match(html, /mailto:mikebannister@gmail\.com/);
  assert.doesNotMatch(html, /codex-preview|react-loading-skeleton|Starter Project/i);
});

test("serves the dedicated privacy policy route", async () => {
  const response = await render("/privacy");
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>Privacy[^<]*Noizey<\/title>/i);
  assert.match(html, /Noizey does not collect your data/);
  assert.match(html, /Android backup and device transfer/);
  assert.match(html, /rel="canonical" href="https:\/\/noizey\.example\/privacy"/i);
  assert.match(html, /mailto:mikebannister@gmail\.com/);
});

test("removes starter assets and keeps branded metadata assets", async () => {
  const [page, privacyContent, layout, packageJson] = await Promise.all([
    readFile(new URL("../app/page.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/privacy-content.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/layout.tsx", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
    access(new URL("../public/noizey-icon.png", import.meta.url)),
    access(new URL("../public/og.png", import.meta.url)),
  ]);

  assert.match(page, /PrivacyPolicyContent/);
  assert.match(privacyContent, /Noizey does not collect your data/);
  assert.match(layout, /Noizey — Make your own quiet/);
  assert.match(layout, /\/og\.png/);
  assert.doesNotMatch(packageJson, /react-loading-skeleton/);
  await assert.rejects(access(new URL("../app/_sites-preview", import.meta.url)));
});
