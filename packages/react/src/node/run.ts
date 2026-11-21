import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { expandCase, requireValid } from "./axes.js";
import type { LoadedConfig } from "./config.js";
import { describeSummary, GoldenRun, GoldenStore, summaryOk, type Mode, type RunSummary } from "./golden.js";
import { decodeManifest, describeDiff, diffIsEmpty, diffManifests, encodeManifest, MANIFEST_FILE, type ManifestDiff, type VCaseManifest } from "./manifest.js";
import { createRenderer } from "./render.js";
import { manifestOf, scan, type ScannedCase } from "./scan.js";
import { startServer } from "./server.js";

export interface RunOptions {
  /** `scan` writes the manifest only; `check` compares it with the scan only (no browser). */
  mode: Mode | "scan" | "check";
  /** Id globs (`ui.button.*`); orphan detection is off while filtering. */
  filter?: string[];
  goldens?: string;
  out?: string;
  log?: (line: string) => void;
}

export interface RunResult {
  manifest: VCaseManifest;
  cases: ScannedCase[];
  /** Committed manifest vs scan; null in record / scan mode or when there is none to compare. */
  diff: ManifestDiff | null;
  summary: RunSummary | null;
  ok: boolean;
  describe(): string;
}

export function globMatcher(filter: string[] = []): (id: string) => boolean {
  const res = filter.map((g) => new RegExp("^" + g.split("*").map((p) => p.replace(/[.+?^${}()|[\]\\]/g, "\\$&")).join(".*") + "$"));
  return (id) => res.length === 0 || res.some((r) => r.test(id));
}

export function scanProject(loaded: LoadedConfig): { manifest: VCaseManifest; cases: ScannedCase[]; skipped: string[] } {
  const { config, dir } = loaded;
  const result = scan({ cwd: dir, sources: config.sources, sourceRoot: config.sourceRoot });
  if (result.problems.length > 0) throw new Error("vcase scan failed:\n" + result.problems.map((p) => `  - ${p}`).join("\n"));
  const manifest = manifestOf(config.project, config.defaults, result.cases);
  requireValid(manifest);
  return { manifest, cases: result.cases, skipped: result.skipped };
}

/**
 * The React `runVCases`: scans, checks or writes the manifest, renders every variant with
 * Playwright and records or verifies goldens.
 *
 * - verify: the committed manifest must equal the scan; every capture must equal its golden; a
 *   missing golden, an error or an orphan golden fails.
 * - record: rewrites the manifest, writes new and changed goldens, deletes orphan goldens.
 */
export async function runVCases(loaded: LoadedConfig, opts: RunOptions): Promise<RunResult> {
  const { config, dir } = loaded;
  const log = opts.log ?? (() => {});
  const { manifest, cases } = scanProject(loaded);
  const manifestFile = path.resolve(dir, config.manifest ?? MANIFEST_FILE);

  let diff: ManifestDiff | null = null;
  if (opts.mode === "record" || opts.mode === "scan") {
    mkdirSync(path.dirname(manifestFile), { recursive: true });
    writeFileSync(manifestFile, encodeManifest(manifest));
  } else {
    diff = existsSync(manifestFile)
      ? diffManifests(decodeManifest(readFileSync(manifestFile, "utf8"), manifestFile), manifest)
      : { added: manifest.cases.map((c) => c.id), removed: [], changed: {}, header: [`missing ${manifestFile}`], moved: [] };
  }
  const finish = (summary: RunSummary | null): RunResult => {
    const ok = (diff ? diffIsEmpty(diff) : true) && (summary ? summaryOk(summary) : true);
    return {
      manifest,
      cases,
      diff,
      summary,
      ok,
      describe: () => {
        const parts: string[] = [];
        if (diff && !diffIsEmpty(diff)) parts.push(describeDiff(diff) + "\n  (run `vcase-react record` or `vcase-react scan` to rewrite the manifest)");
        if (diff && diff.moved.length > 0) parts.push(`note: ${diff.moved.length} case(s) moved within their file (${diff.moved.join(", ")}); record or scan rewrites the source lines`);
        if (summary) parts.push(describeSummary(summary));
        if (parts.length === 0) parts.push(opts.mode === "check" ? `vcase: manifest matches the scan (${manifest.cases.length} cases)` : `vcase: manifest written (${manifest.cases.length} cases)`);
        return parts.join("\n");
      },
    };
  };
  if (opts.mode === "scan" || opts.mode === "check") return finish(null);

  const matches = globMatcher(opts.filter);
  const renderer = config.renderer ?? "web-chromium";
  const run = new GoldenRun(new GoldenStore(path.resolve(dir, opts.goldens ?? config.goldens ?? "goldens"), renderer), path.resolve(dir, opts.out ?? config.out ?? "build/vcase"), opts.mode, config.tolerance ?? {});
  const server = await startServer(config.server, config.baseURL, dir, log);
  try {
    const r = await createRenderer(config, dir);
    try {
      for (const c of cases) {
        if (!matches(c.entry.id)) continue;
        for (const v of expandCase(c.entry, manifest.defaults)) {
          const result = run.accept(v, await r.render(c, v));
          log(`  ${result.status.padEnd(13)} ${v.stem}${result.message ? `: ${result.message}` : ""}`);
        }
      }
    } finally {
      await r.close();
    }
  } finally {
    await server.stop();
  }
  return finish(run.finish((opts.filter ?? []).length === 0, `vcase ${config.project} (${renderer})`));
}
