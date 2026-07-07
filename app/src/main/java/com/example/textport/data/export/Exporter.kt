package com.example.textport.data.export

import com.example.textport.data.model.Message
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Turns a list of [Message]s into a serialized document (JSON / CSV / HTML).
 *
 * Implementations are plain, side-effect-free functions of their input so they
 * are trivially unit testable.
 */
interface Exporter {
    /** Serializes [messages] to a single [String] document. */
    fun export(messages: List<Message>): String
}

/** Formats epoch milliseconds as an ISO-8601 UTC timestamp (e.g. 2026-07-07T10:15:30Z). */
internal fun isoTimestamp(epochMillis: Long): String =
    DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis))

/** ISO-8601 timestamp for "now", used for the export metadata header. */
internal fun isoNow(epochMillis: Long): String = isoTimestamp(epochMillis)

/**
 * The user-selectable export formats. Each knows its exporter, file extension
 * and MIME type for the Storage Access Framework.
 */
enum class ExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
) {
    JSON("JSON", "json", "application/json") {
        override fun exporter(): Exporter = JsonExporter()
    },
    CSV("CSV", "csv", "text/csv") {
        override fun exporter(): Exporter = CsvExporter()
    },
    HTML("HTML", "html", "text/html") {
        override fun exporter(): Exporter = HtmlExporter()
    };

    abstract fun exporter(): Exporter

    /** Suggested filename such as `textport-2026-07-07.json`. */
    fun suggestedFileName(datePart: String): String = "textport-$datePart.$extension"
}
