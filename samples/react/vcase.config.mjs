// @ts-check
import { defineConfig } from "@vcase/react/node";

const fonts = "../compose-desktop/src/main/resources/fonts";

export default defineConfig({
  project: "vcase-react-sample",
  // The same defaults as samples/compose-desktop, so parity pairs have the same pixel sizes.
  defaults: {
    theme: ["light", "dark"],
    viewport: [
      { name: "phone", w: 360, h: 640, density: 2 },
      { name: "wide", w: 960, h: 600, density: 1, optIn: true },
      { name: "desktop", w: 1280, h: 800, density: 2, optIn: true, sizeClass: "wide" },
    ],
    locale: ["en"],
    pointer: ["touch"],
  },
  sources: [{ glob: "src/cases/*.cases.tsx", base: "src/cases", suffix: ".cases.tsx" }],
  // `vite build` + `vite preview`: what CI captures is the production bundle.
  baseURL: "http://127.0.0.1:4173",
  server: { command: "npx vite build && npx vite preview" },
  fontFiles: [`${fonts}/SpaceGrotesk-Regular.ttf`, `${fonts}/SpaceGrotesk-Bold.ttf`],
  // theme.css loads the fonts from a CDN (that does not exist); captures get the files in the repository.
  routes: [
    { url: "https://fonts.vcase.invalid/SpaceGrotesk-Regular.ttf", file: `${fonts}/SpaceGrotesk-Regular.ttf` },
    { url: "https://fonts.vcase.invalid/SpaceGrotesk-Bold.ttf", file: `${fonts}/SpaceGrotesk-Bold.ttf` },
  ],
  // `vcase-react parity` puts these cases next to the Compose sample's goldens.
  parity: { mobile: "../compose-desktop/goldens/desktop-skiko" },
});
