import { describe, expect, it } from "vitest";
import { clockProblems, DEFAULT_CLOCK, frameClock } from "../src/node/index.js";

describe("frame clock", () => {
  it("starts at the configured clock, or the default one, and captures at frames * interval", () => {
    expect(frameClock("2030-05-06T07:08:09.010Z", 10, 16)).toEqual({ wallStart: Date.UTC(2030, 4, 6, 7, 8, 9, 10), capture: 160 });
    expect(frameClock(undefined, 3, 100)).toEqual({ wallStart: new Date(DEFAULT_CLOCK).getTime(), capture: 300 });
    expect(frameClock(null, 2, 0)).toEqual({ wallStart: new Date(DEFAULT_CLOCK).getTime(), capture: 0 });
  });

  it("rejects a clock that is not a date", () => {
    expect(() => frameClock("soon", 1, 16)).toThrow(/invalid clock/);
  });

  it("accepts only the exact expected instant", () => {
    const { wallStart } = frameClock(undefined, 10, 16);
    expect(clockProblems({ wall: wallStart, perf: 0 }, wallStart, 0, "before the first frame")).toEqual([]);
    // The flake this guards against: the page's performance.now() started a few real ms late.
    expect(clockProblems({ wall: wallStart, perf: 4 }, wallStart, 0, "before the first frame")).toEqual([
      `fake clock before the first frame: performance.now() = 4, Date.now() = ${wallStart}; expected 0 and ${wallStart}`,
    ]);
    expect(clockProblems({ wall: wallStart + 1000, perf: 160 }, wallStart + 160, 160, "at capture")).toHaveLength(1);
  });
});
