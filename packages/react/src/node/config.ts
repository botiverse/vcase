import { existsSync } from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";
import type { Tolerance } from "./compare.js";
import { routeProblems, type RouteConfig } from "./routes.js";
import type { ManifestDefaults } from "./manifest.js";
import type { SourceSpec } from "./scan.js";

/** How the host page is served while capturing (like Playwright's `webServer`). */
export interface ServerConfig {
  /** Shell command that serves `baseURL`, e.g. `npx vite preview --port 4173 --strictPort`. */
  command: string;
  /** Working directory (default: the config directory). */
  cwd?: string;
  /** How long to wait for `baseURL` to answer. Default 120 s. */
  timeoutMs?: number;
  /** Use a server already answering at `baseURL` instead of starting one. Default true. */
  reuseExisting?: boolean;
}

/** `vcase.config.mjs` (or `.js` / `.json`): the React counterpart of Kotlin's `VCaseHost` + `VCaseDesktopConfig`. */
export interface VCaseReactConfig {
  /** `project` of the manifest. */
  project: string;
  /** Project axis defaults, written to the manifest as they are (same shape as the Kotlin host's). */
  defaults: ManifestDefaults;
  /** Where cases are. */
  sources: SourceSpec[];
  /** Directory manifest `source` paths are relative to (default: the git root). */
  sourceRoot?: string;
  /** Committed manifest. Default `vcase.manifest.json`. */
  manifest?: string;
  /** Golden root; images live in `<goldens>/<renderer>/`. Default `goldens`. */
  goldens?: string;
  /** Actual images, diffs, metadata and `report/`. Default `build/vcase`. */
  out?: string;
  /** Golden directory name and `renderer` of capture metadata. Default `web-chromium`. */
  renderer?: string;
  /** `platform` of capture metadata. Default `web`. */
  platform?: string;
  /** Origin the host page is served at, e.g. `http://localhost:4173`. */
  baseURL: string;
  /**
   * Path (or absolute URL) of one variant, with `{placeholders}` replaced URL-encoded:
   * `{key}`, `{export}`, `{storyId}`, `{id}`, `{theme}`, `{viewport}`, `{w}`, `{h}`, `{density}`,
   * `{sizeClass}`, `{locale}`, `{pointer}`, `{fontScale}`, `{fixture}`. Default {@link DEFAULT_URL}
   * (what `readVariant()` reads).
   */
  url?: string;
  server?: ServerConfig;
  /** Element a component case is captured by. Default `[data-vcase-root]`. */
  rootSelector?: string;
  /** Waited for before capturing; null to skip. Default `html[data-vcase-ready]` (see `markReady()`). */
  readySelector?: string | null;
  /** Animation frames waited before every capture; a case's `settleFrames` overrides it. Default 2. */
  settleFrames?: number;
  /** `Date` seen by the page (fixed; timers keep running). Null for the real clock. Default 2026-01-01T00:00:00Z. */
  clock?: string | null;
  /** Default `UTC`. */
  timezoneId?: string;
  /** `prefers-reduced-motion`. Default `reduce`. */
  reducedMotion?: "reduce" | "no-preference";
  /** Origins requests may go to besides `baseURL`'s. Anything else fails the variant (unless a route answers it). */
  allowedOrigins?: string[];
  /**
   * Requests answered locally, checked before anything else (the first match wins), e.g. a font
   * CDN's stylesheet and font files served from the repository's own copies.
   */
  routes?: RouteConfig[];
  /** Pixel comparison; default zero tolerance. */
  tolerance?: Tolerance;
  /** Font files hashed into capture metadata `fontsHash`. */
  fontFiles?: string[];
  /** CSS added to every page before capturing. */
  css?: string;
  /** Per-variant timeout. Default 30 s. */
  timeoutMs?: number;
  /** Other platforms' golden directories for `parity`, e.g. `{ mobile: "../mobile/goldens/desktop-skiko" }`. */
  parity?: Record<string, string>;
}

export const DEFAULT_URL =
  "/?case={key}&export={export}&id={id}&theme={theme}&viewport={viewport}&locale={locale}&pointer={pointer}&fontScale={fontScale}&sizeClass={sizeClass}&fixture={fixture}";

export const CONFIG_FILES = ["vcase.config.mjs", "vcase.config.js", "vcase.config.json"];

export interface LoadedConfig {
  config: VCaseReactConfig;
  /** Directory of the config file; relative paths resolve against it. */
  dir: string;
  file: string;
}

export async function loadConfig(cwd: string, explicit?: string): Promise<LoadedConfig> {
  const file = explicit ? path.resolve(cwd, explicit) : CONFIG_FILES.map((f) => path.join(cwd, f)).find((f) => existsSync(f));
  if (!file || !existsSync(file)) throw new Error(`no vcase config (${CONFIG_FILES.join(", ")}) in ${cwd}`);
  let config: VCaseReactConfig;
  if (file.endsWith(".json")) {
    const { readFileSync } = await import("node:fs");
    config = JSON.parse(readFileSync(file, "utf8")) as VCaseReactConfig;
  } else {
    const mod = (await import(pathToFileURL(file).href)) as { default?: VCaseReactConfig };
    if (!mod.default) throw new Error(`${file} must export default a config`);
    config = mod.default;
  }
  for (const k of ["project", "defaults", "sources", "baseURL"] as const) if (config[k] === undefined) throw new Error(`${file}: \`${k}\` is required`);
  const problems = routeProblems(config.routes, path.dirname(file));
  if (problems.length > 0) throw new Error(problems.map((p) => `${file}: ${p}`).join("\n"));
  return { config, dir: path.dirname(file), file };
}

/** For `vcase.config.mjs`: `export default defineConfig({...})` gives types in editors. */
export function defineConfig(config: VCaseReactConfig): VCaseReactConfig {
  return config;
}
