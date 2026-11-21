// raft-design style: named exports only, the doc site picks `*Composition`.
import { vcase } from "../../../../../../src/index.js";

// @docs-source-strip-start
export const meta = vcase({ id: "ui.button.default", parity: ["mobile:ds.button.variants"] });
// @docs-source-strip-end

export function DefaultComposition() {
  return null;
}
