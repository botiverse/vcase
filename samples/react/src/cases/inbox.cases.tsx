import { vcase } from "@vcase/react";
import { EmptyState } from "../components";

export const meta = vcase({
  id: "screens.inbox.empty",
  kind: "screen",
  viewport: ["phone", "wide"],
  parity: ["mobile:screen.inbox.empty"],
  divergences: [
    {
      with: "mobile",
      region: "action",
      why: "The app puts the action under the hint; the web page will move it to its top bar",
      decidedBy: "sample",
      ref: "README#react",
    },
  ],
});

export default function InboxEmpty() {
  return <EmptyState title="Nothing here yet" hint="New messages show up in this list." action="Compose" />;
}
