import { describe, expect, it } from "vitest";
import { expandCase, manifestProblems, type ManifestCase, type ManifestDefaults } from "../src/node/index.js";

const defaults: ManifestDefaults = {
  theme: ["light", "dark"],
  viewport: [
    { name: "phone", w: 360, h: 640, density: 2 },
    { name: "wide", w: 960, h: 600, density: 1, optIn: true },
  ],
  locale: ["en"],
  pointer: ["touch"],
};
const c = (over: Partial<ManifestCase>): ManifestCase => ({ id: "ui.x.y", kind: "component", source: "x.tsx:1", fixture: null, axes: {}, parity: [], divergences: [], ...over });

describe("axis expansion", () => {
  it("names theme and viewport always, overridden axes only", () => {
    expect(expandCase(c({}), defaults).map((v) => v.stem)).toEqual(["ui.x.y@light,phone", "ui.x.y@dark,phone"]);
    expect(expandCase(c({ axes: { theme: ["dark"], pointer: ["touch", "fine"], fontScale: ["1", "1.3"] } }), defaults).map((v) => v.name)).toEqual([
      "dark,phone,touch,fs1",
      "dark,phone,touch,fs1.3",
      "dark,phone,fine,fs1",
      "dark,phone,fine,fs1.3",
    ]);
  });

  it("uses opt-in viewports only when named", () => {
    expect(expandCase(c({ axes: { viewport: ["phone", "wide"], theme: ["light"] } }), defaults).map((v) => v.name)).toEqual(["light,phone", "light,wide"]);
  });

  it("validates like ManifestValidation", () => {
    const m = { schema: "vcase/1", project: "p", defaults, cases: [c({ id: "Bad", axes: { pointer: ["pen"] } }), c({ kind: "screen", capture: { maxWidth: 10 } }), c({ id: "ui.a.b", divergences: [{ with: "mobile", kind: "k", why: "w" }] })] };
    expect(manifestProblems(m)).toEqual([
      "x.tsx:1: id 'Bad' must be lowercase",
      "Bad: unknown pointer 'pen'",
      "ui.x.y: maxWidth / maxHeight bound a component's content size; a screen is captured at its viewport",
      "ui.a.b: divergence with 'mobile' has no parity entry for that platform",
    ]);
  });
});
