package dev.vcase.compose.desktop

import org.junit.Test

/**
 * The one test class a project adds:
 *
 * ```
 * class DesignSystemVCases : VCaseDesktopTest() {
 *     override val host = MyVCaseHost
 *     override val packages = listOf("com.example.designsystem")
 * }
 * ```
 *
 * Mode and paths come from `vcase.*` system properties ([VCaseDesktopConfig.fromSystemProperties]).
 */
public abstract class VCaseDesktopTest {
    protected abstract val host: VCaseHost
    protected abstract val packages: List<String>

    /** Prefix for manifest `source` paths, e.g. `src/desktopTest/kotlin`. */
    protected open val sourceRoot: String = ""

    protected open fun config(): VCaseDesktopConfig = VCaseDesktopConfig.fromSystemProperties(packages, sourceRoot)

    @Test
    public fun vcases() {
        val result = runVCases(host, config())
        println(result.describe())
        result.assertOk()
    }
}
