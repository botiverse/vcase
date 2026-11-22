package dev.vcase.compose.desktop

import dev.vcase.core.VariantResult
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runner.Runner
import org.junit.runner.manipulation.Filter
import org.junit.runner.manipulation.Filterable
import org.junit.runner.manipulation.NoTestsRemainException
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier

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
 * JUnit sees one test per case, named by the case id (all its variants), so a failure names the
 * case and a per-test time limit applies to one case. A last test, [MANIFEST_AND_GOLDENS], checks
 * the manifest and orphan goldens and prints the run's summary.
 *
 * Mode and paths come from `vcase.*` system properties ([VCaseDesktopConfig.fromSystemProperties]).
 */
@RunWith(VCaseRunner::class)
public abstract class VCaseDesktopTest {
    internal val testHost: VCaseHost get() = host

    protected abstract val host: VCaseHost
    protected abstract val packages: List<String>

    /** Prefix for manifest `source` paths, e.g. `src/desktopTest/kotlin`. */
    protected open val sourceRoot: String = ""

    protected open fun config(): VCaseDesktopConfig = VCaseDesktopConfig.fromSystemProperties(packages, sourceRoot)

    internal fun testConfig(): VCaseDesktopConfig = config()

    public companion object {
        public const val MANIFEST_AND_GOLDENS: String = "manifestAndGoldens"
    }
}

/** Runs a [VCaseDesktopTest] as one JUnit test per case. */
public class VCaseRunner(
    private val testClass: Class<*>,
) : Runner(),
    Filterable {
    private val session: VCaseSession by lazy {
        val test = testClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance() as VCaseDesktopTest
        VCaseSession(test.testHost, test.testConfig())
    }
    private val last = describe(VCaseDesktopTest.MANIFEST_AND_GOLDENS)
    private var filter: Filter = Filter.ALL

    private fun describe(name: String): Description = Description.createTestDescription(testClass, name)

    /** The case descriptions; the scan's own failure is reported by [last]. */
    private val scan by lazy { runCatching { session.cases.map { it to describe(it.entry.id) } } }

    private fun selected() = scan.getOrDefault(emptyList()).filter { filter.shouldRun(it.second) }

    override fun getDescription(): Description =
        Description.createSuiteDescription(testClass).apply {
            selected().forEach { addChild(it.second) }
            if (filter.shouldRun(last)) addChild(last)
        }

    override fun filter(filter: Filter) {
        this.filter = this.filter.intersect(filter)
        if (getDescription().children.isEmpty()) throw NoTestsRemainException()
    }

    override fun run(notifier: RunNotifier) {
        selected().forEach { (case, description) ->
            notifier.test(description) {
                val failures = session.run(case).filter { it.status.failing }
                if (failures.isNotEmpty()) throw AssertionError(failures.joinToString("\n", transform = ::message))
            }
        }
        if (!filter.shouldRun(last)) return
        notifier.test(last) {
            scan.getOrThrow()
            val result = session.finish()
            println(result.describe())
            // The cases' own failures are theirs; this one is about the set.
            if (result.manifestDiff?.isEmpty == false || result.summary?.let { it.copy(results = emptyList()).ok } == false) {
                throw AssertionError(result.describe())
            }
        }
    }

    private fun message(result: VariantResult) = "${result.status}: ${result.variant.fileStem}: ${result.message}"

    private inline fun RunNotifier.test(
        description: Description,
        body: () -> Unit,
    ) {
        fireTestStarted(description)
        try {
            body()
        } catch (failure: Throwable) {
            fireTestFailure(Failure(description, failure))
        } finally {
            fireTestFinished(description)
        }
    }
}
