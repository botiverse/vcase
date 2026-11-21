import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync, copyFileSync } from "node:fs";
import path from "node:path";
import type { VCaseVariant } from "./axes.js";
import { compareImages, describeCompare, readPng, writePng, type Tolerance } from "./compare.js";
import { writeReport } from "./report.js";

/** Metadata next to every capture (`<stem>.json`), the adapter contract shared by all platforms. */
export interface CaptureMeta {
  id: string;
  axes: Record<string, string>;
  platform: string;
  renderer: string;
  fontsHash: string | null;
  durationMs: number;
  /** Named regions in image pixels, `[x, y, w, h]`. */
  regions: Record<string, [number, number, number, number]>;
  warnings: string[];
}

export function captureMetaJson(m: CaptureMeta): string {
  const ordered = {
    id: m.id,
    axes: m.axes,
    platform: m.platform,
    renderer: m.renderer,
    fontsHash: m.fontsHash,
    durationMs: m.durationMs,
    regions: m.regions,
    warnings: m.warnings,
  };
  return JSON.stringify(ordered, null, 2) + "\n";
}

export type CaptureOutcome =
  | { kind: "image"; png: Uint8Array; meta: CaptureMeta }
  | { kind: "error"; message: string; meta?: CaptureMeta }
  | { kind: "skipped"; reason: string };

export type Mode = "record" | "verify";

export type VariantStatus = "Passed" | "Recorded" | "Unchanged" | "Skipped" | "Failed" | "MissingGolden" | "Error";

export const FAILING: ReadonlySet<VariantStatus> = new Set(["Failed", "MissingGolden", "Error"]);

export interface VariantResult {
  variant: VCaseVariant;
  status: VariantStatus;
  message: string;
  actual?: string;
  expected?: string;
  diff?: string;
  meta?: CaptureMeta;
}

export interface RunSummary {
  mode: Mode;
  renderer: string;
  results: VariantResult[];
  /** Goldens no variant produced; record mode has deleted them. */
  orphanGoldens: string[];
  reportFile: string | null;
}

export function summaryOk(s: RunSummary): boolean {
  return !s.results.some((r) => FAILING.has(r.status)) && (s.mode === "record" || s.orphanGoldens.length === 0);
}

export function describeSummary(s: RunSummary): string {
  const counts = new Map<VariantStatus, number>();
  s.results.forEach((r) => counts.set(r.status, (counts.get(r.status) ?? 0) + 1));
  let out = `vcase ${s.mode} [${s.renderer}]: ${s.results.length} captures`;
  for (const st of ["Passed", "Recorded", "Unchanged", "Skipped", "Failed", "MissingGolden", "Error"] as const) {
    const n = counts.get(st);
    if (n) out += `, ${st.toLowerCase()} ${n}`;
  }
  if (s.orphanGoldens.length > 0) out += `, orphan goldens ${s.orphanGoldens.length}`;
  for (const r of s.results) if (FAILING.has(r.status)) out += `\n  ${r.status}: ${r.variant.stem}: ${r.message}`;
  for (const o of s.orphanGoldens) out += s.mode === "verify" ? `\n  orphan golden: ${o}.png (re-record or delete)` : `\n  deleted orphan golden: ${o}.png`;
  if (s.reportFile) out += `\n  report: ${s.reportFile}`;
  return out;
}

/** Goldens of one renderer: `<root>/<renderer>/<stem>.png`. */
export class GoldenStore {
  constructor(
    readonly root: string,
    readonly renderer: string,
  ) {}
  get dir(): string {
    return path.join(this.root, this.renderer);
  }
  golden(stem: string): string {
    return path.join(this.dir, `${stem}.png`);
  }
  stems(): string[] {
    if (!existsSync(this.dir)) return [];
    return readdirSync(this.dir)
      .filter((f) => f.endsWith(".png"))
      .map((f) => f.slice(0, -4));
  }
}

/**
 * Applies the record / verify rule to captures (vcase-core's `GoldenRun`): actual images, diffs
 * and metadata go to `<out>/<renderer>/`, the report to `<out>/report/index.html`.
 */
export class GoldenRun {
  private readonly results: VariantResult[] = [];
  readonly captureDir: string;

  constructor(
    readonly goldens: GoldenStore,
    readonly outputDir: string,
    readonly mode: Mode,
    readonly tolerance: Tolerance = {},
  ) {
    this.captureDir = path.join(outputDir, goldens.renderer);
  }

  accept(variant: VCaseVariant, outcome: CaptureOutcome): VariantResult {
    let r: VariantResult;
    if (outcome.kind === "skipped") r = { variant, status: "Skipped", message: outcome.reason };
    else if (outcome.kind === "error") {
      r = { variant, status: "Error", message: outcome.message, meta: outcome.meta };
      if (outcome.meta) this.writeMeta(variant.stem, outcome.meta);
    } else r = this.image(variant, outcome.png, outcome.meta);
    this.results.push(r);
    return r;
  }

  private writeMeta(stem: string, meta: CaptureMeta) {
    mkdirSync(this.captureDir, { recursive: true });
    writeFileSync(path.join(this.captureDir, `${stem}.json`), captureMetaJson(meta));
  }

  private image(variant: VCaseVariant, png: Uint8Array, meta: CaptureMeta): VariantResult {
    mkdirSync(this.captureDir, { recursive: true });
    const stem = variant.stem;
    const actual = path.join(this.captureDir, `${stem}.png`);
    writeFileSync(actual, png);
    this.writeMeta(stem, meta);
    const golden = this.goldens.golden(stem);
    const cmp = existsSync(golden) ? compareImages(readPng(readFileSync(golden)), readPng(png), this.tolerance) : null;
    const writeDiff = () => {
      if (!cmp?.diff) return undefined;
      const f = path.join(this.captureDir, `${stem}.diff.png`);
      writeFileSync(f, writePng(cmp.diff));
      return f;
    };
    if (this.mode === "record") {
      if (!cmp) {
        mkdirSync(path.dirname(golden), { recursive: true });
        writeFileSync(golden, png);
        return { variant, status: "Recorded", message: "new golden", actual, expected: golden, meta };
      }
      if (cmp.matches) return { variant, status: "Unchanged", message: "", actual, expected: golden, meta };
      const diff = writeDiff();
      writeFileSync(golden, png);
      return { variant, status: "Recorded", message: `updated: ${describeCompare(cmp)}`, actual, expected: golden, diff, meta };
    }
    if (!cmp) return { variant, status: "MissingGolden", message: `no golden at ${golden}`, actual, meta };
    if (cmp.matches) return { variant, status: "Passed", message: cmp.mismatched > 0 ? `within tolerance: ${describeCompare(cmp)}` : "", actual, expected: golden, meta };
    const expectedCopy = path.join(this.captureDir, `${stem}.expected.png`);
    copyFileSync(golden, expectedCopy);
    return { variant, status: "Failed", message: describeCompare(cmp), actual, expected: expectedCopy, diff: writeDiff(), meta };
  }

  /** Ends the run: orphan goldens (only for a complete run), deleted in record mode; writes the report. */
  finish(complete: boolean, title = "vcase"): RunSummary {
    const produced = new Set(this.results.map((r) => r.variant.stem));
    const orphans = complete ? this.goldens.stems().filter((s) => !produced.has(s)).sort() : [];
    if (this.mode === "record") orphans.forEach((o) => rmSync(this.goldens.golden(o), { force: true }));
    const summary: RunSummary = { mode: this.mode, renderer: this.goldens.renderer, results: [...this.results], orphanGoldens: orphans, reportFile: null };
    const reportFile = writeReport(path.join(this.outputDir, "report"), summary, title);
    return { ...summary, reportFile };
  }
}
