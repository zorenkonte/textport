package com.example.textport.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.textport.data.SmsRepository
import com.example.textport.data.export.ExportFormat
import com.example.textport.data.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.OutputStream

/** Immutable UI state rendered by [MainScreen]. */
data class UiState(
    val messages: List<Message> = emptyList(),
    val format: ExportFormat = ExportFormat.JSON,
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val hasLoaded: Boolean = false,
    val status: String? = null,
    val error: String? = null,
)

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

    /** Loads messages from the content provider. Call only once permission is granted. */
    fun loadMessages() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null, status = null) }
        viewModelScope.launch {
            try {
                val messages = repository.loadMessages()
                _uiState.update {
                    it.copy(
                        messages = messages,
                        isLoading = false,
                        hasLoaded = true,
                        status = "Loaded ${messages.size} message" +
                            if (messages.size == 1) "" else "s",
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
     * Serializes the loaded messages in the selected format and writes UTF-8 to
     * the stream returned by [openOutputStream] (typically from a SAF Uri).
     */
    fun export(openOutputStream: () -> OutputStream?) {
        val state = _uiState.value
        if (state.isExporting) return
        if (state.messages.isEmpty()) {
            _uiState.update { it.copy(error = "Load messages before exporting.") }
            return
        }
        _uiState.update { it.copy(isExporting = true, error = null, status = null) }
        viewModelScope.launch {
            try {
                val content = state.format.exporter().export(state.messages)
                val stream = openOutputStream()
                    ?: throw IllegalStateException("Could not open the selected file.")
                stream.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        status = "Exported ${state.messages.size} message" +
                            (if (state.messages.size == 1) "" else "s") +
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

        /** Factory that wires the [SmsRepository] into the ViewModel. */
        fun factory(repository: SmsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(repository) as T
            }
    }
}
