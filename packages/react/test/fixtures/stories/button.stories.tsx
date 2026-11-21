import { vcase } from "../../../src/index.js";

const meta = {
  title: "UI/Button",
  component: () => null,
  parameters: { layout: "centered", vcase: { theme: ["light"] } },
} satisfies Record<string, unknown>;
export default meta;

type Story = { args?: Record<string, unknown>; parameters?: Record<string, unknown> };

export const Primary: Story = { args: { label: "Primary" } };

export const PrimaryLarge: Story = {
  args: { size: "lg" },
  parameters: {
    vcase: vcase({ id: "ui.button.large", parity: ["mobile:ds.button.large"], pointer: ["touch", "fine"] }),
  },
};

export const disabledHelper = 1;
