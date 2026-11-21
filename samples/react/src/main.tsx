// The host page: renders exactly one case, the variant the renderer asked for in the URL.
// It is the React side of the Compose sample's SampleVCaseHost: theme, pointer, size class and
// font scale from the axes, components on the canvas with 16px around them.
import { useEffect, type ComponentType } from "react";
import { createRoot } from "react-dom/client";
import { markError, markReady, readVariant, ROOT_ATTRIBUTE, type VCaseMeta } from "@vcase/react";
import "./theme.css";

type CaseModule = { meta?: VCaseMeta } & Record<string, unknown>;
const modules = import.meta.glob<CaseModule>("./cases/*.cases.tsx");
const keyOf = (path: string) => path.replace("./cases/", "").replace(".cases.tsx", "");

function Host({ Case, screen }: { Case: ComponentType; screen: boolean }) {
  useEffect(markReady, []);
  const root = { [ROOT_ATTRIBUTE]: "" };
  return (
    <div className={screen ? "case-screen" : "case-component"} {...root}>
      <Case />
    </div>
  );
}

async function main() {
  const container = document.getElementById("root")!;
  const variant = readVariant();
  if (!variant) {
    // Opened by hand: list the cases.
    container.innerHTML = Object.keys(modules)
      .map(keyOf)
      .map((k) => `<p><a href="?case=${k}&theme=light">${k}</a> · <a href="?case=${k}&theme=dark">dark</a></p>`)
      .join("");
    return;
  }
  const html = document.documentElement;
  html.dataset.theme = variant.theme;
  html.dataset.pointer = variant.pointer;
  if (variant.sizeClass) html.dataset.sizeClass = variant.sizeClass;
  html.lang = variant.locale;
  html.style.fontSize = `${16 * variant.fontScale}px`;

  const load = modules[`./cases/${variant.key}.cases.tsx`];
  if (!load) return markError(`no case file for key '${variant.key}'`);
  const mod = await load();
  const Case = mod[variant.exportName] as ComponentType | undefined;
  if (typeof Case !== "function") return markError(`'${variant.key}' has no export '${variant.exportName}'`);
  // Load both weights before the first paint, so text never renders in a fallback font.
  const faces = await Promise.all([document.fonts.load("400 16px 'Space Grotesk'"), document.fonts.load("700 16px 'Space Grotesk'")]);
  if (faces.some((f) => f.length === 0)) return markError("Space Grotesk did not load");
  createRoot(container).render(<Host Case={Case} screen={mod.meta?.kind === "screen"} />);
}

main().catch((e: unknown) => markError(String(e)));
