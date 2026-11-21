# vcase

Visual cases for UI code. You declare each UI state once in source code, give it an id,
and vcase renders it, compares it with a committed golden image, and writes a report.
It works with any Kotlin Compose project. A React adapter is planned.

Status: phase 1. The Kotlin core and the Compose Desktop adapter work and are tested. Nothing
is published yet; see [Using it before a release](#using-it-before-a-release).

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

## React (planned)

The React adapter is not built yet. It will be shaped like this
([design](docs/design.md#react)):

```tsx
// button.cases.tsx
export const meta = vcase({ id: "ui.button.variants", parity: ["mobile:ds.button.variants"] });
export default function Variants() { return <ButtonGallery />; }
```

- The format is compatible with Storybook CSF (default export + named `meta`), so existing
  stories can be scanned as they are.
- A Playwright script opens a Vite host page that renders only the case, waits for fonts
  and images, and captures the element.
- Network requests are replayed from fixtures. An unknown request fails the case.
- It writes the same output as every adapter: `<id>@<axes>.png` + `<id>@<axes>.json`.

## Modules

| module | what |
|---|---|
| `vcase-annotations` | `@VCase`, `@VCaseDivergence`, `VCaseKind`, axis model (`VCaseAxis`, `VCasePointer`, `VCaseViewport`, `VCaseSizeClass`, `VCaseAxisValues`). KMP, no dependencies. |
| `vcase-core` | JVM. Manifest model + JSON (`VCaseManifest`, `ManifestCapture`, `VCaseJson`), `ManifestValidation`, `VCaseIds`, `AxisExpansion` / `VCaseVariant`, `ManifestDiff`, `ImageCompare` / `PixelTolerance` / `RegionTolerance`, `CaptureMeta`, `GoldenStore` / `GoldenRun` / `RunSummary`, `HtmlReport`. |
| `vcase-compose-desktop` | JVM, Compose Desktop. `VCaseScanner` (ComposablePreviewScanner `JvmAnnotationScanner` + ClassGraph), `VCaseHost`, `CaptureSize`, `LocalVCaseVariant`, `DesktopRenderer` (ImageComposeScene, renderer `desktop-skiko`), `runVCases`, `VCaseDesktopConfig`, `VCaseDesktopTest`. |
| `samples/compose-desktop` | Seven cases (including a wide capture, a settled popup and a wide size class with both pointers), a host, the one test class, committed goldens. |

## Building

JDK 17. `./gradlew check` runs everything: the unit tests, the adapter tests, and the
sample's verify against its committed goldens. On Linux, skiko needs `libGL.so.1`,
`libX11.so.6` and `libfontconfig.so.1` (Ubuntu runners have them).

Kotlin 2.4.21 and Compose Multiplatform 1.12.2 match the first consumer.

### Using it before a release

- Composite build: in the consumer's `settings.gradle.kts`, add `includeBuild("../vcase")`
  and depend on `build.raft.vcase:vcase-compose-desktop`.
- Maven local: run `./gradlew publishToMavenLocal` here, then add `mavenLocal()` and
  `build.raft.vcase:vcase-compose-desktop:0.1.0-SNAPSHOT` in the consumer.

## License

License TBD by owners. See [LICENSE](LICENSE).
