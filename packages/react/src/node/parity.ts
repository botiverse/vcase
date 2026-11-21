import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { expandManifest } from "./axes.js";
import { compareImages, describeCompare, readPng } from "./compare.js";
import type { VCaseManifest } from "./manifest.js";
import { esc, STYLE } from "./report.js";

export interface ParityPair {
  /** This platform's capture stem, e.g. `ui.button.variants@light,phone,touch`. */
  stem: string;
  id: string;
  platform: string;
  /** The counterpart's stem, e.g. `ds.button.variants@light,phone,touch`. */
  otherStem: string;
  ours: string | null;
  theirs: string | null;
  /** Whole-image comparison, a hint only (cross-platform results never gate). */
  hint: string;
}

export interface ParityResult {
  pairs: ParityPair[];
  reportFile: string;
}

/**
 * Side-by-side report of each case with `parity` entries and its counterpart's golden on the
 * other platform. Report-only: it never fails. Variants pair by axis name (`light,phone,touch`);
 * images are copied into the report directory so it uploads as one artifact.
 *
 * @param goldensDir this platform's golden directory (`goldens/web-chromium`)
 * @param others platform name to its golden directory, e.g. `mobile` -> `.../goldens/desktop-skiko`
 */
export function writeParityReport(manifest: VCaseManifest, goldensDir: string, others: Record<string, string>, outDir: string): ParityResult {
  const imgDir = path.join(outDir, "img");
  mkdirSync(imgDir, { recursive: true });
  const pairs: ParityPair[] = [];
  const otherFiles = new Map(Object.entries(others).map(([p, d]) => [p, existsSync(d) ? new Set(readdirSync(d).filter((f) => f.endsWith(".png"))) : new Set<string>()]));
  for (const v of expandManifest(manifest)) {
    for (const ref of v.case.parity) {
      const i = ref.indexOf(":");
      const platform = ref.slice(0, i);
      const otherId = ref.slice(i + 1);
      const dir = others[platform];
      if (!dir) continue;
      const otherStem = `${otherId}@${v.name}`;
      const oursSrc = path.join(goldensDir, `${v.stem}.png`);
      const theirsSrc = otherFiles.get(platform)!.has(`${otherStem}.png`) ? path.join(dir, `${otherStem}.png`) : null;
      const copy = (src: string | null, side: string, stem: string) => {
        if (!src || !existsSync(src)) return null;
        const dst = path.join(imgDir, side, `${stem}.png`);
        mkdirSync(path.dirname(dst), { recursive: true });
        copyFileSync(src, dst);
        return dst;
      };
      const ours = copy(oursSrc, "ours", v.stem);
      const theirs = copy(theirsSrc, platform, otherStem);
      let hint = ours ? (theirs ? "" : `no ${platform} golden ${otherStem}.png`) : `no golden ${v.stem}.png yet`;
      if (ours && theirs) {
        try {
          hint = describeCompare(compareImages(readPng(readFileSync(ours)), readPng(readFileSync(theirs)), { channelDelta: 2 }));
        } catch (e) {
          hint = (e as Error).message;
        }
      }
      pairs.push({ stem: v.stem, id: v.case.id, platform, otherStem, ours, theirs, hint });
    }
  }
  const rel = (f: string | null) => (f ? path.relative(outDir, f).split(path.sep).join("/") : null);
  let h = `<!doctype html><html><head><meta charset="utf-8"><title>vcase parity</title><style>${STYLE}</style></head><body>`;
  h += `<h1>vcase parity: ${esc(manifest.project)}</h1><p>Report-only. ${pairs.length} pairs, ${pairs.filter((p) => p.ours && p.theirs).length} with both images. The whole-image hint (channel delta 2) never fails a build.</p>`;
  for (const p of pairs) {
    const c = manifest.cases.find((x) => x.id === p.id)!;
    const status = p.ours && p.theirs ? "Paired" : "Unpaired";
    h += `<section><h3><code>${esc(p.stem)}</code> vs <code>${esc(p.platform)}:${esc(p.otherStem)}</code> <span class="s ${status}">${status}</span></h3><p>${esc(p.hint)}</p>`;
    const divs = c.divergences.filter((d) => d.with === p.platform);
    if (divs.length > 0) {
      h += "<ul>" + divs.map((d) => `<li>${esc(d.kind)}${d.region ? ` in <code>${esc(d.region)}</code>` : ""}: ${esc(d.why)}${d.ref ? ` (${esc(d.ref)})` : ""}${d.expires ? `, expires ${esc(d.expires)}` : ""}</li>`).join("") + "</ul>";
    }
    h += `<div class="tri">`;
    for (const [label, f] of [
      ["this platform", rel(p.ours)],
      [p.platform, rel(p.theirs)],
    ] as const) {
      h += f ? `<figure><figcaption>${esc(label)}</figcaption><img src="${esc(f)}"></figure>` : `<figure><figcaption>${esc(label)}: missing</figcaption></figure>`;
    }
    h += "</div></section>";
  }
  h += "</body></html>\n";
  const reportFile = path.join(outDir, "index.html");
  writeFileSync(reportFile, h);
  writeFileSync(path.join(outDir, "parity.json"), JSON.stringify(pairs.map((p) => ({ ...p, ours: rel(p.ours), theirs: rel(p.theirs) })), null, 2) + "\n");
  return { pairs, reportFile };
}
