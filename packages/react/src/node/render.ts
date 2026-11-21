import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import path from "node:path";
import type { Browser, Page } from "playwright";
import { axisMap, type VCaseVariant } from "./axes.js";
import { DEFAULT_URL, type VCaseReactConfig } from "./config.js";
import type { CaptureMeta, CaptureOutcome } from "./golden.js";
import { compileRoutes, findRoute, type CompiledRoute } from "./routes.js";
import type { ScannedCase } from "./scan.js";

/** CSS added to every page: no caret, no transitions, no scrollbars. Playwright also stops CSS animations at capture. */
export const FREEZE_CSS =
  "*,*::before,*::after{caret-color:transparent!important;transition-duration:0s!important;transition-delay:0s!important}" +
  "::-webkit-scrollbar{display:none!important}";

/** Chromium flags for repeatable pixels. */
export const CHROMIUM_ARGS = ["--force-color-profile=srgb", "--font-render-hinting=none", "--disable-lcd-text", "--disable-skia-runtime-opts", "--hide-scrollbars"];

export const DEFAULT_CLOCK = "2026-01-01T00:00:00.000Z";

/** The variant's URL: the template with every `{placeholder}` replaced, URL-encoded. */
export function variantUrl(cfg: Pick<VCaseReactConfig, "baseURL" | "url">, c: ScannedCase, v: VCaseVariant): string {
  const values: Record<string, string> = {
    key: c.key,
    export: c.exportName ?? "",
    storyId: c.storyId ?? "",
    id: v.case.id,
    theme: v.axes.theme,
    viewport: v.axes.viewport.name,
    w: String(v.axes.viewport.w),
    h: String(v.axes.viewport.h),
    density: String(v.axes.viewport.density),
    sizeClass: v.axes.viewport.sizeClass ?? "",
    locale: v.axes.locale,
    pointer: v.axes.pointer,
    fontScale: axisMap(v).fontScale!,
    fixture: v.case.fixture ?? "",
  };
  const filled = (cfg.url ?? DEFAULT_URL).replace(/\{(\w+)\}/g, (m, k: string) => {
    if (!(k in values)) throw new Error(`unknown URL placeholder ${m}`);
    return encodeURIComponent(values[k]!);
  });
  return new URL(filled, cfg.baseURL).href;
}

export function fontsHash(dir: string, files: string[] | undefined): string | null {
  if (!files || files.length === 0) return null;
  const h = createHash("sha256");
  for (const f of files) h.update(readFileSync(path.resolve(dir, f)));
  return h.digest("hex").slice(0, 16);
}

export interface Renderer {
  render(c: ScannedCase, v: VCaseVariant): Promise<CaptureOutcome>;
  close(): Promise<void>;
}

/** Opens one Chromium; every variant gets a fresh context at its viewport, density, theme, locale and pointer. */
export async function createRenderer(cfg: VCaseReactConfig, dir: string): Promise<Renderer> {
  const { chromium } = await import("playwright");
  const browser: Browser = await chromium.launch({ args: CHROMIUM_ARGS });
  const hash = fontsHash(dir, cfg.fontFiles);
  const routes = compileRoutes(cfg.routes, dir);
  return {
    render: (c, v) => renderVariant(browser, cfg, hash, routes, c, v),
    close: () => browser.close(),
  };
}

interface Box {
  x: number;
  y: number;
  w: number;
  h: number;
}

async function renderVariant(browser: Browser, cfg: VCaseReactConfig, hash: string | null, routes: CompiledRoute[], c: ScannedCase, v: VCaseVariant): Promise<CaptureOutcome> {
  const started = Date.now();
  const warnings: string[] = [];
  const errors: string[] = [];
  const meta = (regions: CaptureMeta["regions"] = {}): CaptureMeta => ({
    id: v.case.id,
    axes: axisMap(v),
    platform: cfg.platform ?? "web",
    renderer: cfg.renderer ?? "web-chromium",
    fontsHash: hash,
    durationMs: Date.now() - started,
    regions,
    warnings,
  });
  if (v.case.needsDevice) return { kind: "skipped", reason: `needsDevice: not rendered on ${cfg.renderer ?? "web-chromium"}` };

  const vp = v.axes.viewport;
  const cap = v.case.capture ?? {};
  const component = v.case.kind !== "screen";
  // A component may be bounded wider than the viewport (a wide gallery): the window grows to the bound.
  const width = component && cap.maxWidth != null ? Math.max(vp.w, cap.maxWidth) : vp.w;
  const theme = v.axes.theme;
  const context = await browser.newContext({
    viewport: { width, height: vp.h },
    deviceScaleFactor: vp.density,
    locale: v.axes.locale,
    timezoneId: cfg.timezoneId ?? "UTC",
    colorScheme: theme === "dark" || theme === "light" ? theme : null,
    reducedMotion: cfg.reducedMotion ?? "reduce",
    hasTouch: v.axes.pointer === "touch",
  });
  const timeout = cfg.timeoutMs ?? 30_000;
  context.setDefaultTimeout(timeout);
  const page = await context.newPage();
  try {
    const url = variantUrl(cfg, c, v);
    const allowed = new Set([new URL(cfg.baseURL).origin, ...(cfg.allowedOrigins ?? []).map((o) => new URL(o).origin)]);
    await page.route("**/*", (route) => {
      const href = route.request().url();
      const local = findRoute(routes, href);
      if (local) return route.fulfill({ status: local.status, headers: local.headers(href), body: local.body });
      const u = new URL(href);
      if (u.protocol === "data:" || u.protocol === "blob:" || allowed.has(u.origin)) return route.continue();
      errors.push(`unmocked ${route.request().method()} ${u.href}`);
      return route.abort("blockedbyclient");
    });
    page.on("pageerror", (e) => errors.push(`page error: ${e.message}`));
    page.on("console", (m) => {
      if (m.type() === "error") warnings.push(`console: ${m.text()}`);
    });

    const interval = cap.frameIntervalMs ?? null;
    const clock = cfg.clock === undefined ? DEFAULT_CLOCK : cfg.clock;
    if (interval != null) {
      // Fake timers, paused: time moves only in settle(), so the capture is at a known instant.
      const start = new Date(clock ?? DEFAULT_CLOCK).getTime();
      await page.clock.install({ time: start });
      await page.clock.pauseAt(start + 1000);
    } else if (clock) {
      await page.clock.setFixedTime(clock);
    }

    const response = await page.goto(url, { waitUntil: "load" });
    if (response && !response.ok()) errors.push(`${url}: HTTP ${response.status()}`);
    await page.waitForLoadState("networkidle");
    const ready = cfg.readySelector === undefined ? "html[data-vcase-ready]" : cfg.readySelector;
    if (ready) await page.waitForSelector(ready, { state: "attached" });
    const failed = await page.evaluate(() => document.documentElement.getAttribute("data-vcase-error"));
    if (failed) errors.push(`case failed to render: ${failed}`);
    const rootSelector = cfg.rootSelector ?? "[data-vcase-root]";
    if (component) await page.waitForSelector(rootSelector, { state: "visible" });
    await page.addStyleTag({ content: FREEZE_CSS + (cfg.css ?? "") });
    await page.evaluate(async () => {
      await document.fonts.ready;
      await Promise.all([...document.images].map((img) => (img.complete ? Promise.resolve() : img.decode().catch(() => undefined))));
    });
    await page.waitForLoadState("networkidle");
    await settle(page, cap.settleFrames ?? cfg.settleFrames ?? 2, interval);
    if (errors.length > 0) return { kind: "error", message: errors.join("; "), meta: meta() };

    const shot = { animations: "disabled", caret: "hide", scale: "device", timeout } as const;
    let png: Buffer;
    let origin: Box;
    if (component) {
      const box = await page.locator(rootSelector).first().evaluate((e) => {
        const r = e.getBoundingClientRect();
        return { x: r.left + window.scrollX, y: r.top + window.scrollY, w: r.width, h: r.height };
      });
      const x = Math.floor(box.x);
      const y = Math.floor(box.y);
      let w = Math.ceil(box.x + box.w) - x;
      let h = Math.ceil(box.y + box.h) - y;
      if (w <= 0 || h <= 0) return { kind: "error", message: `${rootSelector} has no size (${box.w}x${box.h})`, meta: meta() };
      const maxW = cap.maxWidth ?? vp.w;
      const maxH = cap.maxHeight ?? vp.h * 4;
      if (w > maxW) {
        warnings.push(`content ${w}px wide, cut at ${maxW}px (set maxWidth)`);
        w = maxW;
      }
      if (h > maxH) {
        warnings.push(`content ${h}px high, cut at ${maxH}px (set maxHeight)`);
        h = maxH;
      }
      origin = { x, y, w, h };
      png = await page.screenshot({ ...shot, clip: { x, y, width: w, height: h }, fullPage: true });
    } else {
      origin = { x: 0, y: 0, w: vp.w, h: vp.h };
      png = await page.screenshot(shot);
    }
    const regions = await regionsOf(page, origin, vp.density);
    if (errors.length > 0) return { kind: "error", message: errors.join("; "), meta: meta(regions) };
    return { kind: "image", png, meta: meta(regions) };
  } catch (e) {
    errors.push((e as Error).message.split("\n")[0]!);
    return { kind: "error", message: errors.join("; "), meta: meta() };
  } finally {
    await context.close();
  }
}

/**
 * Waits [frames] frames. On the real clock these are animation frames. With a frame interval
 * the page runs on Playwright's fake clock and each frame advances it by that many ms, so a
 * time-driven animation is captured at `frames * interval` ms (0 = at its start).
 */
async function settle(page: Page, frames: number, interval: number | null): Promise<void> {
  if (interval != null) {
    for (let i = 0; i < frames; i++) await page.clock.runFor(interval);
    return;
  }
  await page.evaluate(
    (n) =>
      new Promise<void>((resolve) => {
        let left = n;
        const tick = () => (left-- <= 0 ? resolve() : requestAnimationFrame(tick));
        tick();
      }),
    frames,
  );
}

/** `[data-vcase-region]` elements, in image pixels relative to the capture. */
async function regionsOf(page: Page, origin: Box, density: number): Promise<CaptureMeta["regions"]> {
  const found = await page.evaluate(() =>
    [...document.querySelectorAll<HTMLElement>("[data-vcase-region]")].map((e) => {
      const r = e.getBoundingClientRect();
      return { name: e.dataset.vcaseRegion ?? "", x: r.left + window.scrollX, y: r.top + window.scrollY, w: r.width, h: r.height };
    }),
  );
  const out: CaptureMeta["regions"] = {};
  for (const r of found) {
    if (!r.name) continue;
    out[r.name] = [Math.round((r.x - origin.x) * density), Math.round((r.y - origin.y) * density), Math.round(r.w * density), Math.round(r.h * density)];
  }
  return out;
}
