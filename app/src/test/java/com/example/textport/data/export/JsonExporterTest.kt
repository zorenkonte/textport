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
        assertEquals("sms", first.getString("kind"))
        assertEquals("+15550001111", first.getString("address"))
        assertEquals("2026-07-10T10:15:00Z", first.getString("date"))
        assertEquals("received", first.getString("type"))
        assertEquals(10L, first.getLong("thread_id"))
        assertTrue(first.getBoolean("read"))
        assertEquals("Hello, world", first.getString("body"))
    }

    @Test
    fun `mms messages are tagged kind mms`() {
        val mms = listOf(
            com.example.textport.data.model.Message(
                id = 9,
                address = "732",
                body = "note",
                date = 1_783_678_500_000L,
                type = com.example.textport.data.model.MessageType.OUTBOX,
                threadId = 4,
                read = true,
                isMms = true,
            ),
        )
        val obj = JSONObject(JsonExporter(0L).export(mms)).getJSONArray("messages").getJSONObject(0)
        assertEquals("mms", obj.getString("kind"))
        assertEquals("outbox", obj.getString("type"))
    }

    @Test
    fun `empty input produces empty array`() {
        val root = JSONObject(exporter.export(emptyList()))
        assertEquals(0, root.getInt("count"))
        assertEquals(0, root.getJSONArray("messages").length())
    }
}
