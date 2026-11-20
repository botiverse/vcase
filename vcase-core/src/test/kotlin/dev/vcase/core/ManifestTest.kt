package dev.vcase.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManifestTest {
    /** The example from the design doc (comments removed), schema vcase/1. */
    private val designDocJson =
        """
        {
          "schema": "vcase/1",
          "project": "raft-mobile",
          "defaults": {
            "theme": ["light", "dark"],
            "viewport": [{ "name": "phone", "w": 390, "h": 844, "density": 3 }],
            "locale": ["zh-CN"],
            "pointer": ["touch"]
          },
          "cases": [
            {
              "id": "ds.button.variants",
              "kind": "component",
              "source": "compose/designsystem/.../RaftButtonCases.kt:12",
              "fixture": null,
              "axes": { "pointer": ["touch", "fine"] },
              "parity": ["web:ui.button.variants"],
              "divergences": []
            },
            {
              "id": "screen.channel.members",
              "kind": "screen",
              "source": "compose/shared/.../ChannelMembersCases.kt:20",
              "fixture": "members.channel(count=24, preset=long-names)",
              "axes": { "theme": ["light", "dark"], "viewport": ["phone"] },
              "parity": ["web:screens.channel.members"],
              "divergences": [
                { "with": "web", "region": "header", "kind": "platform-difference",
                  "why": "system back gesture, no back button", "decidedBy": "artin", "ref": "#proj-mobile:xxxx" }
              ]
            }
          ]
        }
        """.trimIndent()

    @Test
    fun readsDesignDocExampleAndRoundTrips() {
        val m = VCaseJson.decodeManifest(designDocJson)
        assertEquals("raft-mobile", m.project)
        assertEquals(2, m.cases.size)
        assertEquals(null, m.cases[0].fixture)
        assertEquals(listOf("touch", "fine"), m.cases[0].axes["pointer"])
        assertEquals("header", m.cases[1].divergences.single().region)
        assertEquals(listOf(1f), m.defaults.fontScale)
        val text = VCaseJson.encode(m)
        assertEquals(m, VCaseJson.decodeManifest(text))
        // Written like the doc: null fixture and empty lists present, optional extras absent.
        assertTrue("\"fixture\": null" in text, text)
        assertTrue("\"divergences\": []" in text)
        assertFalse("fontScale" in text)
        assertFalse("formerly" in text)
        assertFalse("needsDevice" in text)
        assertTrue(ManifestValidation.problems(m).isEmpty(), ManifestValidation.problems(m).toString())
    }

    @Test
    fun encodingIsStable() {
        val m = VCaseJson.decodeManifest(designDocJson)
        assertEquals(VCaseJson.encode(m), VCaseJson.encode(VCaseJson.decodeManifest(VCaseJson.encode(m))))
    }

    @Test
    fun fileRoundTrip() {
        val f = kotlin.io.path.createTempDirectory("vcase").toFile().resolve("sub/vcase.manifest.json")
        val m = VCaseJson.decodeManifest(designDocJson)
        VCaseJson.write(m, f)
        assertEquals(m, VCaseJson.read(f))
    }

    @Test
    fun rejectsOtherSchemaAndUnknownKeys() {
        assertFailsWith<IllegalArgumentException> { VCaseJson.decodeManifest(designDocJson.replace("vcase/1", "vcase/2")) }
        assertFailsWith<Exception> { VCaseJson.decodeManifest(designDocJson.replace("\"locale\"", "\"bogus\": 1, \"locale\"")) }
    }

    @Test
    fun validationFindsProblems() {
        val m =
            VCaseManifest(
                project = "p",
                defaults = DEFAULTS,
                cases =
                    listOf(
                        case("ds.a.b"),
                        case("ds.a.b"),
                        case("Bad"),
                        case("ds.c.d", parity = listOf("nope")),
                        case("ds.e.f", mapOf("viewport" to listOf("tablet"))),
                        case("ds.g.h").copy(kind = "page"),
                        case("ds.i.j").copy(formerly = listOf("ds.a.b")),
                        case("ds.k.l").copy(divergences = listOf(ManifestDivergence(with = "web", kind = "platform-difference", why = "x"))),
                    ),
            )
        val p = ManifestValidation.problems(m)
        assertEquals(7, p.size, p.joinToString("\n"))
        assertFailsWith<IllegalArgumentException> { ManifestValidation.requireValid(m) }
    }
}
