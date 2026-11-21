import { PNG } from "pngjs";

/**
 * How close two pixels must be. The default (`{}`) is the zero-tolerance rule of same-platform
 * regression: every RGBA channel identical, same size. Loosen it only for an unpinned renderer.
 */
export interface Tolerance {
  /** Largest allowed difference of any RGBA channel (0-255). Default 0. */
  channelDelta?: number;
  /** Pixels that may differ before the comparison fails. Default 0. */
  maxDiffPixels?: number;
}

export interface Image {
  width: number;
  height: number;
  /** RGBA, non-premultiplied, row-major. */
  data: Uint8Array;
}

export interface CompareResult {
  expectedWidth: number;
  expectedHeight: number;
  actualWidth: number;
  actualHeight: number;
  /** Differing pixels, counting every pixel outside the overlap of two differently sized images. */
  mismatched: number;
  total: number;
  /** `[x, y, w, h]` of the mismatched pixels, null when none. */
  bounds: [number, number, number, number] | null;
  sizeMatches: boolean;
  matches: boolean;
  /** Actual faded to grey with mismatches in red; null when the images match. */
  diff: Image | null;
}

export function readPng(bytes: Uint8Array): Image {
  const png = PNG.sync.read(Buffer.from(bytes));
  return { width: png.width, height: png.height, data: new Uint8Array(png.data.buffer, png.data.byteOffset, png.data.length) };
}

export function writePng(img: Image): Buffer {
  const png = new PNG({ width: img.width, height: img.height });
  Buffer.from(img.data.buffer, img.data.byteOffset, img.data.length).copy(png.data);
  return PNG.sync.write(png);
}

export function describeCompare(r: CompareResult): string {
  if (r.matches && r.mismatched === 0) return "identical";
  if (!r.sizeMatches) return `size ${r.expectedWidth}x${r.expectedHeight} -> ${r.actualWidth}x${r.actualHeight}, ${r.mismatched} px differ`;
  const b = r.bounds!;
  return `${r.mismatched} of ${r.total} px differ in [${b[0]},${b[1]} ${b[2]}x${b[3]}]`;
}

/** Pixel comparison with the same rules and diff picture as vcase-core's `ImageCompare`. */
export function compareImages(expected: Image, actual: Image, tolerance: Tolerance = {}): CompareResult {
  const delta = tolerance.channelDelta ?? 0;
  const w = Math.max(expected.width, actual.width);
  const h = Math.max(expected.height, actual.height);
  const diff = new Uint8Array(w * h * 4);
  let mismatched = 0;
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -1;
  let maxY = -1;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const inE = x < expected.width && y < expected.height;
      const inA = x < actual.width && y < actual.height;
      const ei = (y * expected.width + x) * 4;
      const ai = (y * actual.width + x) * 4;
      let same = inE && inA;
      if (same) {
        for (let c = 0; c < 4; c++) {
          if (Math.abs(expected.data[ei + c]! - actual.data[ai + c]!) > delta) {
            same = false;
            break;
          }
        }
      }
      const di = (y * w + x) * 4;
      if (!same) {
        mismatched++;
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
        diff[di] = 255;
        diff[di + 1] = 0;
        diff[di + 2] = 0;
        diff[di + 3] = 255;
      } else {
        const v = faded(actual.data[ai]!, actual.data[ai + 1]!, actual.data[ai + 2]!, actual.data[ai + 3]!);
        diff[di] = v;
        diff[di + 1] = v;
        diff[di + 2] = v;
        diff[di + 3] = 255;
      }
    }
  }
  const sizeMatches = expected.width === actual.width && expected.height === actual.height;
  const matches = sizeMatches && mismatched <= (tolerance.maxDiffPixels ?? 0);
  return {
    expectedWidth: expected.width,
    expectedHeight: expected.height,
    actualWidth: actual.width,
    actualHeight: actual.height,
    mismatched,
    total: w * h,
    bounds: maxX < 0 ? null : [minX, minY, maxX - minX + 1, maxY - minY + 1],
    sizeMatches,
    matches,
    diff: mismatched === 0 ? null : { width: w, height: h, data: diff },
  };
}

/** Grey at a quarter contrast over white, so red mismatches stand out. */
function faded(r: number, g: number, b: number, a: number): number {
  const lum = Math.floor((r * 299 + g * 587 + b * 114) / 1000);
  const onWhite = Math.floor((lum * a + 255 * (255 - a)) / 255);
  return 255 - Math.floor((255 - onWhite) / 4);
}
