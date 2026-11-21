import { readFileSync, existsSync } from "node:fs";
import path from "node:path";
import ts from "typescript";
import { globSync } from "tinyglobby";
import { formatScale } from "./axes.js";
import { KIND_COMPONENT, KIND_SCREEN, sorted, type ManifestCapture, type ManifestCase, type ManifestDefaults, type ManifestDivergence, type VCaseManifest, SCHEMA } from "./manifest.js";

/**
 * Where cases live. `cases` files hold one case each: `export const meta = vcase({...})` next
 * to the component the host renders (`export default`, or the file's own convention, e.g.
 * raft-design's `*Composition` exports). `stories` files are Storybook CSF: every named story
 * export is a case.
 */
export interface SourceSpec {
  /** Globs relative to the config directory. */
  glob: string | string[];
  /** Default: `stories` for `*.stories.*` files, else `cases`. */
  format?: "cases" | "stories";
  /** Directory the case key is relative to (default: the config directory). */
  base?: string;
  /** Suffix cut from the key (default: the file extension, e.g. `.tsx`). */
  suffix?: string;
  /**
   * Stories only: scan every story, deriving its id from `title` and the export name
   * (`UI/Button` + `PrimaryLarge` -> `ui.button.primary-large`). Default true. With false only
   * stories with `parameters.vcase` (or a file-level one) are cases.
   */
  allStories?: boolean;
}

/** A case the scan found: its manifest entry and how the renderer addresses it. */
export interface ScannedCase {
  entry: ManifestCase;
  /** Absolute path of the file. */
  file: string;
  /** The case key, `{key}` in the URL template. */
  key: string;
  /** `default`, a story export name, or null when the file has no default export. */
  exportName: string | null;
  /** Storybook story id (`ui-button--primary`), `{storyId}` in the URL template. */
  storyId?: string;
}

export interface ScanResult {
  cases: ScannedCase[];
  /** Files that matched a glob but declare no case. */
  skipped: string[];
  problems: string[];
}

export interface ScanOptions {
  /** Directory globs and bases are relative to. */
  cwd: string;
  sources: SourceSpec[];
  /** Directory `source` paths are relative to (default: the git root above cwd, else cwd). */
  sourceRoot?: string;
}

class ScanError extends Error {
  constructor(
    readonly node: ts.Node,
    message: string,
  ) {
    super(message);
  }
}

/** Finds the enclosing git root (a `.git` directory or worktree file). */
export function gitRoot(from: string): string | null {
  let dir = path.resolve(from);
  for (;;) {
    if (existsSync(path.join(dir, ".git"))) return dir;
    const up = path.dirname(dir);
    if (up === dir) return null;
    dir = up;
  }
}

const toPosix = (p: string) => p.split(path.sep).join("/");

export function scan(opts: ScanOptions): ScanResult {
  const sourceRoot = path.resolve(opts.cwd, opts.sourceRoot ?? gitRoot(opts.cwd) ?? opts.cwd);
  const out: ScanResult = { cases: [], skipped: [], problems: [] };
  const seen = new Set<string>();
  for (const spec of opts.sources) {
    const globs = Array.isArray(spec.glob) ? spec.glob : [spec.glob];
    const files = globSync(globs, { cwd: opts.cwd, absolute: true, ignore: ["**/node_modules/**", "**/dist/**"] }).sort();
    for (const file of files) {
      if (seen.has(file)) continue;
      seen.add(file);
      const format = spec.format ?? (/\.stories\.[cm]?[jt]sx?$/.test(file) ? "stories" : "cases");
      const rel = toPosix(path.relative(sourceRoot, file));
      const key = keyOf(file, opts.cwd, spec);
      try {
        const text = readFileSync(file, "utf8");
        const found = format === "stories" ? scanStories(text, file, rel, key, spec.allStories ?? true, out.problems) : scanCaseFile(text, file, rel, key, out.problems);
        if (found.length === 0) out.skipped.push(rel);
        out.cases.push(...found);
      } catch (e) {
        out.problems.push(`${rel}: ${(e as Error).message}`);
      }
    }
  }
  out.cases.sort((a, b) => (a.entry.id < b.entry.id ? -1 : a.entry.id > b.entry.id ? 1 : 0));
  return out;
}

export function keyOf(file: string, cwd: string, spec: Pick<SourceSpec, "base" | "suffix">): string {
  const rel = toPosix(path.relative(path.resolve(cwd, spec.base ?? "."), file));
  const suffix = spec.suffix ?? path.extname(file);
  return rel.endsWith(suffix) ? rel.slice(0, rel.length - suffix.length) : rel;
}

/** The manifest a scan produces: cases sorted by id. */
export function manifestOf(project: string, defaults: ManifestDefaults, cases: ScannedCase[]): VCaseManifest {
  return sorted({ schema: SCHEMA, project, defaults, cases: cases.map((c) => c.entry) });
}

function parse(text: string, file: string): ts.SourceFile {
  const kind = /\.tsx$/.test(file) ? ts.ScriptKind.TSX : /\.jsx$/.test(file) ? ts.ScriptKind.JSX : /\.ts$/.test(file) ? ts.ScriptKind.TS : ts.ScriptKind.JS;
  return ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, kind);
}

function lineOf(sf: ts.SourceFile, node: ts.Node): number {
  return sf.getLineAndCharacterOfPosition(node.getStart(sf)).line + 1;
}

function isExported(node: ts.Node): boolean {
  return ts.canHaveModifiers(node) && (ts.getModifiers(node) ?? []).some((m) => m.kind === ts.SyntaxKind.ExportKeyword);
}

function hasDefaultExport(sf: ts.SourceFile): boolean {
  return sf.statements.some(
    (s) =>
      ts.isExportAssignment(s) ||
      (ts.canHaveModifiers(s) && (ts.getModifiers(s) ?? []).some((m) => m.kind === ts.SyntaxKind.DefaultKeyword)) ||
      (ts.isExportDeclaration(s) && s.exportClause && ts.isNamedExports(s.exportClause) && s.exportClause.elements.some((e) => e.name.text === "default")),
  );
}

/** `export const <name> = ...` declarations of the file. */
function exportedConsts(sf: ts.SourceFile): Map<string, ts.VariableDeclaration> {
  const out = new Map<string, ts.VariableDeclaration>();
  for (const s of sf.statements) {
    if (ts.isVariableStatement(s) && isExported(s)) {
      for (const d of s.declarationList.declarations) if (ts.isIdentifier(d.name)) out.set(d.name.text, d);
    }
  }
  return out;
}

function unwrap(node: ts.Expression): ts.Expression {
  let n = node;
  while (ts.isAsExpression(n) || ts.isSatisfiesExpression(n) || ts.isParenthesizedExpression(n) || ts.isTypeAssertionExpression(n) || ts.isNonNullExpression(n)) n = n.expression;
  return n;
}

function calleeName(call: ts.CallExpression): string | null {
  const c = call.expression;
  if (ts.isIdentifier(c)) return c.text;
  if (ts.isPropertyAccessExpression(c)) return c.name.text;
  return null;
}

/** `vcase({...})` (any import alias named `vcase`), the object literal inside it. */
function vcaseCall(node: ts.Expression | undefined): ts.Expression | null {
  if (!node) return null;
  const n = unwrap(node);
  if (ts.isCallExpression(n) && calleeName(n) === "vcase") {
    if (n.arguments.length !== 1) throw new ScanError(n, "vcase() takes one object");
    return n.arguments[0]!;
  }
  return null;
}

/** Static value of a literal expression; anything computed is an error. */
export function literal(node: ts.Expression): unknown {
  const n = unwrap(node);
  if (ts.isStringLiteral(n) || ts.isNoSubstitutionTemplateLiteral(n)) return n.text;
  if (ts.isNumericLiteral(n)) return Number(n.text);
  if (ts.isPrefixUnaryExpression(n) && n.operator === ts.SyntaxKind.MinusToken && ts.isNumericLiteral(n.operand)) return -Number(n.operand.text);
  if (n.kind === ts.SyntaxKind.TrueKeyword) return true;
  if (n.kind === ts.SyntaxKind.FalseKeyword) return false;
  if (n.kind === ts.SyntaxKind.NullKeyword) return null;
  if (ts.isIdentifier(n) && n.text === "undefined") return undefined;
  if (ts.isArrayLiteralExpression(n)) return n.elements.map((e) => literal(e));
  if (ts.isObjectLiteralExpression(n)) {
    const o: Record<string, unknown> = {};
    for (const p of n.properties) {
      if (!ts.isPropertyAssignment(p)) throw new ScanError(p, "only `key: literal` properties are read statically");
      const k = p.name;
      const name = ts.isIdentifier(k) || ts.isStringLiteral(k) || ts.isNumericLiteral(k) ? k.text : null;
      if (name === null) throw new ScanError(p, "computed keys are not read statically");
      o[name] = literal(p.initializer);
    }
    return o;
  }
  if (ts.isCallExpression(n) && calleeName(n) === "vcase") return literal(vcaseCall(n)!);
  throw new ScanError(n, `not a literal: \`${n.getText().slice(0, 60)}\` (vcase meta is read without running the file)`);
}

/** A literal that may hold a nested `parameters.vcase`, read leniently: non-literal siblings are ignored. */
function propertyOf(obj: ts.Expression, name: string): ts.Expression | null {
  const n = unwrap(obj);
  if (!ts.isObjectLiteralExpression(n)) return null;
  for (const p of n.properties) {
    if (ts.isPropertyAssignment(p) && (ts.isIdentifier(p.name) || ts.isStringLiteral(p.name)) && p.name.text === name) return p.initializer;
  }
  return null;
}

const META_KEYS = new Set([
  "id",
  "kind",
  "fixture",
  "parity",
  "theme",
  "viewport",
  "locale",
  "pointer",
  "fontScale",
  "formerly",
  "divergences",
  "needsDevice",
  "maxWidth",
  "maxHeight",
  "settleFrames",
  "frameIntervalMs",
]);
const DIVERGENCE_KEYS = new Set(["with", "region", "kind", "why", "decidedBy", "ref", "expires"]);

function strings(v: unknown, field: string): string[] {
  if (v === undefined) return [];
  if (!Array.isArray(v) || v.some((x) => typeof x !== "string")) throw new Error(`${field} must be an array of strings`);
  return v as string[];
}

function optString(v: unknown, field: string): string | null {
  if (v === undefined || v === null || v === "") return null;
  if (typeof v !== "string") throw new Error(`${field} must be a string`);
  return v;
}

function optInt(v: unknown, field: string, unset: number): number | null {
  if (v === undefined || v === null || v === unset) return null;
  if (typeof v !== "number" || !Number.isInteger(v)) throw new Error(`${field} must be an integer`);
  return v;
}

/** The manifest entry for a meta object, field for field what Kotlin's `VCaseScanner.entryOf` writes. */
export function entryOf(meta: Record<string, unknown>, source: string): ManifestCase {
  for (const k of Object.keys(meta)) if (!META_KEYS.has(k)) throw new Error(`unknown vcase meta field '${k}'`);
  if (typeof meta.id !== "string") throw new Error("vcase meta needs an id string");
  const kind = meta.kind ?? KIND_COMPONENT;
  if (kind !== KIND_COMPONENT && kind !== KIND_SCREEN) throw new Error(`kind must be "component" or "screen", is ${JSON.stringify(kind)}`);
  const axes: Record<string, string[]> = {};
  for (const axis of ["theme", "viewport", "locale", "pointer"] as const) {
    const vals = strings(meta[axis], axis);
    if (vals.length > 0) axes[axis] = vals;
  }
  if (meta.fontScale !== undefined) {
    if (!Array.isArray(meta.fontScale) || meta.fontScale.some((x) => typeof x !== "number")) throw new Error("fontScale must be an array of numbers");
    if (meta.fontScale.length > 0) axes.fontScale = (meta.fontScale as number[]).map(formatScale);
  }
  const divs = meta.divergences ?? [];
  if (!Array.isArray(divs)) throw new Error("divergences must be an array");
  const divergences = divs.map((d, i): ManifestDivergence => {
    if (d === null || typeof d !== "object") throw new Error(`divergences[${i}] must be an object`);
    const o = d as Record<string, unknown>;
    for (const k of Object.keys(o)) if (!DIVERGENCE_KEYS.has(k)) throw new Error(`divergences[${i}]: unknown field '${k}'`);
    const w = optString(o.with, `divergences[${i}].with`);
    const why = optString(o.why, `divergences[${i}].why`);
    if (!w) throw new Error(`divergences[${i}] needs \`with\``);
    const out: ManifestDivergence = { with: w, kind: optString(o.kind, `divergences[${i}].kind`) ?? "platform-difference", why: why ?? "" };
    const region = optString(o.region, `divergences[${i}].region`);
    const decidedBy = optString(o.decidedBy, `divergences[${i}].decidedBy`);
    const ref = optString(o.ref, `divergences[${i}].ref`);
    const expires = optString(o.expires, `divergences[${i}].expires`);
    // Same key order as Kotlin's ManifestDivergence.
    const ordered: ManifestDivergence = { with: out.with, ...(region != null ? { region } : {}), kind: out.kind, why: out.why };
    if (decidedBy != null) ordered.decidedBy = decidedBy;
    if (ref != null) ordered.ref = ref;
    if (expires != null) ordered.expires = expires;
    return ordered;
  });
  if (meta.needsDevice !== undefined && typeof meta.needsDevice !== "boolean") throw new Error("needsDevice must be a boolean");
  const capture: ManifestCapture = {};
  const maxWidth = optInt(meta.maxWidth, "maxWidth", 0);
  const maxHeight = optInt(meta.maxHeight, "maxHeight", 0);
  const settleFrames = optInt(meta.settleFrames, "settleFrames", -1);
  const frameIntervalMs = optInt(meta.frameIntervalMs, "frameIntervalMs", -1);
  if (maxWidth != null) capture.maxWidth = maxWidth;
  if (maxHeight != null) capture.maxHeight = maxHeight;
  if (settleFrames != null) capture.settleFrames = settleFrames;
  if (frameIntervalMs != null) capture.frameIntervalMs = frameIntervalMs;
  const entry: ManifestCase = {
    id: meta.id,
    kind: kind as string,
    source,
    fixture: optString(meta.fixture, "fixture"),
    axes,
    parity: strings(meta.parity, "parity"),
    divergences,
  };
  const formerly = strings(meta.formerly, "formerly");
  if (formerly.length > 0) entry.formerly = formerly;
  if (meta.needsDevice === true) entry.needsDevice = true;
  if (Object.keys(capture).length > 0) entry.capture = capture;
  return entry;
}

function wrapErrors<T>(sf: ShapeFile, problems: string[], fn: () => T): T | null {
  try {
    return fn();
  } catch (e) {
    if (e instanceof ScanError) problems.push(`${sf.rel}:${lineOf(sf.sf, e.node)}: ${e.message}`);
    else problems.push(`${sf.rel}: ${(e as Error).message}`);
    return null;
  }
}

interface ShapeFile {
  sf: ts.SourceFile;
  rel: string;
}

/** A `*.cases.tsx` (or any example file): `export const meta = vcase({...})`. */
function scanCaseFile(text: string, file: string, rel: string, key: string, problems: string[]): ScannedCase[] {
  const sf = parse(text, file);
  const decl = exportedConsts(sf).get("meta");
  if (!decl) return [];
  const shape = { sf, rel };
  const arg = wrapErrors(shape, problems, () => vcaseCall(decl.initializer));
  if (!arg) return [];
  const line = lineOf(sf, decl.parent.parent);
  const entry = wrapErrors(shape, problems, () => entryOf(literal(arg) as Record<string, unknown>, `${rel}:${line}`));
  if (!entry) return [];
  return [{ entry, file, key, exportName: hasDefaultExport(sf) ? "default" : null }];
}

/** Storybook's `sanitize`: lowercase, non-alphanumerics to `-`, no leading / trailing / double dashes. */
export function sanitize(s: string): string {
  return s
    .toLowerCase()
    .replace(/[ ’–—―′¿'`~!@#$%^&*()_|+\-=?;:'",.<>{}[\]\\/]/gi, "-")
    .replace(/-+/g, "-")
    .replace(/^-+/, "")
    .replace(/-+$/, "");
}

/** `PrimaryLarge` -> `Primary Large` (Storybook's `storyNameFromExport`). */
export function storyNameFromExport(name: string): string {
  return name
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/([A-Z]+)([A-Z][a-z])/g, "$1 $2")
    .replace(/[_-]+/g, " ")
    .replace(/^./, (c) => c.toUpperCase())
    .trim();
}

/** A valid vcase id segment from free text: `Primary Large` -> `primary-large`. */
function idSegment(s: string): string {
  const seg = sanitize(s).replace(/^[^a-z]+/, "");
  return seg || "x";
}

function scanStories(text: string, file: string, rel: string, key: string, all: boolean, problems: string[]): ScannedCase[] {
  const sf = parse(text, file);
  const shape = { sf, rel };
  // The default export: an object literal, or a const holding one.
  let metaObj: ts.Expression | null = null;
  for (const s of sf.statements) {
    if (ts.isExportAssignment(s) && !s.isExportEquals) {
      const e = unwrap(s.expression);
      if (ts.isIdentifier(e)) {
        for (const st of sf.statements) {
          if (!ts.isVariableStatement(st)) continue;
          for (const d of st.declarationList.declarations) if (ts.isIdentifier(d.name) && d.name.text === e.text && d.initializer) metaObj = d.initializer;
        }
      } else {
        metaObj = e;
      }
    }
  }
  const titleNode = metaObj ? propertyOf(metaObj, "title") : null;
  const title = titleNode && (ts.isStringLiteral(unwrap(titleNode)) || ts.isNoSubstitutionTemplateLiteral(unwrap(titleNode))) ? (unwrap(titleNode) as ts.StringLiteral).text : key.replace(/\.stories$/, "");
  const fileParams = metaObj ? propertyOf(metaObj, "parameters") : null;
  const fileVcaseNode = fileParams ? propertyOf(fileParams, "vcase") : null;
  const fileMeta = fileVcaseNode ? (wrapErrors(shape, problems, () => literal(fileVcaseNode)) as Record<string, unknown> | null) : null;
  if (fileMeta && "id" in fileMeta) problems.push(`${rel}: a file-level parameters.vcase must not set an id (each story is its own case)`);

  const out: ScannedCase[] = [];
  for (const [name, decl] of exportedConsts(sf)) {
    if (name === "__namedExportsOrder" || name === "default" || !/^[A-Z]/.test(name)) continue;
    const init = decl.initializer;
    const params = init ? propertyOf(init, "parameters") : null;
    const storyVcaseNode = params ? propertyOf(params, "vcase") : null;
    const storyMeta = storyVcaseNode ? (wrapErrors(shape, problems, () => literal(storyVcaseNode)) as Record<string, unknown> | null) : null;
    if (!all && !storyMeta && !fileMeta) continue;
    // Storybook derives the story id from the export name, not from a `name` override.
    const storyName = storyNameFromExport(name);
    const derivedId = [...title.split("/").map(idSegment), idSegment(storyName)].join(".");
    const meta = { ...(fileMeta ?? {}), ...(storyMeta ?? {}) };
    if (meta.id === undefined) meta.id = derivedId;
    const line = lineOf(sf, decl.parent.parent);
    const entry = wrapErrors(shape, problems, () => entryOf(meta, `${rel}:${line}`));
    if (!entry) continue;
    out.push({ entry, file, key, exportName: name, storyId: `${sanitize(title)}--${sanitize(storyName)}` });
  }
  return out;
}
