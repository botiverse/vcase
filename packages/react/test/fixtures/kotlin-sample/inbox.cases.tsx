import { vcase } from "../../../src/index.js";

export const meta = vcase({
  id: "screen.inbox.empty",
  kind: "screen",
  viewport: ["phone", "wide"],
  parity: ["web:screens.inbox.empty"],
  divergences: [
    {
      with: "web",
      region: "action",
      why: "The web page puts the action in its top bar, not under the hint",
      decidedBy: "sample",
      ref: "README#divergences",
    },
  ],
});
export default () => null;
