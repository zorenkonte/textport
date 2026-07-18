package com.example.textport.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.textport.data.export.ExportFormat
import com.example.textport.data.model.Conversation
import com.example.textport.data.model.Message
import com.example.textport.data.model.MessageType
import com.example.textport.util.DefaultSmsApp
import com.example.textport.util.Permissions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val selected = state.selectedConversation

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.loadMessages() else viewModel.onPermissionDenied()
    }

    // Which thread the pending export targets (null = all messages). Set right
    // before launching the picker and consumed in its result callback.
    var pendingExportThreadId by remember { mutableStateOf<Long?>(null) }

    // "*/*" lets the picker set the type from the file extension; the suggested
    // name already carries the correct extension for the selected format.
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        if (uri != null) {
            val threadId = pendingExportThreadId
            viewModel.export(threadId = threadId) { context.contentResolver.openOutputStream(uri) }
        }
    }

    fun launchExport(threadId: Long?, label: String?) {
        pendingExportThreadId = threadId
        createDocumentLauncher.launch(state.format.suggestedFileName(todayDatePart(), label))
    }

    // Prompt to become the default SMS app; on return, refresh status and, if we
    // are now default, reload so the newly-visible failed messages appear.
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val nowDefault = DefaultSmsApp.isDefault(context)
        viewModel.setDefaultSmsStatus(nowDefault)
        if (nowDefault && Permissions.hasReadSms(context)) {
            viewModel.loadMessages()
        }
    }

    // Keep the default-app status fresh — the user may change it in system
    // settings and return to Textport.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.setDefaultSmsStatus(DefaultSmsApp.isDefault(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Hardware back returns to the list rather than leaving the app.
    BackHandler(enabled = selected != null) { viewModel.closeConversation() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected?.address ?: "Textport") },
                navigationIcon = {
                    if (selected != null) {
                        IconButton(onClick = { viewModel.closeConversation() }) {
                            Text("←", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            if (selected == null) {
                ConversationListView(
                    state = state,
                    onLoad = {
                        if (Permissions.hasReadSms(context)) {
                            viewModel.loadMessages()
                        } else {
                            permissionLauncher.launch(Permissions.READ_SMS)
                        }
                    },
                    onSelectFormat = viewModel::selectFormat,
                    onExportAll = { launchExport(threadId = null, label = null) },
                    onOpenConversation = viewModel::openConversation,
                    onSetDefaultSmsApp = { roleLauncher.launch(DefaultSmsApp.requestIntent(context)) },
                    onRestoreDefaultSmsApp = { context.startActivity(DefaultSmsApp.restoreDefaultsIntent()) },
                )
            } else {
                ConversationDetailView(
                    conversation = selected,
                    state = state,
                    onSelectFormat = viewModel::selectFormat,
                    onExportConversation = {
                        launchExport(threadId = selected.threadId, label = selected.address)
                    },
                )
            }
        }
    }
}

@Composable
private fun ConversationListView(
    state: UiState,
    onLoad: () -> Unit,
    onSelectFormat: (ExportFormat) -> Unit,
    onExportAll: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onSetDefaultSmsApp: () -> Unit,
    onRestoreDefaultSmsApp: () -> Unit,
) {
    Text(
        text = "Back up the SMS messages stored on this device to a file you save yourself.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 12.dp),
    )

    Button(
        onClick = onLoad,
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.isLoading) "Loading…" else "Load messages")
    }

    Spacer(Modifier.height(16.dp))

    FormatSelector(selected = state.format, onSelectFormat = onSelectFormat)

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = onExportAll,
        enabled = state.hasLoaded && !state.isExporting && state.messages.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.isExporting) "Exporting…" else "Export all messages")
    }

    Spacer(Modifier.height(12.dp))

    StatusLine(state)

    Spacer(Modifier.height(12.dp))

    DefaultSmsAppCard(
        isDefault = state.isDefaultSmsApp,
        onSetDefault = onSetDefaultSmsApp,
        onRestoreDefault = onRestoreDefaultSmsApp,
    )

    Spacer(Modifier.height(8.dp))

    if (state.hasLoaded && state.conversations.isEmpty()) {
        Text(
            text = "No messages found on this device.",
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.conversations, key = { it.threadId }) { conversation ->
            ConversationRow(conversation, onClick = { onOpenConversation(conversation.threadId) })
        }
    }
}

/**
 * Explains and drives the temporary default-SMS-app flow used to reach failed /
 * unsent SMS the system hides from non-default apps.
 */
@Composable
private fun DefaultSmsAppCard(
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onRestoreDefault: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Read failed / unsent SMS", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Some failed or unsent texts (like notes to a number that can't " +
                    "receive them) are only visible to the device's default SMS app. To " +
                    "include them, set Textport as default, tap Load, then switch back.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "While Textport is default it saves incoming texts to your inbox but " +
                    "won't notify you, and incoming MMS won't be saved. Switch back to your " +
                    "usual app as soon as you've loaded your messages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(10.dp))
            if (isDefault) {
                Text(
                    text = "Textport is currently your default SMS app.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Button(onClick = onRestoreDefault, modifier = Modifier.fillMaxWidth()) {
                    Text("Switch back to your SMS app")
                }
            } else {
                OutlinedButton(onClick = onSetDefault, modifier = Modifier.fillMaxWidth()) {
                    Text("Set Textport as default SMS app")
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = conversation.address,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp).weight(1f),
                )
                Text(
                    text = formatDate(conversation.lastMessageDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = conversation.lastMessageSnippet,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${conversation.count} message" +
                        if (conversation.count == 1) "" else "s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (conversation.unsentCount > 0) {
                    Text(
                        text = "${conversation.unsentCount} unsent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationDetailView(
    conversation: Conversation,
    state: UiState,
    onSelectFormat: (ExportFormat) -> Unit,
    onExportConversation: () -> Unit,
) {
    Spacer(Modifier.height(12.dp))
    FormatSelector(selected = state.format, onSelectFormat = onSelectFormat)

    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onExportConversation,
        enabled = !state.isExporting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.isExporting) "Exporting…" else "Export this conversation")
    }

    Spacer(Modifier.height(12.dp))
    StatusLine(state)
    Spacer(Modifier.height(8.dp))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(conversation.messages, key = { it.stableKey }) { message ->
            MessageBubble(message)
        }
    }
}

/** A chat bubble: outgoing messages align right, incoming align left. */
@Composable
private fun MessageBubble(message: Message) {
    val outgoing = message.type != MessageType.RECEIVED
    val didNotSend = message.type == MessageType.FAILED ||
        message.type == MessageType.QUEUED ||
        message.type == MessageType.OUTBOX ||
        message.type == MessageType.DRAFT

    val bubbleColor = when {
        didNotSend -> MaterialTheme.colorScheme.errorContainer
        outgoing -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        didNotSend -> MaterialTheme.colorScheme.onErrorContainer
        outgoing -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            color = bubbleColor,
            contentColor = textColor,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(text = message.body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                val kindTag = if (message.isMms) "MMS · " else ""
                val stamp = if (message.type == MessageType.RECEIVED || message.type == MessageType.SENT) {
                    "$kindTag${formatDate(message.date)}"
                } else {
                    // Surface the box (failed/queued/outbox/draft) for outgoing notes.
                    "$kindTag${message.type.label} · ${formatDate(message.date)}"
                }
                Text(
                    text = stamp,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun FormatSelector(selected: ExportFormat, onSelectFormat: (ExportFormat) -> Unit) {
    Text("Export format", style = MaterialTheme.typography.labelLarge)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        ExportFormat.entries.forEach { format ->
            FilterChip(
                selected = selected == format,
                onClick = { onSelectFormat(format) },
                label = { Text(format.label) },
            )
        }
    }
}

@Composable
private fun StatusLine(state: UiState) {
    when {
        state.error != null -> Text(
            text = state.error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )

        state.status != null -> Text(
            text = state.status,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )

        else -> {}
    }
}

private val displayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(displayFormatter)

private val fileDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun todayDatePart(): String =
    Instant.now().atZone(ZoneId.systemDefault()).format(fileDateFormatter)
