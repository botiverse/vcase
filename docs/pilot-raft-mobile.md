# Pilot: Raft mobile design system on vcase

This is phase 1 of the migration in [design.md](design.md#migrating-raft). The design
system's gallery screenshot tests become `@VCase` functions. Each one is checked against
the existing goldens pixel for pixel, and only then is the old test deleted.

The pilot exists only as a local commit. It is not pushed:

- repository: `~/work-sr/mobile`, worktree `~/work-sr/m-vcase`
- branch: `agent/sindresorhus/vcase-ds-pilot` (from `origin/main` at `d765e4f5d`)
- commit: `b478477c9` "test(designsystem): vcase pilot for three gallery screenshot tests"

It has not been built. Gradle is not run in the mobile repository outside CI, so none of
the steps below have been tried yet.

## What the pilot contains

Three existing tests in `compose/designsystem/src/desktopTest/.../screenshot/` were picked
because they are small galleries with a light/dark loop and no interaction:

| legacy test | legacy goldens | vcase case | vcase goldens |
|---|---|---|---|
| `EmptyStateScreenshotTest` | `screenshots/empty_state_{light,dark}.png` | `ds.empty-state.gallery` | `goldens/desktop-skiko/ds.empty-state.gallery@{light,dark},compact.png` |
| `SaveStatusScreenshotTest` | `screenshots/save_status_{light,dark}.png` | `ds.save-status.gallery` | `goldens/desktop-skiko/ds.save-status.gallery@{light,dark},compact.png` |
| `ComposerDropOverlayScreenshotTest` | `screenshots/composer_drop_overlay_{light,dark}.png` | `ds.composer-drop-overlay.default` | `goldens/desktop-skiko/ds.composer-drop-overlay.default@{light,dark},compact.png` |

New files are in `compose/designsystem/src/desktopVcase/kotlin/build/raft/designsystem/vcase/`:

- `EmptyStateCases.kt`, `SaveStatusCases.kt`, `ComposerDropOverlayCases.kt`: the gallery
  bodies, moved unchanged into `@VCase @Composable` functions. The light/dark loop and the
  `RaftScreenshot.capture` call are gone, because the theme axis does that work now.
- `Gallery.kt`: `Caption` and `CanvasSurface`, which each test used to keep privately.
- `RaftVCaseHost.kt`: the `VCaseHost`. It reproduces `RaftScreenshot.capture`'s frame
  exactly: `RaftTheme(appearance, systemDark = false, scale = Compact, pointerInput)`, the
  theme canvas with 16dp of padding, content size up to 1200 x 2400 dp, and 1dp = 2px.
  The theme axis maps to `RaftAppearance` and the pointer axis to `RaftPointerInput`. The one
  viewport, `compact` (1200 x 2400 @ 2), is a gallery bound, not a device.
- `DesignSystemVCases.kt`: the one test class (`VCaseDesktopTest`).
- `LegacyGoldenParityTest.kt`: the migration gate. It requires each recorded vcase golden to
  equal its legacy golden (`ImageCompare`, zero tolerance). It is skipped until the vcase
  goldens exist.

Gradle changes, both opt-in. Without the properties, the build is unchanged:

- `compose/settings.gradle.kts`: `providers.gradleProperty("vcase.includeBuild").orNull?.let { includeBuild(it) }`
- `compose/designsystem/build.gradle.kts`: when `vcase.includeBuild` or `vcase.version` is
  set, `desktopTest` also compiles `src/desktopVcase/kotlin` and depends on
  `build.raft.vcase:vcase-compose-desktop`. It also gets the `vcase.*` system properties: goldens in
  `compose/designsystem/goldens/`, the manifest at `compose/designsystem/vcase.manifest.json`,
  and output in `build/vcase/`.

## How to run it (on CI or a machine allowed to build mobile)

From `compose/`. The composite build points at a vcase checkout:

```bash
# 1. record the vcase goldens and the manifest
./gradlew :designsystem:desktopTest --tests '*DesignSystemVCases' \
  -Pvcase.includeBuild=/path/to/vcase -Pvcase.mode=record

# 2. migration gate: vcase goldens == legacy goldens, pixel for pixel
./gradlew :designsystem:desktopTest --tests '*LegacyGoldenParityTest' -Pvcase.includeBuild=/path/to/vcase

# 3. verify (what CI would run): manifest matches the scan, zero-tolerance compare
./gradlew :designsystem:desktopTest --tests '*DesignSystemVCases' -Pvcase.includeBuild=/path/to/vcase
```

Instead of the composite build, you can run `./gradlew publishToMavenLocal` in vcase and pass
`-Pvcase.version=0.1.0-SNAPSHOT`. `mavenLocal()` is already in the app's repositories.

The existing goldens are recorded on the ubuntu CI runner (the `record_screenshots` dispatch
of `test.yml`). Steps 1 and 2 should therefore run there too, because CJK glyphs and fonts
are pinned to that renderer. A dispatch input that checks out vcase next to mobile and
passes `-Pvcase.includeBuild` is the smallest CI change. It is not part of the pilot commit.

## What changes on the app side when the pilot is accepted

1. Commit `goldens/desktop-skiko/*.png` (six images) and `vcase.manifest.json` from step 1.
2. Once `LegacyGoldenParityTest` passes, delete the three legacy test classes and their six
   `screenshots/*.png`. Then delete `LegacyGoldenParityTest` too, because the vcase goldens
   now stand on their own.
3. Make the dependency permanent. Until vcase is published, that means the CI checkout +
   composite build. Afterwards it is a normal `desktopTest` dependency on a released
   `build.raft.vcase:vcase-compose-desktop`, and the properties go away.
4. Move the remaining 41 gallery tests the same way, in batches. Each batch has the same gate.

The following stay as they are: Roborazzi and `RaftScreenshot` for tests that are not yet
migrated, the `desktopTest` task, the `recordRoborazziDesktop` / `verifyRoborazziDesktop`
tasks, and the legacy golden directory.

## Expected friction

- **Pixel parity with the legacy goldens is expected, but nobody has checked it.** The
  frame, density and two-pass sizing are the same. `DesktopRenderer` renders every frame at
  a fixed time (`frameTimeNanos(0) = 0`), while `RaftScreenshot` calls `scene.render()` with
  the default. Static galleries should not care. If a case differs, the gate shows the
  pixel count and bounds.
- **Gallery ids versus web ids.** These cases are whole galleries (`*.gallery`). Pairing
  with raft-design (`parity = ["web:..."]`) needs per-component cases whose ids match the
  web examples (for example `save-status/compositions/preview`). That is the next step
  after regression parity.
- **Locale is not applied.** The host leaves `RaftTheme(localeList = null)`, as the legacy
  harness does. Making `locale` a real axis changes the type styles, so it needs a re-record.
- **Tests with interaction** (hover, pointer density, pull-down, toast timing) need more
  host hooks: settle frames per case, `LocalRaftSpinnerStillStep`, input injection. These
  tests come last.
- **Source line numbers in the manifest.** Editing above a case changes its `source` line,
  and the manifest check then asks for a `vcase.mode=scan`. This is by design (the manifest
  must equal the scan), but it is a real cost in review noise.
