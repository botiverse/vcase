// Format compatibility with vcase-core (Kotlin): the manifest the Kotlin sample committed, and the
// one the React sample committed, read, validate, expand and re-encode byte for byte here.
import { readdirSync, readFileSync, existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { Ajv } from "ajv";
import { describe, expect, it } from "vitest";
import { decodeManifest, encodeManifest, expandManifest, manifestProblems, manifestOf, scan } from "../src/node/index.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, "../../..");
const kotlinManifestFile = path.join(repo, "samples/compose-desktop/vcase.manifest.json");
const kotlinGoldens = path.join(repo, "samples/compose-desktop/goldens/desktop-skiko");
const reactManifestFile = path.join(repo, "samples/react/vcase.manifest.json");
const reactGoldens = path.join(repo, "samples/react/goldens/web-chromium");
const schema = JSON.parse(readFileSync(path.join(here, "../schema/vcase-manifest.schema.json"), "utf8"));

const pngStems = (dir: string) =>
  readdirSync(dir)
    .filter((f) => f.endsWith(".png"))
    .map((f) => f.slice(0, -4))
    .sort();

describe("Kotlin sample manifest (samples/compose-desktop)", () => {
  const text = readFileSync(kotlinManifestFile, "utf8");
  const manifest = decodeManifest(text, kotlinManifestFile);

  it("re-encodes byte for byte", () => {
    expect(encodeManifest(manifest)).toBe(text);
  });

  it("is valid", () => {
    expect(manifestProblems(manifest)).toEqual([]);
  });

  it("expands to exactly the committed Kotlin golden file names", () => {
    expect(expandManifest(manifest).map((v) => v.stem).sort()).toEqual(pngStems(kotlinGoldens));
  });

  it("matches the JSON schema", () => {
    const validate = new Ajv({ allErrors: true }).compile(schema);
    expect(validate(JSON.parse(text)), JSON.stringify(validate.errors)).toBe(true);
  });

  it("is what the React scanner writes for the same cases declared with vcase()", () => {
    const result = scan({ cwd: path.join(here, "fixtures/kotlin-sample"), sources: [{ glob: "*.cases.tsx" }] });
    expect(result.problems).toEqual([]);
    expect(result.skipped).toEqual(["packages/react/test/fixtures/kotlin-sample/not-a-case.cases.tsx"]);
    const ours = manifestOf(manifest.project, manifest.defaults, result.cases);
    // Only `source` differs (a .tsx line instead of a .kt line): swap in Kotlin's and compare bytes.
    const sources = new Map(manifest.cases.map((c) => [c.id, c.source]));
    const swapped = { ...ours, cases: ours.cases.map((c) => ({ ...c, source: sources.get(c.id)! })) };
    expect(encodeManifest(swapped)).toBe(text);
  });
});

describe.runIf(existsSync(reactManifestFile))("React sample manifest (samples/react)", () => {
  const text = existsSync(reactManifestFile) ? readFileSync(reactManifestFile, "utf8") : "";
  const manifest = text ? decodeManifest(text, reactManifestFile) : (null as never);

  it("re-encodes byte for byte and is valid", () => {
    expect(encodeManifest(manifest)).toBe(text);
    expect(manifestProblems(manifest)).toEqual([]);
  });

  it("matches the JSON schema", () => {
    const validate = new Ajv({ allErrors: true }).compile(schema);
    expect(validate(JSON.parse(text)), JSON.stringify(validate.errors)).toBe(true);
  });

  it.runIf(existsSync(reactGoldens))("expands to exactly the committed goldens", () => {
    expect(expandManifest(manifest).map((v) => v.stem).sort()).toEqual(pngStems(reactGoldens));
  });
});

describe("decoding", () => {
  const base = { schema: "vcase/1", project: "p", defaults: { theme: ["light"], viewport: [{ name: "phone", w: 1, h: 1, density: 1 }], locale: ["en"], pointer: ["touch"] }, cases: [] };

  it("rejects unknown keys like kotlinx (ignoreUnknownKeys = false)", () => {
    expect(() => decodeManifest(JSON.stringify({ ...base, extra: 1 }))).toThrow(/unknown key 'extra'/);
    expect(() => decodeManifest(JSON.stringify({ ...base, cases: [{ id: "a.b", source: "x", key: "k" }] }))).toThrow(/unknown key 'key'/);
  });

  it("rejects another schema", () => {
    expect(() => decodeManifest(JSON.stringify({ ...base, schema: "vcase/2" }))).toThrow(/unsupported manifest schema/);
  });

  it("fills Kotlin defaults and writes them back", () => {
    const m = decodeManifest(JSON.stringify({ ...base, cases: [{ id: "a.b", source: "x.tsx:1" }] }));
    expect(m.cases[0]).toEqual({ id: "a.b", kind: "component", source: "x.tsx:1", fixture: null, axes: {}, parity: [], divergences: [] });
    expect(encodeManifest(m)).toContain('"fixture": null');
    expect(encodeManifest(m)).toContain('"density": 1.0');
    expect(encodeManifest(m)).not.toContain("fontScale");
  });
});
