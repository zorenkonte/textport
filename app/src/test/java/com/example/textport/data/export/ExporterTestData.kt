package com.example.textport.data.export

import com.example.textport.data.model.Message
import com.example.textport.data.model.MessageType

/** Shared fixtures for exporter tests. */
object ExporterTestData {

    // 2026-07-10T10:15:30Z
    const val EXPORTED_AT_MILLIS: Long = 1_783_678_530_000L

    val messages: List<Message> = listOf(
        Message(
            id = 1,
            address = "+15550001111",
            body = "Hello, world",
            date = 1_783_678_500_000L, // 2026-07-10T10:15:00Z
            type = MessageType.RECEIVED,
            threadId = 10,
            read = true,
        ),
        Message(
            id = 2,
            address = "+15550001111",
            body = "Reply with \"quotes\", a comma, and\na newline",
            date = 1_783_678_560_000L, // 2026-07-10T10:16:00Z
            type = MessageType.SENT,
            threadId = 10,
            read = true,
        ),
        Message(
            id = 3,
            address = "<Bank & Co>",
            body = "Balance low",
            date = 1_783_764_900_000L, // 2026-07-11T10:15:00Z
            type = MessageType.RECEIVED,
            threadId = 20,
            read = false,
        ),
    )
}
