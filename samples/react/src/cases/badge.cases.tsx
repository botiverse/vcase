import { vcase } from "@vcase/react";
import { Badge } from "../components";

// Dark theme only.
export const meta = vcase({ id: "ui.badge.counts", theme: ["dark"] });

export default function BadgeCounts() {
  return (
    <div className="row">
      <Badge count={1} />
      <Badge count={42} />
      <Badge count={1000} />
    </div>
  );
}
