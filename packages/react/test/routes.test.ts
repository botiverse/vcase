import { mkdtempSync, mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { compileRoutes, contentTypeOf, findRoute, routeContentType, routeMatches, routeProblems } from "../src/node/index.js";

describe("route matching", () => {
  it("treats * as any run of characters and everything else literally", () => {
    const css = "https://fonts.googleapis.com/css2*";
    expect(routeMatches(css, "https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@300..700&display=swap")).toBe(true);
    expect(routeMatches(css, "https://fonts.googleapis.com/css2")).toBe(true);
    expect(routeMatches(css, "https://fonts.googleapis.com/css?family=X")).toBe(false);
    expect(routeMatches(css, "http://fonts.googleapis.com/css2?family=X")).toBe(false);
    expect(routeMatches("https://fonts.gstatic.com/*", "https://fonts.gstatic.com/s/spacemono/v17/i7dPIFZifjKcF5UAWdDRYEF8RQ.woff2")).toBe(true);
    expect(routeMatches("https://fonts.gstatic.com/*", "https://fonts.gstatic.com.evil.test/x.woff2")).toBe(false);
  });

  it("does not read ? . + ( as pattern syntax", () => {
    expect(routeMatches("https://a.test/x?y=1", "https://a.test/x?y=1")).toBe(true);
    expect(routeMatches("https://a.test/x?y=1", "https://a.test/xy=1")).toBe(false);
    expect(routeMatches("https://a.test/a.css", "https://a.test/aXcss")).toBe(false);
    expect(routeMatches("https://a.test/(a)+", "https://a.test/(a)+")).toBe(true);
  });

  it("is anchored at both ends", () => {
    expect(routeMatches("https://a.test/f.woff2", "https://a.test/f.woff2?v=2")).toBe(false);
    expect(routeMatches("a.test/f.woff2", "https://a.test/f.woff2")).toBe(false);
  });

  it("tests a RegExp against the full URL, also a global one repeatedly", () => {
    const re = /^https:\/\/fonts\.gstatic\.com\/.*\.woff2$/g;
    expect(routeMatches(re, "https://fonts.gstatic.com/s/a.woff2")).toBe(true);
    expect(routeMatches(re, "https://fonts.gstatic.com/s/a.woff2")).toBe(true);
    expect(routeMatches(re, "https://fonts.gstatic.com/s/a.ttf")).toBe(false);
  });

  it("first match wins", () => {
    const routes = [
      { name: "a", matches: (u: string) => routeMatches("https://x.test/a*", u) },
      { name: "all", matches: (u: string) => routeMatches("https://x.test/*", u) },
    ];
    expect(findRoute(routes, "https://x.test/ab")?.name).toBe("a");
    expect(findRoute(routes, "https://x.test/b")?.name).toBe("all");
    expect(findRoute(routes, "https://y.test/a")).toBeUndefined();
  });
});

describe("content type", () => {
  it("is inferred from the extension", () => {
    expect(contentTypeOf("fonts/SpaceMono-Bold.woff2")).toBe("font/woff2");
    expect(contentTypeOf("a.WOFF")).toBe("font/woff");
    expect(contentTypeOf("a.ttf")).toBe("font/ttf");
    expect(contentTypeOf("vcase/fonts.css")).toBe("text/css; charset=utf-8");
    expect(contentTypeOf("a.json")).toBe("application/json; charset=utf-8");
    expect(contentTypeOf("a.svg")).toBe("image/svg+xml");
    expect(contentTypeOf("a.png")).toBe("image/png");
    expect(contentTypeOf("a.unknown")).toBeNull();
    expect(contentTypeOf("noext")).toBeNull();
  });

  it("prefers contentType, then the file, then the URL path", () => {
    const url = "https://fonts.googleapis.com/css2?family=X";
    expect(routeContentType({ file: "fonts.css", contentType: "text/plain" }, url)).toBe("text/plain");
    expect(routeContentType({ file: "fonts.css" }, url)).toBe("text/css; charset=utf-8");
    expect(routeContentType({ file: "blob" }, "https://a.test/f.woff2?v=1")).toBe("font/woff2");
    expect(routeContentType({ file: "blob" }, url)).toBe("application/octet-stream");
    expect(routeContentType({ body: "{}" }, "https://a.test/api/x.json")).toBe("application/json; charset=utf-8");
    expect(routeContentType({ body: "hi" }, url)).toBe("text/plain; charset=utf-8");
  });
});

describe("route config", () => {
  const dir = mkdtempSync(path.join(tmpdir(), "vcase-routes-"));
  mkdirSync(path.join(dir, "fonts"));
  writeFileSync(path.join(dir, "fonts", "a.woff2"), "woff2");
  writeFileSync(path.join(dir, "fonts.css"), "@font-face{}");

  it("accepts file or body routes", () => {
    expect(routeProblems(undefined, dir)).toEqual([]);
    expect(routeProblems([{ url: "https://a.test/*", file: "fonts/a.woff2" }, { url: /x/, body: "", status: 204, contentType: "text/plain" }], dir)).toEqual([]);
  });

  it("reports a missing file, both or neither of file and body, and bad fields", () => {
    const problems = routeProblems(
      [
        { url: "https://a.test/*", file: "fonts/missing.woff2" },
        { url: "https://a.test/*", file: "fonts.css", body: "x" },
        { url: "https://a.test/*" },
        { url: "", body: "x", status: 42 },
        { url: "https://a.test/*", file: "fonts" },
      ],
      dir,
    );
    expect(problems).toHaveLength(6);
    expect(problems[0]).toMatch(/^routes\[0\] .*file not found: .*missing\.woff2$/);
    expect(problems[1]).toMatch(/^routes\[1\] .*exactly one of `file` and `body`/);
    expect(problems[2]).toMatch(/^routes\[2\] .*exactly one of `file` and `body`/);
    expect(problems[3]).toMatch(/^routes\[3\]: `url` must be/);
    expect(problems[4]).toMatch(/^routes\[3\]: `status`/);
    expect(problems[5]).toMatch(/^routes\[4\] .*file not found/);
    expect(routeProblems({}, dir)).toEqual(["`routes` must be an array"]);
  });

  it("compiles to bodies and headers, relative to the config directory", () => {
    const [font, css, inline] = compileRoutes(
      [
        { url: "https://fonts.gstatic.com/*", file: "fonts/a.woff2" },
        { url: "https://fonts.googleapis.com/css2*", file: "fonts.css" },
        { url: "https://api.test/ping", body: "pong", status: 201 },
      ],
      dir,
    );
    expect(font!.body.toString()).toBe("woff2");
    expect(font!.status).toBe(200);
    expect(font!.headers("https://fonts.gstatic.com/s/x.ttf")).toEqual({ "content-type": "font/woff2", "access-control-allow-origin": "*" });
    expect(css!.matches("https://fonts.googleapis.com/css2?family=X")).toBe(true);
    expect(css!.headers("https://fonts.googleapis.com/css2?family=X")["content-type"]).toBe("text/css; charset=utf-8");
    expect(inline!.body.toString()).toBe("pong");
    expect(inline!.status).toBe(201);
    expect(() => compileRoutes([{ url: "https://a.test/", body: "x", file: "fonts.css" }], dir)).toThrow(/exactly one/);
  });
});
