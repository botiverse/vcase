// The seven cases of samples/compose-desktop, declared the React way. The scan of this file
// must give the same manifest entries as the Kotlin scan (apart from `source`).
import { vcase } from "../../../src/index.js";

export const meta = vcase({ id: "ds.button.variants", pointer: ["touch", "fine"], parity: ["web:ui.button.variants"] });
export default function ButtonVariants() {
  return null;
}
