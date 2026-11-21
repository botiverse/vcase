import { existsSync, mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { compareImages, expandCase, GoldenRun, GoldenStore, readPng, summaryOk, writePng, writeParityReport, type CaptureMeta, type Image } from "../src/node/index.js";

const img = (w: number, h: number, rgba: [number, number, number, number], tweak?: (d: Uint8Array) => void): Image => {
  const data = new Uint8Array(w * h * 4);
  for (let i = 0; i < w * h; i++) data.set(rgba, i * 4);
  tweak?.(data);
  return { width: w, height: h, data };
};
const defaults = { theme: ["light"], viewport: [{ name: "phone", w: 10, h: 10, density: 1 }], locale: ["en"], pointer: ["touch"] };
const variant = expandCase({ id: "ui.a.b", kind: "component", source: "s", fixture: null, axes: {}, parity: ["mobile:ds.a.b"], divergences: [] }, defaults)[0]!;
const meta: CaptureMeta = { id: "ui.a.b", axes: {}, platform: "web", renderer: "r", fontsHash: null, durationMs: 1, regions: {}, warnings: [] };

describe("compare", () => {
  it("is exact by default", () => {
    const a = img(4, 4, [10, 20, 30, 255]);
    expect(compareImages(a, img(4, 4, [10, 20, 30, 255])).matches).toBe(true);
    const b = img(4, 4, [10, 20, 30, 255], (d) => (d[4 * 5] = 11));
    const r = compareImages(a, b);
    expect(r.matches).toBe(false);
    expect(r.mismatched).toBe(1);
    expect(r.bounds).toEqual([1, 1, 1, 1]);
    expect(compareImages(a, b, { channelDelta: 1 }).matches).toBe(true);
    expect(compareImages(a, b, { maxDiffPixels: 1 }).matches).toBe(true);
  });

  it("fails on size", () => {
    const r = compareImages(img(4, 4, [0, 0, 0, 255]), img(4, 5, [0, 0, 0, 255]));
    expect(r.sizeMatches).toBe(false);
    expect(r.mismatched).toBe(4);
  });

  it("round-trips PNG", () => {
    const a = img(3, 2, [1, 2, 3, 4]);
    expect(readPng(writePng(a))).toEqual(a);
  });
});

describe("golden run", () => {
  it("records, verifies, fails on change, reports orphans", () => {
    const dir = mkdtempSync(path.join(tmpdir(), "vcase-"));
    const store = new GoldenStore(path.join(dir, "goldens"), "web-chromium");
    const png = writePng(img(4, 4, [0, 0, 255, 255]));

    const rec = new GoldenRun(store, path.join(dir, "out"), "record");
    expect(rec.accept(variant, { kind: "image", png, meta }).status).toBe("Recorded");
    expect(existsSync(store.golden("ui.a.b@light,phone"))).toBe(true);

    const ok = new GoldenRun(store, path.join(dir, "out"), "verify");
    expect(ok.accept(variant, { kind: "image", png, meta }).status).toBe("Passed");
    expect(summaryOk(ok.finish(true))).toBe(true);

    const bad = new GoldenRun(store, path.join(dir, "out"), "verify");
    const changed = writePng(img(4, 4, [0, 0, 254, 255]));
    const r = bad.accept(variant, { kind: "image", png: changed, meta });
    expect(r.status).toBe("Failed");
    expect(r.message).toBe("16 of 16 px differ in [0,0 4x4]");
    expect(existsSync(r.diff!)).toBe(true);
    const s = bad.finish(true);
    expect(summaryOk(s)).toBe(false);
    expect(readFileSync(s.reportFile!, "utf8")).toContain("ui.a.b@light,phone");
    expect(JSON.parse(readFileSync(path.join(dir, "out/report/summary.json"), "utf8")).ok).toBe(false);

    const orphan = new GoldenRun(store, path.join(dir, "out"), "verify").finish(true);
    expect(orphan.orphanGoldens).toEqual(["ui.a.b@light,phone"]);
    expect(summaryOk(orphan)).toBe(false);
    new GoldenRun(store, path.join(dir, "out"), "record").finish(true);
    expect(existsSync(store.golden("ui.a.b@light,phone"))).toBe(false);
  });

  it("errors fail verify", () => {
    const dir = mkdtempSync(path.join(tmpdir(), "vcase-"));
    const run = new GoldenRun(new GoldenStore(dir, "r"), path.join(dir, "out"), "verify");
    run.accept(variant, { kind: "error", message: "unmocked GET https://example.com/" });
    expect(summaryOk(run.finish(false))).toBe(false);
  });
});

describe("parity report", () => {
  it("pairs variants by axis name with the other platform's goldens", () => {
    const dir = mkdtempSync(path.join(tmpdir(), "vcase-"));
    const ours = new GoldenStore(path.join(dir, "g"), "web-chromium");
    const theirs = new GoldenStore(path.join(dir, "m"), "desktop-skiko");
    new GoldenRun(ours, path.join(dir, "o"), "record").accept(variant, { kind: "image", png: writePng(img(2, 2, [0, 0, 0, 255])), meta });
    const v2 = { ...variant, stem: "ds.a.b@light,phone" };
    new GoldenRun(theirs, path.join(dir, "o2"), "record").accept(v2, { kind: "image", png: writePng(img(2, 2, [1, 0, 0, 255])), meta });
    const manifest = { schema: "vcase/1", project: "p", defaults, cases: [variant.case] };
    const r = writeParityReport(manifest, ours.dir, { mobile: theirs.dir, ios: path.join(dir, "none") }, path.join(dir, "parity"));
    expect(r.pairs.map((p) => [p.platform, p.otherStem, p.hint])).toEqual([["mobile", "ds.a.b@light,phone", "identical"]]);
    expect(readFileSync(r.reportFile, "utf8")).toContain("img/mobile/ds.a.b@light,phone.png");
  });
});
