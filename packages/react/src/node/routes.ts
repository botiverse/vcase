import { existsSync, readFileSync, statSync } from "node:fs";
import path from "node:path";

/**
 * A request answered locally instead of over the network, e.g. a web font CDN served from the
 * repository's own font files, so a CDN update never changes a golden.
 */
export interface RouteConfig {
  /**
   * The full request URL. A string is a glob in which `*` matches any run of characters
   * (including `/` and `?`) and everything else is literal, e.g. `https://fonts.googleapis.com/css2*`.
   * A `RegExp` (in `.mjs` / `.js` configs) is tested against the full URL.
   */
  url: string | RegExp;
  /** File answered, relative to the config file. Exactly one of `file` and `body`. */
  file?: string;
  /** Inline response body. Exactly one of `file` and `body`. */
  body?: string;
  /** `content-type`. Default: from the extension of `file`, else of the URL path; else `application/octet-stream` (`text/plain` for `body`). */
  contentType?: string;
  /** Default 200. */
  status?: number;
}

/** A validated route with its body read. */
export interface CompiledRoute {
  matches(url: string): boolean;
  status: number;
  /** Response headers for a request to [url]. */
  headers(url: string): Record<string, string>;
  body: Buffer;
}

const CONTENT_TYPES: Record<string, string> = {
  ".css": "text/css; charset=utf-8",
  ".woff2": "font/woff2",
  ".woff": "font/woff",
  ".ttf": "font/ttf",
  ".otf": "font/otf",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".js": "text/javascript; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".txt": "text/plain; charset=utf-8",
};

/** `content-type` for a file name or URL path by its extension; null when unknown. */
export function contentTypeOf(name: string): string | null {
  return CONTENT_TYPES[path.posix.extname(name).toLowerCase()] ?? null;
}

/** The glob of {@link RouteConfig.url} as an anchored RegExp: `*` is any run of characters, the rest literal. */
export function globToRegExp(glob: string): RegExp {
  return new RegExp(`^${glob.split("*").map((s) => s.replace(/[.+?^${}()|[\]\\]/g, "\\$&")).join(".*")}$`);
}

/** Whether [url] (a full request URL) matches the route's pattern. */
export function routeMatches(pattern: string | RegExp, url: string): boolean {
  if (pattern instanceof RegExp) {
    pattern.lastIndex = 0;
    return pattern.test(url);
  }
  return globToRegExp(pattern).test(url);
}

/** The first route matching [url], or undefined. */
export function findRoute<R extends { matches(url: string): boolean }>(routes: readonly R[], url: string): R | undefined {
  return routes.find((r) => r.matches(url));
}

/** The response's `content-type` (see {@link RouteConfig.contentType}). */
export function routeContentType(route: Pick<RouteConfig, "file" | "body" | "contentType">, url: string): string {
  if (route.contentType) return route.contentType;
  if (route.file != null) return contentTypeOf(route.file) ?? contentTypeOf(pathnameOf(url)) ?? "application/octet-stream";
  return contentTypeOf(pathnameOf(url)) ?? "text/plain; charset=utf-8";
}

function pathnameOf(url: string): string {
  try {
    return new URL(url).pathname;
  } catch {
    return "";
  }
}

const label = (r: RouteConfig) => (r.url instanceof RegExp ? String(r.url) : JSON.stringify(r.url));

/** Problems of `routes` (an empty list when valid); `file`s resolve against [dir]. */
export function routeProblems(routes: unknown, dir: string): string[] {
  if (routes === undefined) return [];
  if (!Array.isArray(routes)) return ["`routes` must be an array"];
  const problems: string[] = [];
  routes.forEach((r: RouteConfig, i) => {
    const at = `routes[${i}]`;
    if (r == null || typeof r !== "object") return problems.push(`${at}: must be an object`);
    if (!(r.url instanceof RegExp) && (typeof r.url !== "string" || r.url === "")) problems.push(`${at}: \`url\` must be a non-empty string or a RegExp`);
    if ((r.file == null) === (r.body == null)) problems.push(`${at} (${label(r)}): exactly one of \`file\` and \`body\` is required`);
    if (r.file != null) {
      if (typeof r.file !== "string") problems.push(`${at}: \`file\` must be a string`);
      else {
        const f = path.resolve(dir, r.file);
        if (!existsSync(f) || !statSync(f).isFile()) problems.push(`${at} (${label(r)}): file not found: ${f}`);
      }
    }
    if (r.body != null && typeof r.body !== "string") problems.push(`${at}: \`body\` must be a string`);
    if (r.contentType != null && typeof r.contentType !== "string") problems.push(`${at}: \`contentType\` must be a string`);
    if (r.status != null && (!Number.isInteger(r.status) || r.status < 100 || r.status > 599)) problems.push(`${at}: \`status\` must be an HTTP status code`);
  });
  return problems;
}

/** Validates `routes` (throws on any problem) and reads their bodies. */
export function compileRoutes(routes: RouteConfig[] | undefined, dir: string): CompiledRoute[] {
  const problems = routeProblems(routes, dir);
  if (problems.length > 0) throw new Error(problems.join("\n"));
  return (routes ?? []).map((r) => {
    const re = r.url instanceof RegExp ? r.url : globToRegExp(r.url);
    return {
      matches: (url: string) => routeMatches(re, url),
      status: r.status ?? 200,
      // Cross-origin font and stylesheet loads are CORS requests: allow any origin.
      headers: (url: string) => ({ "content-type": routeContentType(r, url), "access-control-allow-origin": "*" }),
      body: r.file != null ? readFileSync(path.resolve(dir, r.file)) : Buffer.from(r.body!, "utf8"),
    };
  });
}
