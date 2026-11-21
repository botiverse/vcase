import { vcase } from "@vcase/react";
import { Progress } from "../components";

// A JavaScript-driven animation, captured mid-way: on the fake clock, 10 frames of 16 ms put a
// 320 ms fill at exactly 160 ms, i.e. half full, on every run.
export const meta = vcase({ id: "ui.progress.filling", theme: ["light"], settleFrames: 10, frameIntervalMs: 16 });

export default function ProgressFilling() {
  return <Progress durationMs={320} />;
}
