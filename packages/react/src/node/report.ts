import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { describeSummary, FAILING, type RunSummary, type VariantResult } from "./golden.js";

export const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

export const STYLE =
  "body{font:14px system-ui,sans-serif;margin:24px;color:#222}table{border-collapse:collapse}" +
  "td,th{border:1px solid #ddd;padding:4px 8px;text-align:left;vertical-align:top}" +
  ".s{display:inline-block;padding:1px 6px;border-radius:3px;color:#fff;font-size:12px;text-decoration:none}" +
  ".Passed,.Unchanged{background:#2e7d32}.Recorded{background:#1565c0}.Skipped,.Unpaired{background:#888}" +
  ".Failed,.MissingGolden,.Error{background:#c62828}.Paired{background:#6a1b9a}" +
  ".tri{display:flex;gap:12px;align-items:flex-start;flex-wrap:wrap}.tri figure{margin:0}" +
  ".tri img{max-width:420px;border:1px solid #ccc;background:repeating-conic-gradient(#eee 0 25%,#fff 0 50%) 0 0/16px 16px}" +
  "section{margin:24px 0;padding-top:8px;border-top:1px solid #eee}code{background:#f4f4f4;padding:0 4px}";

const anchor = (r: VariantResult) => "c-" + r.variant.stem.replace(/[^A-Za-z0-9_-]/g, "_");

/**
 * Static report: a matrix of cases x axis variants, then expected / actual / diff per capture
 * (vcase-core's `HtmlReport`), plus `summary.json` for tools. Images are linked relatively, so
 * the output directory uploads as one CI artifact.
 */
export function writeReport(dir: string, s: RunSummary, title: string): string {
  mkdirSync(dir, { recursive: true });
  const rel = (f?: string) => (f ? path.relative(dir, f).split(path.sep).join("/") : undefined);
  const ids = [...new Set(s.results.map((r) => r.variant.case.id))].sort();
  const columns = [...new Set(s.results.map((r) => r.variant.name))].sort();
  let h = `<!doctype html><html><head><meta charset="utf-8"><title>${esc(title)}</title><style>${STYLE}</style></head><body>`;
  h += `<h1>${esc(title)}</h1><p>${esc(describeSummary({ ...s, reportFile: null }).split("\n")[0]!)}</p>`;
  if (s.orphanGoldens.length > 0) h += `<p>Orphan goldens: ${s.orphanGoldens.map((o) => `<code>${esc(o)}</code>`).join(" ")}</p>`;
  h += `<table><tr><th>case</th>${columns.map((c) => `<th>${esc(c)}</th>`).join("")}</tr>`;
  for (const id of ids) {
    h += `<tr><td><code>${esc(id)}</code></td>`;
    for (const c of columns) {
      const r = s.results.find((x) => x.variant.case.id === id && x.variant.name === c);
      h += `<td>${r ? `<a class="s ${r.status}" href="#${anchor(r)}">${r.status}</a>` : ""}</td>`;
    }
    h += "</tr>";
  }
  h += "</table>";
  const ordered = [...s.results].sort((a, b) => Number(FAILING.has(b.status)) - Number(FAILING.has(a.status)) || (a.variant.stem < b.variant.stem ? -1 : 1));
  for (const r of ordered) {
    h += `<section id="${anchor(r)}"><h3><code>${esc(r.variant.stem)}</code> <span class="s ${r.status}">${r.status}</span></h3>`;
    if (r.message) h += `<p>${esc(r.message)}</p>`;
    if (r.meta) {
      h += `<p>${esc(`${r.meta.platform} / ${r.meta.renderer} / ${r.meta.durationMs} ms`)}${r.meta.fontsHash ? esc(` / fonts ${r.meta.fontsHash}`) : ""}</p>`;
      if (r.meta.warnings.length > 0) h += `<p>warnings: ${esc(r.meta.warnings.join("; "))}</p>`;
    }
    if (r.variant.case.divergences.length > 0) {
      h += "<ul>" + r.variant.case.divergences.map((d) => `<li>divergence with ${esc(d.with)}${d.region ? ` (${esc(d.region)})` : ""}: ${esc(d.why)}</li>`).join("") + "</ul>";
    }
    const figs = [
      ["expected", rel(r.expected)],
      ["actual", rel(r.actual)],
      ["diff", rel(r.diff)],
    ].filter(([, f]) => f);
    if (figs.length > 0) h += `<div class="tri">${figs.map(([l, f]) => `<figure><figcaption>${l}</figcaption><img src="${esc(f!)}"></figure>`).join("")}</div>`;
    h += "</section>";
  }
  h += "</body></html>\n";
  const file = path.join(dir, "index.html");
  writeFileSync(file, h);
  const json = {
    mode: s.mode,
    renderer: s.renderer,
    ok: !s.results.some((r) => FAILING.has(r.status)) && (s.mode === "record" || s.orphanGoldens.length === 0),
    orphanGoldens: s.orphanGoldens,
    results: s.results.map((r) => ({ stem: r.variant.stem, id: r.variant.case.id, status: r.status, message: r.message, actual: rel(r.actual), expected: rel(r.expected), diff: rel(r.diff) })),
  };
  writeFileSync(path.join(dir, "summary.json"), JSON.stringify(json, null, 2) + "\n");
  return file;
}
