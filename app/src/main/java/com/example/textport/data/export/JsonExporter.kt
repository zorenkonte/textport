package com.example.textport.data.export

import com.example.textport.data.model.Message
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports messages as a JSON object with `exported_at`, `count` and a
 * `messages` array. Timestamps are ISO-8601. Uses the bundled `org.json`.
 *
 * @param exportedAtMillis wall-clock time stamped into `exported_at`.
 */
class JsonExporter(
    private val exportedAtMillis: Long = System.currentTimeMillis(),
) : Exporter {

    override fun export(messages: List<Message>): String {
        val array = JSONArray()
        for (m in messages) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("address", m.address)
            obj.put("date", isoTimestamp(m.date))
            obj.put("type", m.type.label)
            obj.put("thread_id", m.threadId)
            obj.put("read", m.read)
            obj.put("body", m.body)
            array.put(obj)
        }

        val root = JSONObject()
        root.put("exported_at", isoNow(exportedAtMillis))
        root.put("count", messages.size)
        root.put("messages", array)
        return root.toString(2)
    }
}
