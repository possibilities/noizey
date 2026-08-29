import type { Metadata } from "next";
import { headers } from "next/headers";
import "./globals.css";

export async function generateMetadata(): Promise<Metadata> {
  const requestHeaders = await headers();
  const host = requestHeaders.get("x-forwarded-host") ?? requestHeaders.get("host") ?? "localhost:3000";
  const protocol = requestHeaders.get("x-forwarded-proto") ?? (host.startsWith("localhost") ? "http" : "https");
  const origin = `${protocol}://${host}`;

  return {
    metadataBase: new URL(origin),
    title: "Noizey — Make your own quiet",
    description:
      "An offline Android sound mixer with procedural noise and nature soundscapes. No ads, accounts, tracking, or subscriptions.",
    applicationName: "Noizey",
    icons: {
      icon: "/noizey-icon.png",
      shortcut: "/noizey-icon.png",
      apple: "/noizey-icon.png",
    },
    openGraph: {
      type: "website",
      url: origin,
      siteName: "Noizey",
      title: "Noizey — Make your own quiet",
      description: "Procedural sound for focus, rest, and masking distraction.",
      images: [{ url: `${origin}/og.png`, width: 1024, height: 500, alt: "Noizey — Make your own quiet" }],
    },
    twitter: {
      card: "summary_large_image",
      title: "Noizey — Make your own quiet",
      description: "Procedural sound for focus, rest, and masking distraction.",
      images: [`${origin}/og.png`],
    },
  };
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
