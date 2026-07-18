package com.example.textport.data.model

import android.provider.Telephony

/**
 * The kind of an SMS row, mapped from [Telephony.Sms.TYPE] into a readable enum.
 */
enum class MessageType(val label: String) {
    RECEIVED("received"),
    SENT("sent"),
    DRAFT("draft"),
    OUTBOX("outbox"),
    FAILED("failed"),
    QUEUED("queued"),
    ALL("all"),
    UNKNOWN("unknown");

    companion object {
        /** Maps a raw SMS [Telephony.Sms.TYPE] constant to a [MessageType]. */
        fun fromRaw(raw: Int): MessageType = when (raw) {
            Telephony.Sms.MESSAGE_TYPE_INBOX -> RECEIVED
            Telephony.Sms.MESSAGE_TYPE_SENT -> SENT
            Telephony.Sms.MESSAGE_TYPE_DRAFT -> DRAFT
            Telephony.Sms.MESSAGE_TYPE_OUTBOX -> OUTBOX
            Telephony.Sms.MESSAGE_TYPE_FAILED -> FAILED
            Telephony.Sms.MESSAGE_TYPE_QUEUED -> QUEUED
            Telephony.Sms.MESSAGE_TYPE_ALL -> ALL
            else -> UNKNOWN
        }

        /**
         * Maps an MMS [Telephony.Mms.MESSAGE_BOX] constant to a [MessageType].
         * MMS has no dedicated "failed" box — a send that never went through
         * stays in the outbox — so [OUTBOX] is the "didn't send" signal here.
         */
        fun fromMmsBox(box: Int): MessageType = when (box) {
            Telephony.Mms.MESSAGE_BOX_INBOX -> RECEIVED
            Telephony.Mms.MESSAGE_BOX_SENT -> SENT
            Telephony.Mms.MESSAGE_BOX_DRAFTS -> DRAFT
            Telephony.Mms.MESSAGE_BOX_OUTBOX -> OUTBOX
            else -> UNKNOWN
        }
    }
}

/**
 * A single message (SMS or MMS) read from the system content provider.
 *
 * @param date epoch milliseconds the message was sent/received.
 * @param isMms true if this row came from the MMS store, false for SMS. SMS and
 *   MMS use separate id spaces, so use [stableKey] as a collision-free key.
 */
data class Message(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: MessageType,
    val threadId: Long,
    val read: Boolean,
    val isMms: Boolean = false,
) {
    /** Unique across both stores (SMS ids and MMS ids overlap). */
    val stableKey: String get() = if (isMms) "mms-$id" else "sms-$id"
}
