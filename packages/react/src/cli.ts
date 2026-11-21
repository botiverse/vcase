#!/usr/bin/env node
import path from "node:path";
import { parseArgs } from "node:util";
import { loadConfig } from "./node/config.js";
import { decodeManifest, MANIFEST_FILE } from "./node/manifest.js";
import { writeParityReport } from "./node/parity.js";
import { runVCases } from "./node/run.js";
import { readFileSync } from "node:fs";

const USAGE = `usage: vcase-react <command> [options]

commands:
  scan      scan the cases, write the manifest (no browser)
  check     fail when the committed manifest differs from the scan (no browser)
  verify    manifest check + capture every variant + compare with the goldens (zero tolerance)
  record    rewrite the manifest, write new and changed goldens, delete orphan goldens
  parity    side-by-side report against other platforms' goldens (never fails)

options:
  --config <file>          default: vcase.config.mjs / .js / .json in the current directory
  --filter <globs>         comma-separated id globs, e.g. 'ui.button.*,screens.*' (verify / record)
  --goldens <dir>          golden root (default from config, else goldens)
  --out <dir>              output directory (default from config, else build/vcase)
  --with <platform=dir>    parity: another platform's golden directory (repeatable; default config.parity)
`;

async function main(argv: string[]): Promise<number> {
  const { values, positionals } = parseArgs({
    args: argv,
    allowPositionals: true,
    options: {
      config: { type: "string" },
      filter: { type: "string" },
      goldens: { type: "string" },
      out: { type: "string" },
      with: { type: "string", multiple: true },
      help: { type: "boolean", short: "h" },
    },
  });
  const command = positionals[0];
  if (values.help || !command) {
    process.stdout.write(USAGE);
    return command || values.help ? 0 : 2;
  }
  const loaded = await loadConfig(process.cwd(), values.config);
  const { config, dir } = loaded;
  const log = (s: string) => process.stdout.write(s + "\n");

  if (command === "parity") {
    const others: Record<string, string> = { ...(config.parity ?? {}) };
    for (const w of values.with ?? []) {
      const i = w.indexOf("=");
      if (i <= 0) throw new Error(`--with expects platform=dir, got '${w}'`);
      others[w.slice(0, i)] = w.slice(i + 1);
    }
    const resolved = Object.fromEntries(Object.entries(others).map(([p, d]) => [p, path.resolve(dir, d)]));
    const manifestFile = path.resolve(dir, config.manifest ?? MANIFEST_FILE);
    const manifest = decodeManifest(readFileSync(manifestFile, "utf8"), manifestFile);
    const goldens = path.resolve(dir, values.goldens ?? config.goldens ?? "goldens", config.renderer ?? "web-chromium");
    const out = path.resolve(dir, values.out ?? config.out ?? "build/vcase", "parity");
    const result = writeParityReport(manifest, goldens, resolved, out);
    const both = result.pairs.filter((p) => p.ours && p.theirs).length;
    log(`vcase parity: ${result.pairs.length} pairs, ${both} with both images (report-only)`);
    for (const p of result.pairs) log(`  ${p.stem} vs ${p.platform}:${p.otherStem}: ${p.hint}`);
    log(`  report: ${result.reportFile}`);
    return 0;
  }

  if (!["scan", "check", "verify", "record"].includes(command)) {
    process.stderr.write(`unknown command '${command}'\n\n${USAGE}`);
    return 2;
  }
  const filter = (values.filter ?? "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  const result = await runVCases(loaded, {
    mode: command as "scan" | "check" | "verify" | "record",
    filter,
    goldens: values.goldens,
    out: values.out,
    log,
  });
  log(result.describe());
  return result.ok ? 0 : 1;
}

main(process.argv.slice(2)).then(
  (code) => process.exit(code),
  (e: unknown) => {
    process.stderr.write(`vcase-react: ${e instanceof Error ? e.message : String(e)}\n`);
    process.exit(1);
  },
);
