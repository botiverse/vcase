package dev.vcase

/**
 * Declares a visual case: one UI state that can be rendered on its own, with a globally
 * unique [id] shared by every platform that renders it (the id never names a platform).
 *
 * Put it on a parameterless `@Composable` function (Kotlin) next to, or instead of,
 * `@Preview`. The vcase scanner turns every annotated function into a manifest entry and
 * every adapter renders it once per axis combination.
 *
 * ```
 * @VCase("ds.button.variants", pointer = ["touch", "fine"], parity = ["web:ui.button.variants"])
 * @Composable
 * fun ButtonVariantsCase() { ButtonGallery() }
 * ```
 *
 * Axis parameters ([theme], [viewport], [locale], [pointer], [fontScale]) left empty use the
 * project defaults; a case lists only the axes it changes.
 *
 * @property id `layer.domain.name`, lowercase segments separated by dots
 *   (`ds.*` design system components, `screen.*` screens, `part.*` parts of a screen).
 * @property fixture name of the data set the case renders, e.g.
 *   `members.channel(count=24, preset=long-names)`; empty for none.
 * @property parity cases on other platforms this one must look like, `platform:id`
 *   (e.g. `web:ui.button.variants`).
 * @property kind [VCaseKind.Component] is captured at its content size,
 *   [VCaseKind.Screen] at the full viewport.
 * @property theme theme names to expand over, e.g. `light`, `dark`.
 * @property viewport viewport names (declared in the project defaults), e.g. `phone`, `wide`.
 * @property locale BCP 47 tags, e.g. `zh-CN`, `en`.
 * @property pointer [VCasePointer] names: `touch`, `fine`.
 * @property fontScale font scales, e.g. `1.0`, `1.3`.
 * @property formerly previous ids of this case, so goldens move with a rename instead of
 *   being dropped and re-added.
 * @property divergences decided differences from other platforms.
 * @property needsDevice the case can only be rendered on a device or emulator
 *   (gestures, keyboard, system sheets); JVM adapters skip it.
 * @property maxWidth bound in dp of a [VCaseKind.Component] capture's width, for content wider
 *   than the host's bound (a wide gallery); `0` = the host's bound (by default the viewport
 *   width). Content reaching the bound is cut there and gets a warning.
 * @property maxHeight bound in dp of a [VCaseKind.Component] capture's height; `0` = the
 *   host's bound (by default 4x the viewport height).
 * @property settleFrames frames rendered before the captured one, e.g. `2` for a popup or
 *   tooltip that lays itself out a frame late; `-1` = the host's `settleFrames`. Applies to the
 *   capture pass; a component's size is measured as the host measures it.
 * @property frameIntervalMs fixed time step between rendered frames: frame `i` renders at
 *   `i * frameIntervalMs` ms. `0` renders every frame at time 0, so state settles but
 *   animations never start (what `ImageComposeScene.render()` with no time does); a positive
 *   step lets time-driven animations run, e.g. `settleFrames = 20, frameIntervalMs = 16` for
 *   a 300 ms fade. `-1` = the host's `frameTimeNanos`.
 */
@MustBeDocumented
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class VCase(
    val id: String,
    val fixture: String = "",
    val parity: Array<String> = [],
    val kind: VCaseKind = VCaseKind.Component,
    val theme: Array<String> = [],
    val viewport: Array<String> = [],
    val locale: Array<String> = [],
    val pointer: Array<String> = [],
    val fontScale: FloatArray = [],
    val formerly: Array<String> = [],
    val divergences: Array<VCaseDivergence> = [],
    val needsDevice: Boolean = false,
    val maxWidth: Int = 0,
    val maxHeight: Int = 0,
    val settleFrames: Int = -1,
    val frameIntervalMs: Int = -1,
)

/** What a case captures; decides the capture size. */
public enum class VCaseKind {
    /** A component or gallery: captured at its own measured size (bounded by the viewport). */
    Component,

    /** A whole screen: captured at exactly the viewport size. */
    Screen,
}

/**
 * A decided, reviewed difference between this case and its [parity] counterpart on
 * platform [with], limited to [region] (a region name the adapters report, or empty for the
 * whole image). Differences inside the region do not fail the cross-platform comparison but
 * stay visible in the report.
 *
 * @property kind e.g. `platform-difference`, `pending-fix`.
 * @property why one line, readable by a reviewer.
 * @property decidedBy who decided.
 * @property ref where it was decided (task, PR, thread).
 * @property expires ISO date (`2027-03-01`) after which the report flags it; empty for none.
 */
@MustBeDocumented
@Target()
@Retention(AnnotationRetention.RUNTIME)
public annotation class VCaseDivergence(
    val with: String,
    val region: String = "",
    val kind: String = "platform-difference",
    val why: String,
    val decidedBy: String = "",
    val ref: String = "",
    val expires: String = "",
)
