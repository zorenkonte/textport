package com.example.textport.data.export

import com.example.textport.data.model.Message
import com.example.textport.data.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    private val exporter = CsvExporter()

    @Test
    fun `header row is present and body is last column`() {
        val lines = exporter.export(ExporterTestData.messages).split("\r\n")
        assertEquals("id,address,date,type,thread_id,read,body", lines[0])
    }

    @Test
    fun `rows are separated by CRLF and file ends with CRLF`() {
        val out = exporter.export(ExporterTestData.messages)
        assertTrue(out.endsWith("\r\n"))
        // header + 3 rows, each terminated by CRLF -> trailing split yields an empty last element
        assertEquals(5, out.split("\r\n").size)
    }

    @Test
    fun `field without special characters is not quoted`() {
        val plain = listOf(
            Message(
                id = 7,
                address = "Alice",
                body = "no special chars here",
                date = 1_783_678_500_000L,
                type = MessageType.RECEIVED,
                threadId = 3,
                read = true,
            ),
        )
        val row = exporter.export(plain).split("\r\n")[1]
        assertEquals("7,Alice,2026-07-10T10:15:00Z,received,3,true,no special chars here", row)
    }

    @Test
    fun `field with comma is quoted`() {
        // message 1 body "Hello, world" contains a comma
        val out = exporter.export(ExporterTestData.messages)
        assertTrue(out.contains(",\"Hello, world\"\r\n"))
    }

    @Test
    fun `field with quote comma and newline is RFC4180 quoted with doubled quotes`() {
        val out = exporter.export(ExporterTestData.messages)
        // body: Reply with "quotes", a comma, and\na newline
        assertTrue(
            out.contains("\"Reply with \"\"quotes\"\", a comma, and\na newline\""),
        )
    }
}
