/**
 * The `vcase/1` manifest: the same model and the same JSON as vcase-core's `Manifest.kt`, so a
 * manifest written here is byte-identical to the one the Kotlin side would write for the same
 * cases, and either side reads the other's.
 *
 * JSON rules mirrored from `VCaseJson` (kotlinx.serialization, `prettyPrint`, indent 2,
 * `encodeDefaults`, `explicitNulls`, `ignoreUnknownKeys = false`):
 * - fields in declaration order; `fixture: null`, empty `axes` / `parity` / `divergences` written;
 * - fields marked `EncodeDefault.NEVER` are omitted while at their default
 *   (`fontScale == [1.0]`, `optIn == false`, `sizeClass == null`, `formerly == []`,
 *   `needsDevice == false`, `capture == null`, null capture and divergence fields);
 * - Kotlin `Float`s (`density`, `fontScale`) are written with a fraction (`2.0`);
 * - unknown keys are an error; the file ends with a newline.
 */

export const SCHEMA = "vcase/1";
export const MANIFEST_FILE = "vcase.manifest.json";
export const KIND_COMPONENT = "component";
export const KIND_SCREEN = "screen";

export interface ManifestViewport {
  name: string;
  w: number;
  h: number;
  density: number;
  optIn?: boolean;
  sizeClass?: string | null;
}

export interface ManifestDefaults {
  theme: string[];
  viewport: ManifestViewport[];
  locale: string[];
  pointer: string[];
  /** Defaults to `[1]`. */
  fontScale?: number[];
}

export interface ManifestCapture {
  maxWidth?: number | null;
  maxHeight?: number | null;
  settleFrames?: number | null;
  frameIntervalMs?: number | null;
}

export interface ManifestDivergence {
  with: string;
  region?: string | null;
  kind: string;
  why: string;
  decidedBy?: string | null;
  ref?: string | null;
  expires?: string | null;
}

export interface ManifestCase {
  id: string;
  kind: string;
  source: string;
  fixture: string | null;
  axes: Record<string, string[]>;
  parity: string[];
  divergences: ManifestDivergence[];
  formerly?: string[];
  needsDevice?: boolean;
  capture?: ManifestCapture | null;
}

export interface VCaseManifest {
  schema: string;
  project: string;
  defaults: ManifestDefaults;
  cases: ManifestCase[];
}

export function fontScales(d: ManifestDefaults): number[] {
  return d.fontScale ?? [1];
}

export function defaultViewports(d: ManifestDefaults): ManifestViewport[] {
  return d.viewport.filter((v) => !v.optIn);
}

/** Cases sorted by id, the canonical order a scan writes (Kotlin `sortedBy { it.id }`, UTF-16 order). */
export function sorted(m: VCaseManifest): VCaseManifest {
  return { ...m, cases: [...m.cases].sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0)) };
}

// ---------------------------------------------------------------------------------------------
// Encoding

/** A number written the way Kotlin writes a Float: always with a fraction. */
class KFloat {
  constructor(readonly value: number) {}
}

type Json = string | number | boolean | null | KFloat | Json[] | { [k: string]: Json };

/** Kotlin `Float.toString()` for the values manifests use (`2.0`, `1.3`, `2.625`). */
export function formatFloat(v: number): string {
  if (!Number.isFinite(v)) throw new Error(`not a finite number: ${v}`);
  return Number.isInteger(v) ? v.toFixed(1) : String(v);
}

function print(value: Json, indent: string): string {
  if (value instanceof KFloat) return formatFloat(value.value);
  if (value === null || typeof value !== "object") return JSON.stringify(value);
  const inner = indent + "  ";
  if (Array.isArray(value)) {
    if (value.length === 0) return "[]";
    return "[\n" + value.map((v) => inner + print(v, inner)).join(",\n") + "\n" + indent + "]";
  }
  const entries = Object.entries(value);
  if (entries.length === 0) return "{}";
  return "{\n" + entries.map(([k, v]) => inner + JSON.stringify(k) + ": " + print(v, inner)).join(",\n") + "\n" + indent + "}";
}

function viewportJson(v: ManifestViewport): Json {
  const o: Record<string, Json> = { name: v.name, w: v.w, h: v.h, density: new KFloat(v.density) };
  if (v.optIn) o.optIn = true;
  if (v.sizeClass != null) o.sizeClass = v.sizeClass;
  return o;
}

function defaultsJson(d: ManifestDefaults): Json {
  const o: Record<string, Json> = {
    theme: d.theme,
    viewport: d.viewport.map(viewportJson),
    locale: d.locale,
    pointer: d.pointer,
  };
  const scales = fontScales(d);
  if (!(scales.length === 1 && scales[0] === 1)) o.fontScale = scales.map((s) => new KFloat(s));
  return o;
}

function divergenceJson(d: ManifestDivergence): Json {
  const o: Record<string, Json> = { with: d.with };
  if (d.region != null) o.region = d.region;
  o.kind = d.kind;
  o.why = d.why;
  if (d.decidedBy != null) o.decidedBy = d.decidedBy;
  if (d.ref != null) o.ref = d.ref;
  if (d.expires != null) o.expires = d.expires;
  return o;
}

function captureJson(c: ManifestCapture): Json {
  const o: Record<string, Json> = {};
  for (const k of CAPTURE_FIELDS) if (c[k] != null) o[k] = c[k]!;
  return o;
}

function caseJson(c: ManifestCase): Json {
  const o: Record<string, Json> = {
    id: c.id,
    kind: c.kind,
    source: c.source,
    fixture: c.fixture,
    axes: c.axes,
    parity: c.parity,
    divergences: c.divergences.map(divergenceJson),
  };
  if (c.formerly && c.formerly.length > 0) o.formerly = c.formerly;
  if (c.needsDevice) o.needsDevice = true;
  if (c.capture) o.capture = captureJson(c.capture);
  return o;
}

export function encodeManifest(m: VCaseManifest): string {
  const o: Json = { schema: m.schema, project: m.project, defaults: defaultsJson(m.defaults), cases: m.cases.map(caseJson) };
  return print(o, "") + "\n";
}

// ---------------------------------------------------------------------------------------------
// Decoding (strict, like `ignoreUnknownKeys = false`)

const CAPTURE_FIELDS = ["maxWidth", "maxHeight", "settleFrames", "frameIntervalMs"] as const;

class Reader {
  constructor(readonly path: string) {}
  fail(msg: string): never {
    throw new Error(`manifest ${this.path}: ${msg}`);
  }
}

function obj(v: unknown, at: string, allowed: readonly string[], r: Reader): Record<string, unknown> {
  if (v === null || typeof v !== "object" || Array.isArray(v)) r.fail(`${at} must be an object`);
  const o = v as Record<string, unknown>;
  for (const k of Object.keys(o)) if (!allowed.includes(k)) r.fail(`${at}: unknown key '${k}'`);
  return o;
}

function str(v: unknown, at: string, r: Reader): string {
  if (typeof v !== "string") r.fail(`${at} must be a string`);
  return v as string;
}

function optStr(v: unknown, at: string, r: Reader): string | null {
  return v === undefined || v === null ? null : str(v, at, r);
}

function num(v: unknown, at: string, r: Reader, int = false): number {
  if (typeof v !== "number" || (int && !Number.isInteger(v))) r.fail(`${at} must be ${int ? "an integer" : "a number"}`);
  return v as number;
}

function optInt(v: unknown, at: string, r: Reader): number | null {
  return v === undefined || v === null ? null : num(v, at, r, true);
}

function strList(v: unknown, at: string, r: Reader): string[] {
  if (!Array.isArray(v)) r.fail(`${at} must be an array`);
  return (v as unknown[]).map((x, i) => str(x, `${at}[${i}]`, r));
}

export function decodeManifest(text: string, path = "<text>"): VCaseManifest {
  const r = new Reader(path);
  let raw: unknown;
  try {
    raw = JSON.parse(text);
  } catch (e) {
    r.fail(`not JSON: ${(e as Error).message}`);
  }
  const m = obj(raw, "root", ["schema", "project", "defaults", "cases"], r);
  const schema = m.schema === undefined ? SCHEMA : str(m.schema, "schema", r);
  if (schema !== SCHEMA) r.fail(`unsupported manifest schema '${schema}' (expected ${SCHEMA})`);
  const d = obj(m.defaults, "defaults", ["theme", "viewport", "locale", "pointer", "fontScale"], r);
  if (!Array.isArray(d.viewport)) r.fail("defaults.viewport must be an array");
  const defaults: ManifestDefaults = {
    theme: strList(d.theme, "defaults.theme", r),
    viewport: (d.viewport as unknown[]).map((v, i) => {
      const at = `defaults.viewport[${i}]`;
      const o = obj(v, at, ["name", "w", "h", "density", "optIn", "sizeClass"], r);
      const vp: ManifestViewport = {
        name: str(o.name, `${at}.name`, r),
        w: num(o.w, `${at}.w`, r, true),
        h: num(o.h, `${at}.h`, r, true),
        density: num(o.density, `${at}.density`, r),
      };
      if (o.optIn !== undefined) {
        if (typeof o.optIn !== "boolean") r.fail(`${at}.optIn must be a boolean`);
        if (o.optIn) vp.optIn = true;
      }
      const sc = optStr(o.sizeClass, `${at}.sizeClass`, r);
      if (sc != null) vp.sizeClass = sc;
      return vp;
    }),
    locale: strList(d.locale, "defaults.locale", r),
    pointer: strList(d.pointer, "defaults.pointer", r),
  };
  if (d.fontScale !== undefined) {
    if (!Array.isArray(d.fontScale)) r.fail("defaults.fontScale must be an array");
    defaults.fontScale = (d.fontScale as unknown[]).map((x, i) => num(x, `defaults.fontScale[${i}]`, r));
  }
  if (!Array.isArray(m.cases)) r.fail("cases must be an array");
  const cases = (m.cases as unknown[]).map((v, i) => decodeCase(v, `cases[${i}]`, r));
  return { schema, project: str(m.project, "project", r), defaults, cases };
}

function decodeCase(v: unknown, at: string, r: Reader): ManifestCase {
  const o = obj(v, at, ["id", "kind", "source", "fixture", "axes", "parity", "divergences", "formerly", "needsDevice", "capture"], r);
  const axes: Record<string, string[]> = {};
  if (o.axes !== undefined) {
    const a = obj(o.axes, `${at}.axes`, Object.keys(o.axes ?? {}), r);
    for (const [k, vals] of Object.entries(a)) axes[k] = strList(vals, `${at}.axes.${k}`, r);
  }
  const c: ManifestCase = {
    id: str(o.id, `${at}.id`, r),
    kind: o.kind === undefined ? KIND_COMPONENT : str(o.kind, `${at}.kind`, r),
    source: str(o.source, `${at}.source`, r),
    fixture: optStr(o.fixture, `${at}.fixture`, r),
    axes,
    parity: o.parity === undefined ? [] : strList(o.parity, `${at}.parity`, r),
    divergences: [],
  };
  if (o.divergences !== undefined) {
    if (!Array.isArray(o.divergences)) r.fail(`${at}.divergences must be an array`);
    c.divergences = (o.divergences as unknown[]).map((dv, j) => {
      const dat = `${at}.divergences[${j}]`;
      const d = obj(dv, dat, ["with", "region", "kind", "why", "decidedBy", "ref", "expires"], r);
      const out: ManifestDivergence = { with: str(d.with, `${dat}.with`, r), kind: str(d.kind, `${dat}.kind`, r), why: str(d.why, `${dat}.why`, r) };
      for (const k of ["region", "decidedBy", "ref", "expires"] as const) {
        const s = optStr(d[k], `${dat}.${k}`, r);
        if (s != null) out[k] = s;
      }
      return out;
    });
  }
  if (o.formerly !== undefined) {
    const f = strList(o.formerly, `${at}.formerly`, r);
    if (f.length > 0) c.formerly = f;
  }
  if (o.needsDevice !== undefined) {
    if (typeof o.needsDevice !== "boolean") r.fail(`${at}.needsDevice must be a boolean`);
    if (o.needsDevice) c.needsDevice = true;
  }
  if (o.capture !== undefined && o.capture !== null) {
    const cap = obj(o.capture, `${at}.capture`, CAPTURE_FIELDS, r);
    const out: ManifestCapture = {};
    for (const k of CAPTURE_FIELDS) {
      const n = optInt(cap[k], `${at}.capture.${k}`, r);
      if (n != null) out[k] = n;
    }
    c.capture = out;
  }
  return c;
}

// ---------------------------------------------------------------------------------------------
// Ids (Kotlin `VCaseIds`)

const ID = /^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+$/;
const PLATFORM = /^[a-z][a-z0-9-]*$/;

export function idProblem(id: string): string | null {
  if (id.length === 0) return "id is empty";
  if (id !== id.toLowerCase()) return `id '${id}' must be lowercase`;
  if (!id.includes(".")) return `id '${id}' needs at least two dot-separated segments (layer.domain.name)`;
  if (!ID.test(id)) return `id '${id}' must be dot-separated segments of [a-z][a-z0-9-]*`;
  return null;
}

export function parityProblem(ref: string): string | null {
  const i = ref.indexOf(":");
  const platform = i < 0 ? "" : ref.slice(0, i);
  const id = i < 0 ? "" : ref.slice(i + 1);
  if (!platform || !id) return `parity '${ref}' must be platform:id`;
  if (!PLATFORM.test(platform)) return `parity '${ref}': platform must be lowercase`;
  const p = idProblem(id);
  return p ? `parity '${ref}': ${p}` : null;
}

// ---------------------------------------------------------------------------------------------
// Diff (Kotlin `ManifestDiff`)

export interface ManifestDiff {
  added: string[];
  removed: string[];
  changed: Record<string, string[]>;
  header: string[];
  /** Cases whose `source` only moved within the same file: a note, never a failure. */
  moved: string[];
}

export function diffIsEmpty(d: ManifestDiff): boolean {
  return d.added.length === 0 && d.removed.length === 0 && Object.keys(d.changed).length === 0 && d.header.length === 0;
}

export function describeDiff(d: ManifestDiff): string {
  if (diffIsEmpty(d)) return "manifest matches the scan";
  const lines = ["manifest differs from the scan:"];
  d.header.forEach((h) => lines.push(`  ~ ${h}`));
  d.added.forEach((h) => lines.push(`  + ${h}`));
  d.removed.forEach((h) => lines.push(`  - ${h}`));
  Object.entries(d.changed).forEach(([id, f]) => lines.push(`  ~ ${id} (${f.join(", ")})`));
  return lines.join("\n");
}

export function sourceFile(source: string): string {
  const i = source.lastIndexOf(":");
  if (i < 0) return source;
  const line = source.slice(i + 1);
  return line.length > 0 && /^\d+$/.test(line) ? source.slice(0, i) : source;
}

/** Order-insensitive for object keys, order-sensitive for arrays (Kotlin data class equality). */
export function deepEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (typeof a !== "object" || typeof b !== "object" || a === null || b === null) return false;
  if (Array.isArray(a) !== Array.isArray(b)) return false;
  if (Array.isArray(a)) {
    const bb = b as unknown[];
    return a.length === bb.length && a.every((x, i) => deepEqual(x, bb[i]));
  }
  const ao = stripUndef(a as Record<string, unknown>);
  const bo = stripUndef(b as Record<string, unknown>);
  const ak = Object.keys(ao);
  return ak.length === Object.keys(bo).length && ak.every((k) => k in bo && deepEqual(ao[k], bo[k]));
}

function stripUndef(o: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(Object.entries(o).filter(([, v]) => v !== undefined && v !== null));
}

function normDefaults(d: ManifestDefaults): unknown {
  return { ...d, fontScale: fontScales(d), viewport: d.viewport.map((v) => ({ ...v, optIn: v.optIn ?? false, sizeClass: v.sizeClass ?? null })) };
}

export function diffManifests(committed: VCaseManifest, scanned: VCaseManifest): ManifestDiff {
  const header: string[] = [];
  if (committed.schema !== scanned.schema) header.push("schema");
  if (committed.project !== scanned.project) header.push("project");
  if (!deepEqual(normDefaults(committed.defaults), normDefaults(scanned.defaults))) header.push("defaults");
  const old = new Map(committed.cases.map((c) => [c.id, c]));
  const neu = new Map(scanned.cases.map((c) => [c.id, c]));
  const changed: Record<string, string[]> = {};
  const moved: string[] = [];
  for (const id of [...old.keys()].filter((k) => neu.has(k)).sort()) {
    const a = old.get(id)!;
    const b = neu.get(id)!;
    const fields: string[] = [];
    if (a.kind !== b.kind) fields.push("kind");
    if (sourceFile(a.source) !== sourceFile(b.source)) fields.push("source");
    if (a.fixture !== b.fixture) fields.push("fixture");
    if (!deepEqual(a.axes, b.axes)) fields.push("axes");
    if (!deepEqual(a.parity, b.parity)) fields.push("parity");
    if (!deepEqual(a.divergences, b.divergences)) fields.push("divergences");
    if (!deepEqual(a.formerly ?? [], b.formerly ?? [])) fields.push("formerly");
    if ((a.needsDevice ?? false) !== (b.needsDevice ?? false)) fields.push("needsDevice");
    if (!deepEqual(a.capture ?? null, b.capture ?? null)) fields.push("capture");
    if (fields.length > 0) changed[id] = fields;
    else if (a.source !== b.source) moved.push(id);
  }
  return {
    added: [...neu.keys()].filter((k) => !old.has(k)).sort(),
    removed: [...old.keys()].filter((k) => !neu.has(k)).sort(),
    changed,
    header,
    moved,
  };
}
