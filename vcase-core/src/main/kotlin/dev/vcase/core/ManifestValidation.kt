package dev.vcase.core

/** Checks a manifest beyond what JSON decoding does; returns human-readable problems. */
public object ManifestValidation {
    public fun problems(manifest: VCaseManifest): List<String> {
        val out = mutableListOf<String>()
        if (manifest.schema != VCaseManifest.SCHEMA) out += "schema is '${manifest.schema}', expected '${VCaseManifest.SCHEMA}'"
        if (manifest.project.isBlank()) out += "project is empty"
        val d = manifest.defaults
        if (d.theme.isEmpty()) out += "defaults.theme is empty"
        if (d.defaultViewports().isEmpty()) out += "defaults.viewport has no viewport that is not optIn"
        if (d.locale.isEmpty()) out += "defaults.locale is empty"
        if (d.pointer.isEmpty()) out += "defaults.pointer is empty"
        d.viewport.groupBy { it.name }.filterValues { it.size > 1 }.keys.forEach { out += "viewport '$it' declared twice" }
        d.viewport.filter { it.w <= 0 || it.h <= 0 || it.density <= 0f }.forEach { out += "viewport '${it.name}' must have positive w, h, density" }

        manifest.cases.groupBy { it.id }.filterValues { it.size > 1 }.forEach { (id, list) ->
            out += "id '$id' declared ${list.size} times: ${list.joinToString { it.source }}"
        }
        val allIds = manifest.cases.map { it.id }.toSet()
        for (case in manifest.cases) {
            VCaseIds.problem(case.id)?.let { out += "${case.source}: $it" }
            if (case.kind != ManifestCase.KIND_COMPONENT && case.kind != ManifestCase.KIND_SCREEN) {
                out += "${case.id}: kind '${case.kind}' must be component or screen"
            }
            case.parity.forEach { ref -> VCaseIds.parityProblem(ref)?.let { out += "${case.id}: $it" } }
            case.formerly.forEach { old ->
                VCaseIds.problem(old)?.let { out += "${case.id}: formerly $it" }
                if (old in allIds) out += "${case.id}: formerly '$old' is still a live case id"
            }
            case.divergences.forEach { dv ->
                if (case.parity.none { it.substringBefore(':') == dv.with }) {
                    out += "${case.id}: divergence with '${dv.with}' has no parity entry for that platform"
                }
                if (dv.why.isBlank()) out += "${case.id}: divergence with '${dv.with}' needs a why"
            }
            runCatching { AxisExpansion.expand(case, d) }.onFailure { out += it.message ?: it.toString() }
        }
        return out
    }

    public fun requireValid(manifest: VCaseManifest) {
        val problems = problems(manifest)
        require(problems.isEmpty()) { "invalid vcase manifest:\n" + problems.joinToString("\n") { "  - $it" } }
    }
}
