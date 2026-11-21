import {
  defaultViewports,
  fontScales,
  idProblem,
  KIND_COMPONENT,
  KIND_SCREEN,
  parityProblem,
  type ManifestCapture,
  type ManifestCase,
  type ManifestDefaults,
  type ManifestViewport,
  type VCaseManifest,
  SCHEMA,
} from "./manifest.js";

/** Axis names in file-name order (Kotlin `VCaseAxis.ORDER`). */
export const AXES = ["theme", "viewport", "locale", "pointer", "fontScale"] as const;
export const POINTERS = ["touch", "fine"] as const;

export interface VariantAxes {
  theme: string;
  viewport: ManifestViewport;
  locale: string;
  pointer: string;
  fontScale: number;
}

/** One capture: a case at one point of its axis space. */
export interface VCaseVariant {
  case: ManifestCase;
  axes: VariantAxes;
  /** Comma-joined axis values: `dark,phone` or `light,phone,fine`. */
  name: string;
  /** `<id>@<name>`: the capture is `<stem>.png`, its metadata `<stem>.json`. */
  stem: string;
}

/** `1` -> `1`, `1.3` -> `1.3` (Kotlin `AxisExpansion.formatScale`). */
export function formatScale(scale: number): string {
  return String(scale);
}

export function axisMap(v: VCaseVariant): Record<string, string> {
  return {
    theme: v.axes.theme,
    viewport: v.axes.viewport.name,
    locale: v.axes.locale,
    pointer: v.axes.pointer,
    fontScale: formatScale(v.axes.fontScale),
  };
}

/**
 * The cartesian product of a case's effective axis values (override, else default), in
 * {@link AXES} order. Theme and viewport are always in the name; locale, pointer and font scale
 * only when the case overrides that axis or the default lists more than one value.
 */
export function expandCase(c: ManifestCase, d: ManifestDefaults): VCaseVariant[] {
  const unknown = Object.keys(c.axes).filter((k) => !(AXES as readonly string[]).includes(k));
  if (unknown.length > 0) throw new Error(`${c.id}: unknown axes [${unknown.join(", ")}] (known: [${AXES.join(", ")}])`);
  const themes = c.axes.theme ?? d.theme;
  const viewports = (c.axes.viewport ?? defaultViewports(d).map((v) => v.name)).map((name) => {
    const v = d.viewport.find((x) => x.name === name);
    if (!v) throw new Error(`${c.id}: unknown viewport '${name}'`);
    return v;
  });
  const locales = c.axes.locale ?? d.locale;
  const pointers = (c.axes.pointer ?? d.pointer).map((p) => {
    if (!(POINTERS as readonly string[]).includes(p)) throw new Error(`${c.id}: unknown pointer '${p}'`);
    return p;
  });
  const scales =
    c.axes.fontScale?.map((s) => {
      const n = Number(s);
      if (s.trim() === "" || !Number.isFinite(n)) throw new Error(`${c.id}: font scale '${s}' is not a number`);
      return n;
    }) ?? fontScales(d);
  for (const [axis, values] of [
    ["theme", themes],
    ["viewport", viewports],
    ["locale", locales],
    ["pointer", pointers],
    ["fontScale", scales],
  ] as const) {
    if (values.length === 0) throw new Error(`${c.id}: axis '${axis}' has no values`);
  }
  const showLocale = "locale" in c.axes || d.locale.length > 1;
  const showPointer = "pointer" in c.axes || d.pointer.length > 1;
  const showScale = "fontScale" in c.axes || fontScales(d).length > 1;

  const out: VCaseVariant[] = [];
  for (const theme of themes)
    for (const viewport of viewports)
      for (const locale of locales)
        for (const pointer of pointers)
          for (const fontScale of scales) {
            const parts = [theme, viewport.name];
            if (showLocale) parts.push(locale);
            if (showPointer) parts.push(pointer);
            if (showScale) parts.push("fs" + formatScale(fontScale));
            const name = parts.join(",");
            out.push({ case: c, axes: { theme, viewport, locale, pointer, fontScale }, name, stem: `${c.id}@${name}` });
          }
  const seen = new Set<string>();
  const dup = new Set<string>();
  for (const v of out) (seen.has(v.name) ? dup : seen).add(v.name);
  if (dup.size > 0) throw new Error(`${c.id}: duplicate axis values give the same capture name [${[...dup].join(", ")}]`);
  return out;
}

export function expandManifest(m: VCaseManifest): VCaseVariant[] {
  return m.cases.flatMap((c) => expandCase(c, m.defaults));
}

/** Kotlin `ManifestValidation.problems`. */
export function manifestProblems(m: VCaseManifest): string[] {
  const out: string[] = [];
  if (m.schema !== SCHEMA) out.push(`schema is '${m.schema}', expected '${SCHEMA}'`);
  if (m.project.trim() === "") out.push("project is empty");
  const d = m.defaults;
  if (d.theme.length === 0) out.push("defaults.theme is empty");
  if (defaultViewports(d).length === 0) out.push("defaults.viewport has no viewport that is not optIn");
  if (d.locale.length === 0) out.push("defaults.locale is empty");
  if (d.pointer.length === 0) out.push("defaults.pointer is empty");
  dupes(d.viewport.map((v) => v.name)).forEach((n) => out.push(`viewport '${n}' declared twice`));
  d.viewport.filter((v) => v.w <= 0 || v.h <= 0 || v.density <= 0).forEach((v) => out.push(`viewport '${v.name}' must have positive w, h, density`));
  d.viewport.filter((v) => v.sizeClass != null && v.sizeClass.trim() === "").forEach((v) => out.push(`viewport '${v.name}': sizeClass is blank (omit it instead)`));

  const byId = new Map<string, ManifestCase[]>();
  for (const c of m.cases) byId.set(c.id, [...(byId.get(c.id) ?? []), c]);
  for (const [id, list] of byId) if (list.length > 1) out.push(`id '${id}' declared ${list.length} times: ${list.map((c) => c.source).join(", ")}`);
  const all = new Set(m.cases.map((c) => c.id));
  for (const c of m.cases) {
    const p = idProblem(c.id);
    if (p) out.push(`${c.source}: ${p}`);
    if (c.kind !== KIND_COMPONENT && c.kind !== KIND_SCREEN) out.push(`${c.id}: kind '${c.kind}' must be component or screen`);
    c.parity.forEach((ref) => {
      const pp = parityProblem(ref);
      if (pp) out.push(`${c.id}: ${pp}`);
    });
    (c.formerly ?? []).forEach((old) => {
      const fp = idProblem(old);
      if (fp) out.push(`${c.id}: formerly ${fp}`);
      if (all.has(old)) out.push(`${c.id}: formerly '${old}' is still a live case id`);
    });
    c.divergences.forEach((dv) => {
      if (!c.parity.some((p) => p.split(":")[0] === dv.with)) out.push(`${c.id}: divergence with '${dv.with}' has no parity entry for that platform`);
      if (dv.why.trim() === "") out.push(`${c.id}: divergence with '${dv.with}' needs a why`);
    });
    if (c.capture) out.push(...captureProblems(c, c.capture));
    try {
      expandCase(c, d);
    } catch (e) {
      out.push((e as Error).message);
    }
  }
  return out;
}

function captureProblems(c: ManifestCase, cap: ManifestCapture): string[] {
  const out: string[] = [];
  if (cap.maxWidth == null && cap.maxHeight == null && cap.settleFrames == null && cap.frameIntervalMs == null) out.push(`${c.id}: capture is empty (omit it instead)`);
  if (cap.maxWidth != null && cap.maxWidth <= 0) out.push(`${c.id}: capture.maxWidth must be positive, is ${cap.maxWidth}`);
  if (cap.maxHeight != null && cap.maxHeight <= 0) out.push(`${c.id}: capture.maxHeight must be positive, is ${cap.maxHeight}`);
  if (cap.settleFrames != null && cap.settleFrames < 0) out.push(`${c.id}: capture.settleFrames must be 0 or more, is ${cap.settleFrames}`);
  if (cap.frameIntervalMs != null && cap.frameIntervalMs < 0) out.push(`${c.id}: capture.frameIntervalMs must be 0 or more, is ${cap.frameIntervalMs}`);
  if (c.kind === KIND_SCREEN && (cap.maxWidth != null || cap.maxHeight != null)) {
    out.push(`${c.id}: maxWidth / maxHeight bound a component's content size; a screen is captured at its viewport`);
  }
  return out;
}

function dupes(xs: string[]): string[] {
  const seen = new Set<string>();
  const d = new Set<string>();
  for (const x of xs) (seen.has(x) ? d : seen).add(x);
  return [...d];
}

export function requireValid(m: VCaseManifest): void {
  const p = manifestProblems(m);
  if (p.length > 0) throw new Error("invalid vcase manifest:\n" + p.map((x) => `  - ${x}`).join("\n"));
}
