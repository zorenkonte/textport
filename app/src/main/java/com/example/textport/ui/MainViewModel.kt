package com.example.textport.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.textport.data.SmsRepository
import com.example.textport.data.export.ExportFormat
import com.example.textport.data.model.Conversation
import com.example.textport.data.model.Message
import com.example.textport.data.model.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.OutputStream

/** Immutable UI state rendered by [MainScreen]. */
data class UiState(
    val messages: List<Message> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    /** Thread currently opened in the detail view, or null for the list view. */
    val selectedThreadId: Long? = null,
    val format: ExportFormat = ExportFormat.JSON,
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val hasLoaded: Boolean = false,
    val status: String? = null,
    val error: String? = null,
) {
    /** The conversation matching [selectedThreadId], if any. */
    val selectedConversation: Conversation?
        get() = selectedThreadId?.let { id -> conversations.firstOrNull { it.threadId == id } }
}

/**
 * Holds UI state and coordinates loading SMS and exporting them. All blocking
 * work happens on background dispatchers via the repository / IO.
 */
class MainViewModel(
    private val repository: SmsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun selectFormat(format: ExportFormat) {
        _uiState.update { it.copy(format = format) }
    }

    /** Opens a conversation's detail view. */
    fun openConversation(threadId: Long) {
        _uiState.update { it.copy(selectedThreadId = threadId, error = null) }
    }

    /** Returns to the conversation list. */
    fun closeConversation() {
        _uiState.update { it.copy(selectedThreadId = null) }
    }

    /** Loads messages from the content provider. Call only once permission is granted. */
    fun loadMessages() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null, status = null) }
        viewModelScope.launch {
            try {
                val messages = repository.loadMessages()
                val conversations = Conversation.fromMessages(messages)
                _uiState.update {
                    it.copy(
                        messages = messages,
                        conversations = conversations,
                        selectedThreadId = null,
                        isLoading = false,
                        hasLoaded = true,
                        status = loadedStatus(messages, conversations.size),
                    )
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "READ_SMS permission missing while loading", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Permission to read SMS was denied.",
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load messages", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Could not read messages: ${e.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                error = "Textport needs the SMS permission to read your messages. " +
                    "Grant it in Settings and try again.",
            )
        }
    }

    /**
     * Serializes messages in the selected format and writes UTF-8 to the stream
     * returned by [openOutputStream] (typically a SAF Uri). When [threadId] is
     * given, only that conversation's messages are exported; otherwise all are.
     */
    fun export(threadId: Long? = null, openOutputStream: () -> OutputStream?) {
        val state = _uiState.value
        if (state.isExporting) return

        val toExport = if (threadId != null) {
            state.conversations.firstOrNull { it.threadId == threadId }?.messages.orEmpty()
        } else {
            state.messages
        }
        if (toExport.isEmpty()) {
            _uiState.update { it.copy(error = "Load messages before exporting.") }
            return
        }

        _uiState.update { it.copy(isExporting = true, error = null, status = null) }
        viewModelScope.launch {
            try {
                val content = state.format.exporter().export(toExport)
                val stream = openOutputStream()
                    ?: throw IllegalStateException("Could not open the selected file.")
                stream.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                val scope = if (threadId != null) " from this conversation" else ""
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        status = "Exported ${toExport.size} message" +
                            (if (toExport.size == 1) "" else "s") + scope +
                            " as ${state.format.label}.",
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        error = "Export failed: ${e.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainViewModel"

        /**
         * Builds a status line with a per-type breakdown so unsent boxes
         * (failed/queued/outbox/draft) are visible, e.g.
         * "Loaded 342 messages in 12 conversations (received 300, sent 38, failed 4)".
         */
        private fun loadedStatus(messages: List<Message>, conversationCount: Int): String {
            if (messages.isEmpty()) return "No messages found on this device."
            val counts = messages.groupingBy { it.type }.eachCount()
            val breakdown = MessageType.entries
                .mapNotNull { type -> counts[type]?.let { "${type.label} $it" } }
                .joinToString(", ")
            val plural = if (messages.size == 1) "" else "s"
            val convPlural = if (conversationCount == 1) "" else "s"
            return "Loaded ${messages.size} message$plural in " +
                "$conversationCount conversation$convPlural ($breakdown)"
        }

        /** Factory that wires the [SmsRepository] into the ViewModel. */
        fun factory(repository: SmsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(repository) as T
            }
    }
}
