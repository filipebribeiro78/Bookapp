package pt.estante.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pt.estante.app.appContainer
import pt.estante.app.data.BookSummary
import pt.estante.app.data.SheetSettings
import pt.estante.app.data.SyncState
import pt.estante.core.Condition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(onNewBook: () -> Unit, onOpenBook: (String) -> Unit, onSettings: () -> Unit) {
    val container = LocalContext.current.appContainer
    val books by remember { container.repository.observeSummaries() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val sheet by remember { container.settings.sheetSettings }
        .collectAsStateWithLifecycle(initialValue = null as SheetSettings?)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estante") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewBook,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New book") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (sheet?.isComplete == false) {
                item { ConnectSheetCard(onSettings) }
            }
            if (books.isEmpty()) {
                item {
                    Text(
                        "No books yet. Tap \"New book\" to photograph the first one.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(books, key = { it.book.id }) { summary ->
                BookCard(
                    summary = summary,
                    onClick = { onOpenBook(summary.book.id) },
                    onUpload = {
                        container.applicationScope.launch { container.repository.queueUpload(summary.book.id) }
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectSheetCard(onSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connect your Google Sheet", style = MaterialTheme.typography.titleMedium)
            Text("You can photograph books now. They upload as soon as the Sheet is connected.")
            Button(onClick = onSettings) { Text("Open settings") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookCard(summary: BookSummary, onClick: () -> Unit, onUpload: () -> Unit) {
    val book = summary.book
    val failed = book.syncState == SyncState.ERROR.name
    val onlyOnPhone = book.syncState == SyncState.LOCAL.name && summary.photoCount > 0
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium)
            val condition = Condition.entries.firstOrNull { it.name == book.condition }?.label ?: book.condition
            Text("$condition · ${photoCountText(summary.photoCount)}", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    statusText(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (failed || onlyOnPhone) {
                    TextButton(onClick = onUpload) { Text(if (failed) "Retry" else "Upload") }
                }
            }
        }
    }
}

private fun statusText(summary: BookSummary): String = when (summary.book.syncState) {
    SyncState.LOCAL.name -> if (summary.photoCount == 0) "No photos yet" else "Only on this phone"
    SyncState.QUEUED.name -> "Waiting to upload (needs internet)"
    SyncState.SYNCING.name -> "Uploading ${summary.uploadedCount} of ${summary.photoCount}…"
    SyncState.SYNCED.name ->
        if (summary.uploadedCount < summary.photoCount) "New photos not uploaded yet" else "In your Google Sheet"
    SyncState.ERROR.name -> "Upload problem: ${summary.book.lastError ?: "unknown error"}"
    else -> summary.book.syncState
}

internal fun photoCountText(count: Int): String = when (count) {
    0 -> "no photos"
    1 -> "1 photo"
    else -> "$count photos"
}
