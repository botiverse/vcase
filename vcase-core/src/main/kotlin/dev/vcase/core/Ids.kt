package dev.vcase.core

/**
 * Case id rules: `layer.domain.name`, at least two lowercase segments separated by dots;
 * a segment starts with a letter and holds letters, digits and `-`. Ids never name a
 * platform: the same screen on the app and on the web has one id.
 */
public object VCaseIds {
    private val ID = Regex("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+$")
    private val PLATFORM = Regex("^[a-z][a-z0-9-]*$")

    public fun isValid(id: String): Boolean = ID.matches(id)

    /** Null when valid, otherwise why not. */
    public fun problem(id: String): String? =
        when {
            id.isEmpty() -> "id is empty"
            id != id.lowercase() -> "id '$id' must be lowercase"
            '.' !in id -> "id '$id' needs at least two dot-separated segments (layer.domain.name)"
            !ID.matches(id) -> "id '$id' must be dot-separated segments of [a-z][a-z0-9-]*"
            else -> null
        }

    public fun require(id: String): String {
        problem(id)?.let { throw IllegalArgumentException(it) }
        return id
    }

    /** A parity reference `platform:id`, e.g. `web:ui.button.variants`. */
    public fun parityProblem(ref: String): String? {
        val platform = ref.substringBefore(':', missingDelimiterValue = "")
        val id = ref.substringAfter(':', missingDelimiterValue = "")
        return when {
            platform.isEmpty() || id.isEmpty() -> "parity '$ref' must be platform:id"
            !PLATFORM.matches(platform) -> "parity '$ref': platform must be lowercase"
            else -> problem(id)?.let { "parity '$ref': $it" }
        }
    }
}
