package dev.vcase.core

internal val DEFAULTS =
    ManifestDefaults(
        theme = listOf("light", "dark"),
        viewport = listOf(ManifestViewport("phone", 390, 844, 3f), ManifestViewport("wide", 1280, 800, 2f)).take(1),
        locale = listOf("zh-CN"),
        pointer = listOf("touch"),
    )

internal fun case(
    id: String,
    axes: Map<String, List<String>> = emptyMap(),
    parity: List<String> = emptyList(),
) = ManifestCase(id = id, source = "Cases.kt:1", axes = axes, parity = parity)
