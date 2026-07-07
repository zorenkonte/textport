package com.example.textport.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTest {

    private fun msg(
        id: Long,
        threadId: Long,
        date: Long,
        address: String = "+15550000000",
        body: String = "b",
        type: MessageType = MessageType.RECEIVED,
    ) = Message(
        id = id,
        address = address,
        body = body,
        date = date,
        type = type,
        threadId = threadId,
        read = true,
    )

    @Test
    fun `groups by thread and orders conversations by most recent`() {
        val messages = listOf(
            msg(1, threadId = 10, date = 100),
            msg(2, threadId = 20, date = 300),
            msg(3, threadId = 10, date = 200),
        )
        val convos = Conversation.fromMessages(messages)
        assertEquals(2, convos.size)
        // thread 20 (date 300) is more recent than thread 10 (date 200)
        assertEquals(20L, convos[0].threadId)
        assertEquals(10L, convos[1].threadId)
    }

    @Test
    fun `messages within a conversation are oldest-to-newest`() {
        val convo = Conversation.fromMessages(
            listOf(
                msg(1, threadId = 10, date = 300),
                msg(2, threadId = 10, date = 100),
                msg(3, threadId = 10, date = 200),
            ),
        ).single()
        assertEquals(listOf(2L, 3L, 1L), convo.messages.map { it.id })
        assertEquals(300L, convo.lastMessageDate)
    }

    @Test
    fun `snippet is the most recent body`() {
        val convo = Conversation.fromMessages(
            listOf(
                msg(1, threadId = 10, date = 100, body = "old"),
                msg(2, threadId = 10, date = 200, body = "newest"),
            ),
        ).single()
        assertEquals("newest", convo.lastMessageSnippet)
    }

    @Test
    fun `unsent count covers failed queued and outbox`() {
        val convo = Conversation.fromMessages(
            listOf(
                msg(1, threadId = 10, date = 100, type = MessageType.RECEIVED),
                msg(2, threadId = 10, date = 200, type = MessageType.FAILED),
                msg(3, threadId = 10, date = 300, type = MessageType.QUEUED),
                msg(4, threadId = 10, date = 400, type = MessageType.OUTBOX),
                msg(5, threadId = 10, date = 500, type = MessageType.SENT),
            ),
        ).single()
        assertEquals(3, convo.unsentCount)
        assertEquals(5, convo.count)
    }

    @Test
    fun `blank address falls back to unknown`() {
        val convo = Conversation.fromMessages(
            listOf(msg(1, threadId = 10, date = 100, address = "")),
        ).single()
        assertEquals("(unknown)", convo.address)
    }

    @Test
    fun `display address prefers latest non-blank address in thread`() {
        val convo = Conversation.fromMessages(
            listOf(
                msg(1, threadId = 10, date = 100, address = "Alice"),
                msg(2, threadId = 10, date = 200, address = ""),
            ),
        ).single()
        assertTrue(convo.address == "Alice")
    }
}
