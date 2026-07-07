package com.example.textport.data.export

import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlExporterTest {

    private val exporter = HtmlExporter(ExporterTestData.EXPORTED_AT_MILLIS)

    @Test
    fun `output is a self-contained html document with inline styles`() {
        val html = exporter.export(ExporterTestData.messages)
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<style>"))
        assertTrue(html.contains("prefers-color-scheme: dark"))
        // no external resource references
        assertTrue(!html.contains("<link") && !html.contains("<script"))
    }

    @Test
    fun `text is html-escaped`() {
        val html = exporter.export(ExporterTestData.messages)
        assertTrue(html.contains("&lt;Bank &amp; Co&gt;"))
        assertTrue(html.contains("Reply with &quot;quotes&quot;"))
        // raw unescaped angle brackets from data must not appear
        assertTrue(!html.contains("<Bank & Co>"))
    }

    @Test
    fun `threads are ordered most recent first`() {
        val html = exporter.export(ExporterTestData.messages)
        // thread 20's most recent message (2026-07-08) is newer than thread 10's (2026-07-07)
        val idxBank = html.indexOf("&lt;Bank &amp; Co&gt;")
        val idxPhone = html.indexOf("+15550001111")
        assertTrue(idxBank in 0 until idxPhone)
    }

    @Test
    fun `sent and received messages get distinct sides`() {
        val html = exporter.export(ExporterTestData.messages)
        assertTrue(html.contains("class=\"row sent\""))
        assertTrue(html.contains("class=\"row received\""))
    }
}
