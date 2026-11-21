# vcase

Visual cases for UI code. You declare each UI state once in source code, give it an id,
and vcase renders it, compares it with a committed golden image, and writes a report.
It works with any Kotlin Compose project and any React project (`@vcase/react`), and both
write the same manifest.

Status: phase 1. The Kotlin core, the Compose Desktop adapter and the React adapter work and are
tested. Nothing is published yet; see [Using it before a release](#using-it-before-a-release).

- Design: [docs/design.md](docs/design.md)
- First adoption, the Raft mobile design system: [docs/pilot-raft-mobile.md](docs/pilot-raft-mobile.md)

## Ideas

- **A case is written once.** It is a plain `@Composable` function with `@VCase("ds.button.variants")`.
  It is not a test class. The manifest (`vcase.manifest.json`) is produced by scanning the code.
  Nobody edits it by hand.
- **Axes are expanded for you.** Theme, viewport, locale, pointer (touch or fine) and font
  scale have project defaults. A case lists only the axes it changes, and vcase renders the
  cartesian product.
- **Same-platform regression has zero tolerance.** Every ARGB value must be equal, and so
  must the size. The renderer and fonts are pinned. When one of them changes, you re-record
  that renderer's goldens and say why in the PR.
- **Goldens are stored per renderer.** `goldens/desktop-skiko/`, `goldens/android-robolectric/`, ...
  Switching a renderer re-records only that renderer's set.
- **Cross-platform comparison is declared, not tuned.** `parity = ["web:ui.button.variants"]`
  pairs a case with its web counterpart. Decided differences are written as divergences with
  a reason and a reference. Cross-platform results are report-only for now.

## Kotlin Compose quick start

### 1. Write cases

```kotlin
import dev.vcase.VCase
import dev.vcase.VCaseKind

@VCase("ds.button.variants", pointer = ["touch", "fine"], parity = ["web:ui.button.variants"])
@Composable
fun ButtonVariantsCase() {
    Row { MyButton("Primary"); MyButton("Secondary", secondary = true) }
}

@VCase("screen.inbox.empty", kind = VCaseKind.Screen, viewport = ["phone", "wide"])
@Composable
fun InboxEmptyCase() {
    InboxEmpty()
}
```

`vcase-annotations` has no dependencies and is Kotlin Multiplatform (jvm, iosArm64,
iosSimulatorArm64, js, wasmJs). You can use it from `commonMain`. Android modules resolve
its jvm variant. Put cases wherever suits the project: in the test source set (they never
ship), or next to `@Preview` functions.

Id rules: lowercase segments separated by dots, at least two segments. Use `ds.*` for
design-system components, `screen.*` for screens and `part.*` for parts of a screen. Ids
never name a platform. The same screen on the app and on the web has the same id.

### 2. Implement a host

The host is how your project renders a case: theme for the axis values, background,
"freeze" settings, size. vcase knows nothing about your theme.

```kotlin
object MyVCaseHost : VCaseHost {
    override val project = "my-app"
    override val defaults = ManifestDefaults(
        theme = listOf("light", "dark"),
        viewport = listOf(
            ManifestViewport("phone", 390, 844, 3f),
            ManifestViewport("wide", 1280, 800, 2f, optIn = true), // only cases that name it
        ),
        locale = listOf("en"),
        pointer = listOf("touch"),
    )

    @Composable
    override fun Wrap(variant: VCaseVariant, content: @Composable () -> Unit) {
        MyTheme(dark = variant.axes.theme == "dark", touch = variant.axes.pointer == VCasePointer.Touch) {
            Box(Modifier.background(MyTheme.colors.canvas).padding(16.dp)) { content() }
        }
    }
}
```

Optional hooks:
- `captureSize(variant)`: by default components are captured at content size and screens at the viewport.
- `settleFrames` and `frameTimeNanos(i)`: every frame is rendered at a fixed time.
- `beforeCapture` / `afterCapture`: for example `Locale.setDefault`, or a fixed clock.
- `fontsHash`: written to the metadata of every capture.
- `renderer`: the golden directory name.

Inside a case, `LocalVCaseVariant.current` gives the axes and the fixture name.

### Per-case capture settings

A case can change how it is captured. Every setting is optional, defaults to the host's
behaviour, and is written to the manifest's `capture` object only when set, so existing
cases, manifests and goldens are unchanged.

| `@VCase` parameter | default | effect |
|---|---|---|
| `maxWidth` | `0` (host's bound, by default the viewport width) | width bound in dp of a component capture, for content wider than the host allows (a wide gallery) |
| `maxHeight` | `0` (host's bound, by default 4x the viewport height) | height bound in dp of a component capture |
| `settleFrames` | `-1` (host's `settleFrames`) | frames rendered before the captured one, e.g. `2` for a popup that lays itself out a frame late |
| `frameIntervalMs` | `-1` (host's `frameTimeNanos`) | frame `i` renders at `i * frameIntervalMs` ms; `0` renders every frame at time 0 |

```kotlin
@VCase("ds.choice-list.gallery", maxWidth = 1600)                        // wider than the 1200dp gallery bound
@VCase("ds.tooltip.open", settleFrames = 2, frameIntervalMs = 0)          // popup settled, animations not started
@VCase("ds.toast.shown", settleFrames = 20, frameIntervalMs = 16)         // a 300 ms fade-in has finished
```

- `maxWidth` / `maxHeight` replace the bounds of the host's `CaptureSize.WrapContent`. A
  screen is captured at its viewport, so the manifest check rejects them on screens.
- `settleFrames` applies to the capture pass. The measure pass of a component (which finds
  its content size) keeps the host's count.
- `settleFrames = N, frameIntervalMs = 0` matches a hand-written
  `repeat(N) { scene.render() }; scene.render()` on an `ImageComposeScene`: state, effects
  and popups settle, while time-driven animations stay at their start. Give a positive
  interval to let animations run.

### Wide layouts and the mouse

The pointer axis (`touch`, `fine`) is how a case asks for mouse sizing:
`pointer = ["fine"]`, or `["touch", "fine"]` for both. A wide layout (a desktop type scale,
larger panes) is a viewport with a size class:

```kotlin
ManifestViewport("wide", 1200, 2400, 2f, optIn = true, sizeClass = VCaseSizeClass.WIDE)

@VCase("ds.desktop-scale.gallery", viewport = ["wide"], pointer = ["fine", "touch"])
```

The host reads `variant.axes.viewport.sizeClass` and `variant.axes.pointer` in `Wrap` and
pins the theme's layout and input modes with them. It should not derive them from the
capture size: a component is captured at its content size, which says nothing about the
window it was designed for. `sizeClass` is free text (`VCaseSizeClass.COMPACT` and `WIDE` are
the conventional names). It is not an axis of its own, so it never appears in file names.

### 3. Add one test class

```kotlin
class MyVCases : VCaseDesktopTest() {
    override val host = MyVCaseHost
    override val packages = listOf("com.example.ui")
}
```

Wire the `vcase.*` system properties in the module that runs the test (see
`samples/compose-desktop/build.gradle.kts`):

```kotlin
tasks.test {
    providers.gradleProperty("vcase.mode").orNull?.let { systemProperty("vcase.mode", it) }
    systemProperty("vcase.goldens", file("goldens").absolutePath)
    systemProperty("vcase.manifest", file("vcase.manifest.json").absolutePath)
    systemProperty("vcase.out", layout.buildDirectory.dir("vcase").get().asFile.absolutePath)
}
```

| command | effect |
|---|---|
| `./gradlew test` | verify: the manifest must match the scan, and every capture must equal its golden |
| `./gradlew test -Pvcase.mode=record` | write new and changed goldens, delete orphans, rewrite the manifest |
| `./gradlew test -Pvcase.mode=scan` | rewrite the manifest only |
| `-Pvcase.filter='ds.button.*,screen.*'` | only these ids (orphan detection is off) |

If you prefer not to subclass, call `runVCases(host, VCaseDesktopConfig(...))` directly. It
returns a `VCaseRunResult` (`ok`, `describe()`, `assertOk()`).

A Gradle plugin with `vcaseScan` / `vcaseRecord` / `vcaseVerify` tasks is planned. Until it
exists, the property wiring above is the whole integration.

### Output

```
goldens/desktop-skiko/ds.button.variants@light,phone,touch.png   committed golden
vcase.manifest.json                                              committed manifest
build/vcase/desktop-skiko/<id>@<axes>.png                        actual
build/vcase/desktop-skiko/<id>@<axes>.json                       metadata (adapter contract)
build/vcase/desktop-skiko/<id>@<axes>.diff.png / .expected.png   on failure
build/vcase/report/index.html                                    matrix + expected / actual / diff
```

File names are `<id>@<axis values>`. Theme and viewport are always part of the name. Locale,
pointer and font scale (`fs1.3`) appear only when the case overrides that axis, or when the
project default has more than one value for it.

## React

`@vcase/react` (in [`packages/react`](packages/react)) scans React case files into the same
`vcase.manifest.json` as the Kotlin side, captures every variant with Playwright Chromium, and
records or verifies goldens with the same rules: zero tolerance, one golden directory per
renderer (`goldens/web-chromium/`), file names `<id>@<axes>.png`.
[`samples/react`](samples/react) is a working project. See also the [design](docs/design.md#react-built).

### 1. Write cases

```tsx
// src/cases/button.cases.tsx
import { vcase } from "@vcase/react";

export const meta = vcase({ id: "ui.button.variants", pointer: ["touch", "fine"], parity: ["mobile:ds.button.variants"] });
export default function ButtonVariants() {
  return <ButtonGallery />;
}
```

`vcase({...})` takes the fields of Kotlin's `@VCase` under the same names: `id`, `kind`
(`"component"` or `"screen"`), `fixture`, `parity`, the axes `theme`, `viewport`, `locale`,
`pointer`, `fontScale`, plus `formerly`, `divergences`, `needsDevice`, and the capture settings
`maxWidth`, `maxHeight`, `settleFrames`, `frameIntervalMs`. The scanner reads `meta` with the
TypeScript parser and never runs the file, so every value must be a literal. A computed value
or an unknown field is a scan error with `file:line`.

Three kinds of source files can be scanned:

| source | what is a case | case key (`{key}`) |
|---|---|---|
| `*.cases.tsx` | the file: `export const meta = vcase(...)` + `export default` component | path under `base`, without `suffix` |
| any example glob, e.g. raft-design's `*.composition.tsx` | a file with `export const meta = vcase(...)`; files without it are skipped | same; the host page decides which export to render |
| Storybook CSF `*.stories.tsx` | every named story; the id comes from `title` + export name (`UI/Button` + `PrimaryLarge` gives `ui.button.primary-large`) unless `parameters: { vcase: vcase({...}) }` sets one | the file key, plus `{export}` and `{storyId}` (`ui-button--primary-large`) |

### 2. Serve a host page

The renderer opens one URL per variant. The host page renders only that case, applies the
axes, marks the case's root element with `data-vcase-root`, and calls `markReady()` once the
case has mounted. With the default URL template, `readVariant()` gives it the axes:

```tsx
import { markReady, readVariant, ROOT_ATTRIBUTE } from "@vcase/react";

const modules = import.meta.glob("./cases/*.cases.tsx");
const v = readVariant()!; // key, exportName, id, theme, viewport, locale, pointer, fontScale, sizeClass, fixture
document.documentElement.dataset.theme = v.theme;
const mod = await modules[`./cases/${v.key}.cases.tsx`]!();
// render <div data-vcase-root>{<mod.default />}</div>, then markReady() in an effect
```

[`samples/react/src/main.tsx`](samples/react/src/main.tsx) is a complete host. It is
the counterpart of the Kotlin `VCaseHost`: the theme for the axis values, the canvas and padding
around a component, the pointer and size class, the font scale (root font size).

An existing page that renders one example by URL works too: set `url` to its template, and
`rootSelector` / `readySelector` to what it renders.

### 3. Configure

```js
// vcase.config.mjs
import { defineConfig } from "@vcase/react/node";

export default defineConfig({
  project: "my-web",
  defaults: {
    theme: ["light", "dark"],
    viewport: [{ name: "phone", w: 360, h: 640, density: 2 }, { name: "wide", w: 1280, h: 800, density: 2, optIn: true }],
    locale: ["en"],
    pointer: ["touch"],
  },
  sources: [{ glob: "src/cases/*.cases.tsx", base: "src/cases", suffix: ".cases.tsx" }],
  baseURL: "http://127.0.0.1:4173",
  server: { command: "npx vite build && npx vite preview --port 4173 --strictPort" },
  parity: { mobile: "../mobile/goldens/desktop-skiko" },
});
```

| option | default | meaning |
|---|---|---|
| `project`, `defaults` | required | written to the manifest; the same shape as the Kotlin host's `ManifestDefaults` |
| `sources` | required | `{ glob, base?, suffix?, format?: "cases" \| "stories", allStories? }` |
| `baseURL` | required | where the host page is served |
| `url` | `/?case={key}&export={export}&id={id}&theme={theme}&…` | URL template; placeholders `{key}` `{export}` `{storyId}` `{id}` `{theme}` `{viewport}` `{w}` `{h}` `{density}` `{sizeClass}` `{locale}` `{pointer}` `{fontScale}` `{fixture}`, URL-encoded |
| `server` | none | `{ command, cwd?, timeoutMs?, reuseExisting? }`, started and stopped around the run |
| `rootSelector` | `[data-vcase-root]` | what a component case captures |
| `readySelector` | `html[data-vcase-ready]` | waited for before capturing; `null` to skip |
| `settleFrames` | `2` | animation frames before every capture (a case's `settleFrames` wins) |
| `clock` | `2026-01-01T00:00:00Z` | the page's `Date` (timers keep running); `null` for the real clock |
| `timezoneId`, `reducedMotion` | `UTC`, `reduce` | context emulation |
| `allowedOrigins` | none | origins besides `baseURL` the page may request; any other request fails the variant |
| `tolerance` | exact | `{ channelDelta?, maxDiffPixels? }`; leave it exact for same-renderer regression |
| `fontFiles` | none | hashed into `fontsHash` of every capture's metadata |
| `manifest`, `goldens`, `out`, `renderer` | `vcase.manifest.json`, `goldens`, `build/vcase`, `web-chromium` | paths and golden directory name |
| `parity` | none | other platforms' golden directories, by platform name |

### 4. Run

| command | effect |
|---|---|
| `vcase-react scan` | write the manifest (no browser) |
| `vcase-react check` | fail when the committed manifest differs from the scan (no browser) |
| `vcase-react verify` | manifest check, capture every variant, compare with the goldens; exit 1 on any difference, missing golden, error or orphan |
| `vcase-react record` | rewrite the manifest, write new and changed goldens, delete orphans |
| `vcase-react parity [--with mobile=dir]` | side-by-side page of each `parity` pair; never fails |
| `--filter 'ui.button.*,screens.*'` | only these ids (orphan detection is off) |

How a variant is captured: a fresh browser context at the viewport's size and density, the
theme as `prefers-color-scheme` (for `light` / `dark`), the locale, touch emulation for
`pointer: touch`, reduced motion, a fixed `Date`. The renderer waits for the load event,
network idle, `readySelector`, fonts and images, then `settleFrames` animation frames;
transitions, CSS animations and the caret are stopped. A component is captured at its root
element's bounds (cut at `maxWidth`, default the viewport width, and `maxHeight`, default 4x
the viewport height, with a warning); `maxWidth` also widens the window so a wide gallery can
lay out. A screen is captured at the viewport. With `frameIntervalMs`, the page runs on
Playwright's paused fake clock and each settle frame advances it by that many ms, so a
JavaScript animation is captured at a known instant (`0` keeps it at its start).

A page error, an HTTP error for the page, or a request to an origin that is not allowed makes
the variant an error: no image, and verify fails. `data-vcase-region="name"` elements are
written to the capture metadata as regions in image pixels.

Output, the same as the Kotlin adapter:

```
goldens/web-chromium/<id>@<axes>.png                    committed golden
vcase.manifest.json                                     committed manifest
build/vcase/web-chromium/<id>@<axes>.png / .json        actual + metadata
build/vcase/web-chromium/<id>@<axes>.diff.png / .expected.png
build/vcase/report/index.html, summary.json             matrix + expected / actual / diff
build/vcase/parity/index.html, parity.json              parity pairs (report-only)
```

### Goldens come from CI

Browser pixels depend on the Chromium build, its fonts and the OS, so goldens are recorded
where they are verified: the `react` job of [ci.yml](.github/workflows/ci.yml) (Ubuntu 24.04,
the Playwright version in `packages/react/package-lock.json`). When `verify` fails there, the
job runs `record` and uploads the `vcase-react-goldens` artifact. Look at the report
(`vcase-react-report`), then download the goldens into `samples/react` and commit them with
the reason for the change.

### Not done yet

- Fixtures (phase 2): cases take no data yet. `fixture` is written to the manifest and passed
  to the URL as `{fixture}`, but nothing generates fixture files.
- Network replay: requests outside `baseURL` and `allowedOrigins` fail the case, but they are
  not yet replayed from recorded fixtures.
- Parity is a side-by-side page with a whole-image hint. Per-region comparison and divergence
  masks are not applied yet.
- `vcase accept --from-ci <run>` does not exist; download the artifact by hand.
- The package is not published to npm. Depend on it with `"@vcase/react": "file:../vcase/packages/react"` after `npm ci && npm run build` there.

## Modules

| module | what |
|---|---|
| `vcase-annotations` | `@VCase`, `@VCaseDivergence`, `VCaseKind`, axis model (`VCaseAxis`, `VCasePointer`, `VCaseViewport`, `VCaseSizeClass`, `VCaseAxisValues`). KMP, no dependencies. |
| `vcase-core` | JVM. Manifest model + JSON (`VCaseManifest`, `ManifestCapture`, `VCaseJson`), `ManifestValidation`, `VCaseIds`, `AxisExpansion` / `VCaseVariant`, `ManifestDiff`, `ImageCompare` / `PixelTolerance` / `RegionTolerance`, `CaptureMeta`, `GoldenStore` / `GoldenRun` / `RunSummary`, `HtmlReport`. |
| `vcase-compose-desktop` | JVM, Compose Desktop. `VCaseScanner` (ComposablePreviewScanner `JvmAnnotationScanner` + ClassGraph), `VCaseHost`, `CaptureSize`, `LocalVCaseVariant`, `DesktopRenderer` (ImageComposeScene, renderer `desktop-skiko`), `runVCases`, `VCaseDesktopConfig`, `VCaseDesktopTest`. |
| `packages/react` | TypeScript, npm. `@vcase/react` (`vcase()`, `readVariant`, `markReady`) and `@vcase/react/node`: manifest model and JSON byte-compatible with vcase-core, scanner, axis expansion, validation, Playwright renderer, golden run, HTML report, parity report, `vcase-react` CLI. `schema/vcase-manifest.schema.json` is the manifest's JSON schema. |
| `samples/react` | Vite + React host page and five cases (dark-only, font scale, a screen with a divergence, parity with the Compose sample, a JavaScript animation on the fake clock), committed goldens. |
| `samples/compose-desktop` | Seven cases (including a wide capture, a settled popup and a wide size class with both pointers), a host, the one test class, committed goldens. |

## Building

JDK 17. `./gradlew check` runs everything on the Kotlin side: the unit tests, the adapter
tests, and the sample's verify against its committed goldens.

Node 22 for the React side: `npm ci && npm run typecheck && npm test && npm run build` in
`packages/react`, then `npm ci && npx vcase-react verify` in `samples/react` (needs
`npx playwright install --with-deps chromium`; goldens only match CI's Chromium). On Linux, skiko needs `libGL.so.1`,
`libX11.so.6` and `libfontconfig.so.1` (Ubuntu runners have them).

Kotlin 2.4.21 and Compose Multiplatform 1.12.2 match the first consumer.

### Using it before a release

- Composite build: in the consumer's `settings.gradle.kts`, add `includeBuild("../vcase")`
  and depend on `build.raft.vcase:vcase-compose-desktop`.
- Maven local: run `./gradlew publishToMavenLocal` here, then add `mavenLocal()` and
  `build.raft.vcase:vcase-compose-desktop:0.1.0-SNAPSHOT` in the consumer.

## License

License TBD by owners. See [LICENSE](LICENSE).
