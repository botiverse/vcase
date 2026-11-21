import { vcase } from "@vcase/react";
import { Button } from "../components";

// Pairs with the Compose sample's ds.button.variants (report-only parity).
export const meta = vcase({ id: "ui.button.variants", pointer: ["touch", "fine"], parity: ["mobile:ds.button.variants"] });

export default function ButtonVariants() {
  return (
    <div className="column">
      <div className="row">
        <Button>Primary</Button>
        <Button kind="secondary">Secondary</Button>
        <Button kind="danger">Delete</Button>
      </div>
      <Button disabled>Disabled</Button>
    </div>
  );
}
