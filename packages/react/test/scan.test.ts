import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { scan, storyNameFromExport, sanitize, variantUrl, expandCase, keyOf } from "../src/node/index.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const fx = (p: string) => path.join(here, "fixtures", p);

describe("cases files", () => {
  it("reads meta statically, with its source line and the default export", () => {
    const r = scan({ cwd: fx("kotlin-sample"), sources: [{ glob: "inbox.cases.tsx" }], sourceRoot: fx(".") });
    expect(r.problems).toEqual([]);
    const c = r.cases[0]!;
    expect(c.entry.source).toBe("kotlin-sample/inbox.cases.tsx:3");
    expect(c.entry.kind).toBe("screen");
    expect(c.entry.divergences[0]).toEqual({
      with: "web",
      region: "action",
      kind: "platform-difference",
      why: "The web page puts the action in its top bar, not under the hint",
      decidedBy: "sample",
      ref: "README#divergences",
    });
    expect(Object.keys(c.entry.divergences[0]!)).toEqual(["with", "region", "kind", "why", "decidedBy", "ref"]);
    expect(c.key).toBe("inbox.cases");
    expect(c.exportName).toBe("default");
  });

  it("writes capture settings like Kotlin, fontScale as strings", () => {
    const r = scan({ cwd: fx("kotlin-sample"), sources: [{ glob: ["tooltip.cases.tsx", "card.cases.tsx"], suffix: ".cases.tsx" }] });
    const byId = Object.fromEntries(r.cases.map((c) => [c.entry.id, c]));
    expect(byId["ds.tooltip.open"]!.entry.capture).toEqual({ settleFrames: 2, frameIntervalMs: 0 });
    expect(byId["ds.card.default"]!.entry.axes).toEqual({ fontScale: ["1", "1.3"] });
    expect(byId["ds.card.default"]!.key).toBe("card");
  });

  it("reports computed values and unknown fields with file:line", () => {
    const r = scan({ cwd: fx("bad"), sources: [{ glob: "*.cases.tsx" }], sourceRoot: fx(".") });
    expect(r.cases).toEqual([]);
    expect(r.problems).toEqual([
      "bad/computed.cases.tsx:4: not a literal: `themes` (vcase meta is read without running the file)",
      "bad/typo.cases.tsx: unknown vcase meta field 'pointers'",
    ]);
  });
});

describe("raft-design style compositions", () => {
  it("keys files by their path under the base, without the suffix; files without meta are skipped", () => {
    const r = scan({
      cwd: fx("compositions"),
      sources: [{ glob: "modules/**/*.composition.tsx", base: "modules", suffix: ".composition.tsx" }],
      sourceRoot: fx("compositions"),
    });
    expect(r.problems).toEqual([]);
    expect(r.skipped).toEqual(["modules/button/compositions/split.composition.tsx"]);
    expect(r.cases).toHaveLength(1);
    const c = r.cases[0]!;
    expect(c.key).toBe("button/compositions/default");
    expect(c.exportName).toBeNull();
    expect(c.entry.source).toBe("modules/button/compositions/default.composition.tsx:5");
    const v = expandCase(c.entry, { theme: ["light", "dark"], viewport: [{ name: "phone", w: 375, h: 560, density: 2 }], locale: ["en"], pointer: ["touch"] })[1]!;
    expect(variantUrl({ baseURL: "http://localhost:3000", url: "/iframe/example?path={key}&mode={theme}" }, c, v)).toBe(
      "http://localhost:3000/iframe/example?path=button%2Fcompositions%2Fdefault&mode=dark",
    );
  });
});

describe("Storybook CSF", () => {
  const r = scan({ cwd: fx("stories"), sources: [{ glob: "*.stories.tsx" }], sourceRoot: fx(".") });

  it("makes every story a case, with derived ids and story ids", () => {
    expect(r.problems).toEqual([]);
    expect(r.cases.map((c) => [c.entry.id, c.exportName, c.storyId])).toEqual([
      ["ui.button.large", "PrimaryLarge", "ui-button--primary-large"],
      ["ui.button.primary", "Primary", "ui-button--primary"],
    ]);
  });

  it("merges file-level parameters.vcase under story-level ones", () => {
    const large = r.cases.find((c) => c.exportName === "PrimaryLarge")!.entry;
    expect(large.axes).toEqual({ theme: ["light"], pointer: ["touch", "fine"] });
    expect(large.parity).toEqual(["mobile:ds.button.large"]);
    expect(large.source).toBe("stories/button.stories.tsx:14");
  });

  it("opt-in mode keeps only stories with vcase parameters (here: all, through the file-level one)", () => {
    const opt = scan({ cwd: fx("stories"), sources: [{ glob: "*.stories.tsx", allStories: false }] });
    expect(opt.cases).toHaveLength(2);
  });

  it("follows Storybook naming", () => {
    expect(storyNameFromExport("PrimaryLarge")).toBe("Primary Large");
    expect(sanitize("UI/Button")).toBe("ui-button");
    expect(keyOf("/a/b/c.stories.tsx", "/a", { suffix: ".stories.tsx" })).toBe("b/c");
  });
});
