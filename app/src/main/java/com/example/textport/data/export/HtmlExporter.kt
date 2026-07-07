package com.example.textport.data.export

import com.example.textport.data.model.Message
import com.example.textport.data.model.MessageType

/**
 * Exports messages as a single self-contained, chat-style HTML file.
 *
 * Messages are grouped by `thread_id`; threads are ordered by their most recent
 * message (newest first), and within a thread messages read oldest-to-newest.
 * Sent messages align right, everything else left. All text is HTML-escaped and
 * the page adapts to light/dark via `prefers-color-scheme`.
 *
 * @param exportedAtMillis wall-clock time shown in the page header.
 */
class HtmlExporter(
    private val exportedAtMillis: Long = System.currentTimeMillis(),
) : Exporter {

    override fun export(messages: List<Message>): String {
        val threads = messages
            .groupBy { it.threadId }
            .map { (threadId, msgs) -> threadId to msgs.sortedBy { it.date } }
            .sortedByDescending { (_, msgs) -> msgs.maxOfOrNull { it.date } ?: Long.MIN_VALUE }

        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>\n")
        sb.append("<html lang=\"en\">\n<head>\n")
        sb.append("<meta charset=\"utf-8\">\n")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        sb.append("<title>Textport export</title>\n")
        sb.append("<style>\n").append(STYLE).append("</style>\n")
        sb.append("</head>\n<body>\n")

        sb.append("<header class=\"page-header\">\n")
        sb.append("<h1>Textport export</h1>\n")
        sb.append("<p class=\"meta\">")
            .append(messages.size).append(" message").append(if (messages.size == 1) "" else "s")
            .append(" &middot; exported ").append(escape(isoNow(exportedAtMillis)))
            .append("</p>\n")
        sb.append("</header>\n")

        if (threads.isEmpty()) {
            sb.append("<p class=\"empty\">No messages.</p>\n")
        }

        for ((_, msgs) in threads) {
            val title = threadTitle(msgs)
            sb.append("<section class=\"thread\">\n")
            sb.append("<h2 class=\"thread-title\">").append(escape(title)).append("</h2>\n")
            for (m in msgs) {
                val side = if (m.type == MessageType.SENT) "sent" else "received"
                sb.append("<div class=\"row ").append(side).append("\">\n")
                sb.append("<div class=\"bubble\">\n")
                sb.append("<div class=\"body\">").append(escape(m.body)).append("</div>\n")
                sb.append("<div class=\"stamp\">")
                    .append(escape(m.type.label)).append(" &middot; ")
                    .append(escape(isoTimestamp(m.date)))
                    .append("</div>\n")
                sb.append("</div>\n</div>\n")
            }
            sb.append("</section>\n")
        }

        sb.append("</body>\n</html>\n")
        return sb.toString()
    }

    private fun threadTitle(msgs: List<Message>): String {
        val address = msgs.asReversed().firstOrNull { it.address.isNotBlank() }?.address
        return if (address.isNullOrBlank()) "(unknown)" else address
    }

    /** Escapes the five characters that are unsafe in HTML text/attributes. */
    private fun escape(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private companion object {
        val STYLE = """
            :root {
              --bg: #f4f6f6;
              --surface: #ffffff;
              --text: #10201e;
              --muted: #5f6f6c;
              --received: #e4e9e9;
              --sent: #00695c;
              --sent-text: #ffffff;
            }
            @media (prefers-color-scheme: dark) {
              :root {
                --bg: #0e1615;
                --surface: #16211f;
                --text: #e7ecea;
                --muted: #9aa8a5;
                --received: #24302e;
                --sent: #4db6ac;
                --sent-text: #06201c;
              }
            }
            * { box-sizing: border-box; }
            body {
              margin: 0;
              padding: 24px 16px 48px;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
              background: var(--bg);
              color: var(--text);
            }
            .page-header { max-width: 760px; margin: 0 auto 24px; }
            .page-header h1 { margin: 0 0 4px; font-size: 1.4rem; }
            .meta { margin: 0; color: var(--muted); font-size: 0.85rem; }
            .empty { max-width: 760px; margin: 0 auto; color: var(--muted); }
            .thread {
              max-width: 760px;
              margin: 0 auto 28px;
              background: var(--surface);
              border-radius: 14px;
              padding: 16px;
            }
            .thread-title {
              margin: 0 0 12px;
              font-size: 0.95rem;
              color: var(--muted);
              word-break: break-word;
            }
            .row { display: flex; margin: 6px 0; }
            .row.received { justify-content: flex-start; }
            .row.sent { justify-content: flex-end; }
            .bubble {
              max-width: 78%;
              padding: 8px 12px;
              border-radius: 14px;
              background: var(--received);
              color: var(--text);
            }
            .row.sent .bubble { background: var(--sent); color: var(--sent-text); }
            .body { white-space: pre-wrap; word-wrap: break-word; }
            .stamp { margin-top: 4px; font-size: 0.72rem; opacity: 0.75; }
        """.trimIndent()
    }
}
