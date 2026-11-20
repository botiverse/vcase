package dev.vcase.core

/**
 * Difference between the committed manifest and a fresh scan. CI fails on a non-empty diff:
 * the committed file must be what the scanner produces.
 */
public data class ManifestDiff(
    val added: List<String>,
    val removed: List<String>,
    /** Case id to the names of the fields that differ. */
    val changed: Map<String, List<String>>,
    /** Top-level fields that differ: `schema`, `project`, `defaults`. */
    val header: List<String>,
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && changed.isEmpty() && header.isEmpty()

    public fun describe(): String =
        if (isEmpty) {
            "manifest matches the scan"
        } else {
            buildString {
                appendLine("manifest differs from the scan:")
                header.forEach { appendLine("  ~ $it") }
                added.forEach { appendLine("  + $it") }
                removed.forEach { appendLine("  - $it") }
                changed.forEach { (id, fields) -> appendLine("  ~ $id (${fields.joinToString()})") }
            }.trimEnd()
        }

    public companion object {
        public fun between(
            committed: VCaseManifest,
            scanned: VCaseManifest,
        ): ManifestDiff {
            val header = mutableListOf<String>()
            if (committed.schema != scanned.schema) header += "schema"
            if (committed.project != scanned.project) header += "project"
            if (committed.defaults != scanned.defaults) header += "defaults"
            val old = committed.cases.associateBy { it.id }
            val new = scanned.cases.associateBy { it.id }
            val changed = linkedMapOf<String, List<String>>()
            for (id in (old.keys intersect new.keys).sorted()) {
                val a = old.getValue(id)
                val b = new.getValue(id)
                val fields =
                    buildList {
                        if (a.kind != b.kind) add("kind")
                        if (a.source != b.source) add("source")
                        if (a.fixture != b.fixture) add("fixture")
                        if (a.axes != b.axes) add("axes")
                        if (a.parity != b.parity) add("parity")
                        if (a.divergences != b.divergences) add("divergences")
                        if (a.formerly != b.formerly) add("formerly")
                        if (a.needsDevice != b.needsDevice) add("needsDevice")
                    }
                if (fields.isNotEmpty()) changed[id] = fields
            }
            return ManifestDiff(
                added = (new.keys - old.keys).sorted(),
                removed = (old.keys - new.keys).sorted(),
                changed = changed,
                header = header,
            )
        }
    }
}
