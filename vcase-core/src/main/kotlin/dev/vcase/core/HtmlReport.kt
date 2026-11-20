package dev.vcase.core

import java.io.File

/**
 * Static HTML report: a matrix of cases x axis variants with each capture's status, then one
 * panel per capture with expected, actual and the diff overlay side by side. Images are
 * referenced by relative path, so the report directory and the capture directory upload
 * together as one CI artifact.
 */
public object HtmlReport {
    public fun write(
        dir: File,
        summary: RunSummary,
        title: String = "vcase",
    ): File {
        dir.mkdirs()
        val file = File(dir, "index.html")
        file.writeText(render(dir, summary, title))
        return file
    }

    public fun render(
        dir: File,
        summary: RunSummary,
        title: String,
    ): String {
        val byCase = summary.results.groupBy { it.variant.id }.toSortedMap()
        val columns = summary.results.map { it.variant.name }.distinct().sorted()
        fun rel(f: File?): String? = f?.let { dir.absoluteFile.toPath().relativize(it.absoluteFile.toPath()).toString().replace(File.separatorChar, '/') }

        return buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\"><title>").append(esc(title)).append("</title>")
            append("<style>")
            append("body{font:14px system-ui,sans-serif;margin:24px;color:#222}table{border-collapse:collapse}")
            append("td,th{border:1px solid #ddd;padding:4px 8px;text-align:left;vertical-align:top}")
            append(".s{display:inline-block;padding:1px 6px;border-radius:3px;color:#fff;font-size:12px;text-decoration:none}")
            append(".Passed,.Unchanged{background:#2e7d32}.Recorded{background:#1565c0}.Skipped{background:#888}")
            append(".Failed,.MissingGolden,.Error{background:#c62828}")
            append(".tri{display:flex;gap:12px;align-items:flex-start}.tri figure{margin:0}.tri img{max-width:420px;border:1px solid #ccc;background:repeating-conic-gradient(#eee 0 25%,#fff 0 50%) 0 0/16px 16px}")
            append("section{margin:24px 0;padding-top:8px;border-top:1px solid #eee}code{background:#f4f4f4;padding:0 4px}")
            append("</style></head><body>")
            append("<h1>").append(esc(title)).append("</h1>")
            append("<p>").append(esc(summary.describe().lineSequence().first())).append("</p>")
            if (summary.orphanGoldens.isNotEmpty()) {
                append("<p>Orphan goldens: ")
                summary.orphanGoldens.forEach { append("<code>").append(esc(it)).append("</code> ") }
                append("</p>")
            }
            append("<table><tr><th>case</th>")
            columns.forEach { append("<th>").append(esc(it)).append("</th>") }
            append("</tr>")
            for ((id, results) in byCase) {
                append("<tr><td><code>").append(esc(id)).append("</code></td>")
                val byName = results.associateBy { it.variant.name }
                for (c in columns) {
                    val r = byName[c]
                    append("<td>")
                    if (r != null) {
                        append("<a class=\"s ${r.status.name}\" href=\"#").append(anchor(r)).append("\">").append(r.status.name).append("</a>")
                    }
                    append("</td>")
                }
                append("</tr>")
            }
            append("</table>")
            // Failing captures first, then the rest in id order.
            val ordered = summary.results.sortedWith(compareBy<VariantResult>({ !it.status.failing }, { it.variant.fileStem }))
            for (r in ordered) {
                append("<section id=\"").append(anchor(r)).append("\"><h3><code>").append(esc(r.variant.fileStem)).append("</code> ")
                append("<span class=\"s ${r.status.name}\">").append(r.status.name).append("</span></h3>")
                if (r.message.isNotEmpty()) append("<p>").append(esc(r.message)).append("</p>")
                r.meta?.let { m ->
                    append("<p>").append(esc("${m.platform} / ${m.renderer} / ${m.durationMs} ms"))
                    m.fontsHash?.let { append(esc(" / fonts $it")) }
                    append("</p>")
                    if (m.warnings.isNotEmpty()) append("<p>warnings: ").append(esc(m.warnings.joinToString("; "))).append("</p>")
                }
                if (r.variant.case.divergences.isNotEmpty()) {
                    append("<ul>")
                    r.variant.case.divergences.forEach { d ->
                        append("<li>").append(esc("divergence with ${d.with}${d.region?.let { " [$it]" } ?: ""}: ${d.why} (${d.decidedBy ?: "?"}, ${d.ref ?: "-"})")).append("</li>")
                    }
                    append("</ul>")
                }
                append("<div class=\"tri\">")
                fig(this, "expected", rel(r.expected))
                fig(this, "actual", rel(r.actual))
                fig(this, "diff", rel(r.diff))
                append("</div></section>")
            }
            append("</body></html>\n")
        }
    }

    private fun fig(
        sb: StringBuilder,
        label: String,
        src: String?,
    ) {
        if (src == null) return
        sb.append("<figure><figcaption>").append(label).append("</figcaption><img src=\"").append(esc(src)).append("\" alt=\"").append(label).append("\"></figure>")
    }

    private fun anchor(r: VariantResult): String = "c-" + r.variant.fileStem.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
