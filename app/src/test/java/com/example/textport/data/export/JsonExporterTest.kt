package com.example.textport.data.export

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonExporterTest {

    private val exporter = JsonExporter(ExporterTestData.EXPORTED_AT_MILLIS)

    @Test
    fun `header carries exported_at and count`() {
        val root = JSONObject(exporter.export(ExporterTestData.messages))
        assertEquals("2026-07-10T10:15:30Z", root.getString("exported_at"))
        assertEquals(3, root.getInt("count"))
        assertEquals(3, root.getJSONArray("messages").length())
    }

    @Test
    fun `message fields are mapped with iso timestamps`() {
        val root = JSONObject(exporter.export(ExporterTestData.messages))
        val first = root.getJSONArray("messages").getJSONObject(0)
        assertEquals(1L, first.getLong("id"))
        assertEquals("+15550001111", first.getString("address"))
        assertEquals("2026-07-10T10:15:00Z", first.getString("date"))
        assertEquals("received", first.getString("type"))
        assertEquals(10L, first.getLong("thread_id"))
        assertTrue(first.getBoolean("read"))
        assertEquals("Hello, world", first.getString("body"))
    }

    @Test
    fun `empty input produces empty array`() {
        val root = JSONObject(exporter.export(emptyList()))
        assertEquals(0, root.getInt("count"))
        assertEquals(0, root.getJSONArray("messages").length())
    }
}
