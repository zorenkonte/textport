package com.example.textport.data.model

/**
 * A group of [Message]s that share a `thread_id` — i.e. one conversation with a
 * contact/number, the way a messaging app presents them.
 *
 * @param messages the thread's messages sorted oldest-to-newest (chat order).
 */
data class Conversation(
    val threadId: Long,
    val address: String,
    val messages: List<Message>,
) {
    /** Epoch millis of the most recent message, used to order conversations. */
    val lastMessageDate: Long = messages.maxOfOrNull { it.date } ?: 0L

    /** Body of the most recent message, for the conversation-list preview. */
    val lastMessageSnippet: String =
        messages.maxByOrNull { it.date }?.body.orEmpty()

    val count: Int = messages.size

    /** Number of failed / queued / outbox messages — the "didn't send" ones. */
    val unsentCount: Int = messages.count {
        it.type == MessageType.FAILED ||
            it.type == MessageType.QUEUED ||
            it.type == MessageType.OUTBOX
    }

    companion object {
        /**
         * Groups a flat message list into conversations by `thread_id`, ordered
         * by most recent activity. Each conversation's messages are sorted
         * oldest-to-newest. The display address is the latest non-blank address
         * seen in the thread, falling back to "(unknown)".
         */
        fun fromMessages(messages: List<Message>): List<Conversation> =
            messages
                .groupBy { it.threadId }
                .map { (threadId, msgs) ->
                    val ordered = msgs.sortedBy { it.date }
                    val address = ordered.asReversed()
                        .firstOrNull { it.address.isNotBlank() }
                        ?.address
                        ?.takeIf { it.isNotBlank() }
                        ?: "(unknown)"
                    Conversation(threadId = threadId, address = address, messages = ordered)
                }
                .sortedByDescending { it.lastMessageDate }
    }
}
