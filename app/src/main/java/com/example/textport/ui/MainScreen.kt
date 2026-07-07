package com.example.textport.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.textport.data.export.ExportFormat
import com.example.textport.data.model.Message
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.loadMessages() else viewModel.onPermissionDenied()
    }

    // "*/*" lets the picker set the type from the file extension; the suggested
    // name already carries the correct extension for the selected format.
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        if (uri != null) {
            viewModel.export { context.contentResolver.openOutputStream(uri) }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Textport") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Back up the SMS messages stored on this device to a file you save yourself.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            Button(
                onClick = {
                    if (Permissions.hasReadSms(context)) {
                        viewModel.loadMessages()
                    } else {
                        permissionLauncher.launch(Permissions.READ_SMS)
                    }
                },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isLoading) "Loading…" else "Load messages")
            }

            Spacer(Modifier.height(16.dp))

            Text("Export format", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                ExportFormat.entries.forEach { format ->
                    FilterChip(
                        selected = state.format == format,
                        onClick = { viewModel.selectFormat(format) },
                        label = { Text(format.label) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val name = state.format.suggestedFileName(todayDatePart())
                    createDocumentLauncher.launch(name)
                },
                enabled = state.hasLoaded && !state.isExporting && state.messages.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isExporting) "Exporting…" else "Export")
            }

            Spacer(Modifier.height(12.dp))

            StatusLine(state)

            Spacer(Modifier.height(8.dp))

            MessageList(
                messages = state.messages,
                showEmptyHint = state.hasLoaded && state.messages.isEmpty(),
                modifier = Modifier.fillMaxSize(),
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

@Composable
private fun MessageList(
    messages: List<Message>,
    showEmptyHint: Boolean,
    modifier: Modifier = Modifier,
) {
    if (showEmptyHint) {
        Text(
            text = "No messages found on this device.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageRow(message)
        }
    }
}

@Composable
private fun MessageRow(message: Message) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message.address.ifBlank { "(unknown)" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = "${message.type.label} · ${formatDate(message.date)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
