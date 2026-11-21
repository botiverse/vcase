import { vcase } from "@vcase/react";
import { Card } from "../components";

// Text grows with the font scale; the 280px card does not.
export const meta = vcase({ id: "ui.card.default", fontScale: [1, 1.3] });

export default function CardDefault() {
  return <Card title="Weekly sync" body="Notes from Monday are ready to review." />;
}
