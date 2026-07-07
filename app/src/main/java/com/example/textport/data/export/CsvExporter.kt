package com.example.textport.data.export

import com.example.textport.data.model.Message

/**
 * Exports messages as RFC-4180 CSV. Fields are quoted only when they contain a
 * comma, double quote, or newline; inner quotes are doubled. Rows are separated
 * by CRLF. The `body` column is last. Header row included.
 */
class CsvExporter : Exporter {

    override fun export(messages: List<Message>): String {
        val sb = StringBuilder()
        appendRow(sb, HEADER)
        for (m in messages) {
            appendRow(
                sb,
                listOf(
                    m.id.toString(),
                    m.address,
                    isoTimestamp(m.date),
                    m.type.label,
                    m.threadId.toString(),
                    if (m.read) "true" else "false",
                    m.body,
                ),
            )
        }
        return sb.toString()
    }

    private fun appendRow(sb: StringBuilder, fields: List<String>) {
        fields.joinTo(sb, separator = ",") { quote(it) }
        sb.append("\r\n")
    }

    /** Quotes a field per RFC-4180 if it contains a comma, quote, CR or LF. */
    private fun quote(field: String): String {
        val needsQuoting = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return field
        return "\"" + field.replace("\"", "\"\"") + "\""
    }

    private companion object {
        val HEADER = listOf("id", "address", "date", "type", "thread_id", "read", "body")
    }
}
