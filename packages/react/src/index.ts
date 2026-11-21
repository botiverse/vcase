/**
 * `@vcase/react`: what case files and the host page import. Browser-safe, no dependencies.
 * The scanner, renderer and CLI live in `@vcase/react/node`.
 */

/** A decided difference between this case and its parity counterpart on platform [with]. */
export interface VCaseDivergence {
  /** Platform of the parity entry this divergence belongs to, e.g. `mobile`. */
  with: string;
  /** Region name the adapters report (`data-vcase-region`), or omitted for the whole image. */
  region?: string;
  /** e.g. `platform-difference` (the default), `pending-fix`. */
  kind?: string;
  /** One line, readable by a reviewer. */
  why: string;
  decidedBy?: string;
  /** Where it was decided (task, PR, thread). */
  ref?: string;
  /** ISO date after which the report flags it. */
  expires?: string;
}

export type VCaseKind = "component" | "screen";
export type VCasePointer = "touch" | "fine";

/**
 * The declaration of one case, the React form of Kotlin's `@VCase`. Field names and meaning
 * are the same; a field left out uses the project default (axes) or the host's value
 * (capture settings).
 *
 * The scanner reads it statically, so every value must be a literal (strings, numbers,
 * booleans, arrays and objects of literals). Computed values are a scan error.
 */
export interface VCaseMeta {
  /** `layer.domain.name`: lowercase dot-separated segments, at least two. Never names a platform. */
  id: string;
  /** `component` (default) is captured at its element's size, `screen` at the viewport. */
  kind?: VCaseKind;
  /** Name of the data set the case renders, e.g. `members.channel(count=24, preset=long-names)`. */
  fixture?: string;
  /** Cases on other platforms this one must look like, `platform:id`, e.g. `mobile:ds.button.variants`. */
  parity?: string[];
  theme?: string[];
  /** Viewport names declared in the project defaults. */
  viewport?: string[];
  /** BCP 47 tags. */
  locale?: string[];
  pointer?: VCasePointer[];
  fontScale?: number[];
  /** Previous ids, so goldens move with a rename. */
  formerly?: string[];
  divergences?: VCaseDivergence[];
  /** Only renderable on a device; the web adapter skips it. */
  needsDevice?: boolean;
  /** Width bound in CSS px of a component capture (default: the viewport width). */
  maxWidth?: number;
  /** Height bound in CSS px of a component capture (default: 4x the viewport height). */
  maxHeight?: number;
  /** Animation frames waited before the capture (default: the config's `settleFrames`). */
  settleFrames?: number;
  /**
   * With a value, the page runs on a paused fake clock: it loads and first renders at
   * `performance.now() === 0` (`Date.now()` = the config's `clock`), however long loading takes,
   * and each settle frame advances it by exactly this many ms, so the capture is at
   * `settleFrames * frameIntervalMs`. `requestAnimationFrame` fires at every multiple of 16 ms
   * of `performance.now()`, with that value as its timestamp. `0` keeps time-driven animations
   * at their start.
   */
  frameIntervalMs?: number;
}

/** Declares a case: `export const meta = vcase({ id: "ui.button.variants" })`. Returns its argument. */
export function vcase<const M extends VCaseMeta>(meta: M): M {
  return meta;
}

/** Props of a case component that renders a fixture (phase 2: fixtures are not generated yet). */
export interface CaseProps<T> {
  data: T;
}

/** Marks the element the renderer captures for a component case. */
export const ROOT_ATTRIBUTE = "data-vcase-root";
/** Set on `<html>` by the host page once the case has rendered (see `markReady`). */
export const READY_ATTRIBUTE = "data-vcase-ready";
/** Names a region inside the root; its bounds go into the capture metadata. */
export const REGION_ATTRIBUTE = "data-vcase-region";

/** The variant the renderer asked for, as the host page reads it from its URL. */
export interface VCaseVariantParams {
  /** Case key (by default the case file's path without extension, relative to its glob base). */
  key: string;
  /** Export to render: `default` for `*.cases.tsx`, the story name for stories. */
  exportName: string;
  id: string;
  theme: string;
  viewport: string;
  locale: string;
  pointer: VCasePointer;
  fontScale: number;
  sizeClass: string | null;
  fixture: string | null;
}

/**
 * Reads the variant from the query string the default URL template writes
 * (`?case=…&export=…&id=…&theme=…&viewport=…&locale=…&pointer=…&fontScale=…&sizeClass=…&fixture=…`).
 * Null when the page was not opened by vcase (no `case` parameter).
 */
export function readVariant(search: string = globalThis.location?.search ?? ""): VCaseVariantParams | null {
  const p = new URLSearchParams(search);
  const key = p.get("case");
  if (!key) return null;
  return {
    key,
    exportName: p.get("export") || "default",
    id: p.get("id") ?? "",
    theme: p.get("theme") ?? "light",
    viewport: p.get("viewport") ?? "",
    locale: p.get("locale") ?? "en",
    pointer: p.get("pointer") === "fine" ? "fine" : "touch",
    fontScale: Number(p.get("fontScale") ?? "1") || 1,
    sizeClass: p.get("sizeClass") || null,
    fixture: p.get("fixture") || null,
  };
}

/**
 * Tells the renderer the case is mounted: sets `data-vcase-ready` on `<html>`. Call it from an
 * effect of the case's wrapper once the case module has loaded and rendered. The renderer still
 * waits for fonts, images, network idle and the settle frames after it.
 */
export function markReady(): void {
  document.documentElement.setAttribute(READY_ATTRIBUTE, "");
}

/** Tells the renderer the case failed to load or render; the variant becomes an error. */
export function markError(message: string): void {
  document.documentElement.setAttribute(READY_ATTRIBUTE, "");
  document.documentElement.setAttribute("data-vcase-error", message);
}
