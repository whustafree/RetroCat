package com.swordfish.lemuroid.app.mobile.feature.store

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.SystemID
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val TILE_WIDTH = 120.dp

/** Cover of a list row, at the 3:4 ratio most packs use. */
private val ROW_COVER_WIDTH = 56.dp
private val ROW_COVER_HEIGHT = 74.dp

/** Room kept on the right of the list for the A-Z strip. */
private val LETTER_INDEX_WIDTH = 26.dp

/** How many manifest warnings to spell out before the list stops being useful. */
private const val MAX_SHOWN_ISSUES = 5

/**
 * Size a preloaded cover is decoded at. It is only there to warm Coil's cache, so it does not need
 * to match the row exactly: the same image is fetched once and decoded again at the size the row
 * asks for.
 */
private const val COVER_PRELOAD_SIZE_PX = 256

/**
 * The store: a remote catalog of downloadable games, laid out as one shelf per system.
 *
 * Onboarding (an unconfigured store asks for the manifest URL) and the catalog share this screen,
 * and picking a game opens a preview with the cover, screenshots and description before anything is
 * downloaded, which is the "see it before you run it" part of a console store.
 */
@Composable
fun StoreScreen(
    modifier: Modifier = Modifier,
    viewModel: StoreViewModel,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var selectedGame by remember { mutableStateOf<StoreGame?>(null) }
    var editingUrl by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // The catalog the list shows. Recomputed only when something that changes the result does, so a
    // download progress tick never re-sorts a thousand entries.
    val groups =
        remember(state.groups, state.query, state.statusFilter, state.sort, state.downloadedIds, state.newIds) {
            state.visibleGroups { systemId -> storeSystemName(context, systemId) }
        }

    // Downloads are queued from here rather than straight from the button, so one that would not fit
    // can be confirmed first. Null means nothing is waiting for an answer.
    var pendingDownloads by remember { mutableStateOf<List<StoreGame>?>(null) }
    val queueDownloads: (List<StoreGame>) -> Unit = { games ->
        val check = storeSpaceCheck(games, state.freeBytes)
        if (check == null || check.fits) {
            viewModel.download(games)
            viewModel.clearSelection()
        } else {
            pendingDownloads = games
        }
    }

    // A new search starts at the top, whatever the user had scrolled to.
    LaunchedEffect(state.query, state.statusFilter, state.sort) {
        listState.scrollToItem(0)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!state.configured) {
            StoreSetup(onSave = viewModel::saveLocation)
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                StoreHeader(
                    name = state.name,
                    summary = storeSummary(state),
                    loading = state.loading,
                    viewMode = state.viewMode,
                    onToggleViewMode = viewModel::toggleViewMode,
                    onRefresh = viewModel::refresh,
                    onEditUrl = { editingUrl = true },
                )

                if (state.selectionMode) {
                    StoreSelectionBar(
                        count = state.selection.size,
                        onCancel = viewModel::clearSelection,
                        onSelectAll = viewModel::selectAll,
                        onDownload = { queueDownloads(viewModel.selectedDownloads()) },
                    )
                } else if (!state.isEmpty) {
                    StoreSearchBar(query = state.query, onQueryChange = viewModel::changeQuery)

                    StoreFilterRow(
                        state = state,
                        onStatusSelected = viewModel::selectStatusFilter,
                        onSortSelected = viewModel::selectSort,
                        onWifiOnlyChange = viewModel::setWifiOnly,
                    )
                }

                val summary = state.downloadSummary()
                if (!state.selectionMode && summary.active) {
                    StoreDownloadsPanel(
                        summary = summary,
                        onRetryFailed = viewModel::retryFailedDownloads,
                        onCancelAll = viewModel::cancelAllDownloads,
                    )
                }

                StoreBody(
                    state = state,
                    groups = groups,
                    listState = listState,
                    onGameClick = { selectedGame = it },
                    onGameLongClick = viewModel::startSelection,
                    onToggleSelection = viewModel::toggleSelection,
                    onClearFilters = viewModel::clearFilters,
                )
            }
        }

        selectedGame?.let { game ->
            StoreGameDetailDialog(
                game = game,
                download = state.downloads[game.id],
                downloaded = game.id in state.downloadedIds,
                onDismiss = { selectedGame = null },
                onDownload = { queueDownloads(listOf(game)) },
                onCancel = { viewModel.cancelDownload(game) },
                onRetry = { viewModel.retryDownload(game) },
                onDelete = { viewModel.delete(game) },
            )
        }

        pendingDownloads?.let { games ->
            val check = storeSpaceCheck(games, state.freeBytes)
            StoreSpaceDialog(
                check = check,
                onDismiss = { pendingDownloads = null },
                onConfirm = {
                    pendingDownloads = null
                    viewModel.download(games)
                    viewModel.clearSelection()
                },
            )
        }

        if (editingUrl) {
            StoreLocationDialog(
                initialSource = state.source,
                initialUrl = state.location,
                onDismiss = { editingUrl = false },
                onConfirm = { source, url ->
                    editingUrl = false
                    viewModel.saveLocation(source, url)
                },
            )
        }
    }
}

/**
 * The bar that replaces the search row while games are being ticked for a bulk download.
 *
 * Selecting is a mode rather than a floating button: with a thousand entries the useful action is
 * "pick a bunch and fetch them", and a mode makes it obvious which games are queued and how to
 * leave.
 */
@Composable
private fun StoreSelectionBar(
    count: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.store_selection_cancel),
                )
            }

            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.store_selected_count, count),
                style = MaterialTheme.typography.titleSmall,
            )

            TextButton(onClick = onSelectAll) {
                Text(stringResource(R.string.store_select_all))
            }

            TextButton(
                onClick = onDownload,
                enabled = count > 0,
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = stringResource(R.string.store_download),
                )
            }
        }
    }
}

@Composable
private fun StoreSetup(onSave: (StoreSource, String) -> Unit) {
    var source by rememberSaveable { mutableStateOf(StoreSource.MANIFEST) }
    var url by rememberSaveable { mutableStateOf("") }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.store_setup_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        )

        Text(
            text = stringResource(R.string.store_setup_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StoreSourceSelector(source = source, onSourceChange = { source = it })

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(source.locationLabelRes)) },
            singleLine = true,
        )

        Button(
            onClick = { onSave(source, url) },
            enabled = url.isNotBlank(),
        ) {
            Text(stringResource(R.string.store_save))
        }
    }
}

/** The two origins the store can read, as a plain radio group. */
@Composable
private fun StoreSourceSelector(
    source: StoreSource,
    onSourceChange: (StoreSource) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StoreSource.values().forEach { option ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSourceChange(option) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == source, onClick = { onSourceChange(option) })

                Text(
                    text = stringResource(option.sourceLabelRes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun StoreLocationDialog(
    initialSource: StoreSource,
    initialUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (StoreSource, String) -> Unit,
) {
    var source by rememberSaveable { mutableStateOf(initialSource) }
    var url by rememberSaveable { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.store_change_location)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StoreSourceSelector(source = source, onSourceChange = { source = it })

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(source.locationLabelRes)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(source, url) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.store_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.store_cancel))
            }
        },
    )
}

/** Field label for the active origin. */
private val StoreSource.locationLabelRes: Int
    get() =
        when (this) {
            StoreSource.MANIFEST -> R.string.store_manifest_url
            StoreSource.DRIVE_FOLDER -> R.string.store_drive_folder_url
        }

/** Name shown in the origin selector. */
private val StoreSource.sourceLabelRes: Int
    get() =
        when (this) {
            StoreSource.MANIFEST -> R.string.store_source_manifest
            StoreSource.DRIVE_FOLDER -> R.string.store_source_drive
        }

/**
 * The line under the catalog name: how many games there are and how much room they take.
 *
 * The store's own folder is the only part this can honestly report, so it says "installed" rather
 * than guessing at the whole device.
 */
@Composable
private fun storeSummary(state: StoreUiState): String {
    val games = stringResource(R.string.store_catalog_count, state.totalGames)
    if (state.installedBytes <= 0L) return games

    return "$games · ${stringResource(R.string.store_installed_size, formatBytes(state.installedBytes))}"
}

@Composable
private fun StoreHeader(
    name: String,
    summary: String,
    loading: Boolean,
    viewMode: StoreViewMode,
    onToggleViewMode: () -> Unit,
    onRefresh: () -> Unit,
    onEditUrl: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name.ifBlank { stringResource(R.string.store_title) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }

        IconButton(onClick = onToggleViewMode) {
            when (viewMode) {
                StoreViewMode.LIST ->
                    Icon(
                        imageVector = Icons.Filled.GridView,
                        contentDescription = stringResource(R.string.store_show_shelves),
                    )

                StoreViewMode.SHELVES ->
                    Icon(
                        imageVector = Icons.Filled.ViewList,
                        contentDescription = stringResource(R.string.store_show_list),
                    )
            }
        }

        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.store_refresh))
        }

        IconButton(onClick = onEditUrl) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.store_change_location))
        }
    }
}

@Composable
private fun StoreBody(
    state: StoreUiState,
    groups: List<StoreGroup>,
    listState: LazyListState,
    onGameClick: (StoreGame) -> Unit,
    onGameLongClick: (StoreGame) -> Unit,
    onToggleSelection: (StoreGame) -> Unit,
    onClearFilters: () -> Unit,
) {
    when {
        state.error != null && state.isEmpty -> StoreCenteredMessage { Text(state.error) }

        state.loading && state.isEmpty -> StoreCenteredMessage { CircularProgressIndicator() }

        state.isEmpty -> StoreCenteredMessage { Text(stringResource(R.string.store_empty)) }

        else ->
            Column(modifier = Modifier.fillMaxSize()) {
                // A refresh that failed over a catalog that is still on screen keeps the catalog
                // and only says so on a banner: losing it would be worse than a stale list.
                state.error?.let { StoreErrorBanner(it) }

                if (groups.isEmpty()) {
                    StoreCenteredMessage {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.store_no_results))

                            TextButton(onClick = onClearFilters) {
                                Text(stringResource(R.string.store_clear_filters))
                            }
                        }
                    }
                } else if (state.selectionMode) {
                    // Picking several games is always done on the list, whatever the catalog was
                    // being shown as: a shelf of covers has nowhere to put a tick.
                    StoreGameList(
                        state = state,
                        groups = groups,
                        listState = listState,
                        onGameClick = onToggleSelection,
                        onGameLongClick = onGameLongClick,
                        onToggleSelection = onToggleSelection,
                    )
                } else {
                    when (state.viewMode) {
                        StoreViewMode.LIST ->
                            StoreGameList(
                                state = state,
                                groups = groups,
                                listState = listState,
                                onGameClick = onGameClick,
                                onGameLongClick = onGameLongClick,
                                onToggleSelection = onToggleSelection,
                            )

                        StoreViewMode.SHELVES ->
                            StoreShelves(
                                state = state,
                                groups = groups,
                                onGameClick = onGameClick,
                                onGameLongClick = onGameLongClick,
                            )
                    }
                }
            }
    }
}

/** Title of a failed refresh over a catalog that is still usable. */
@Composable
private fun StoreErrorBanner(message: String) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.store_refresh_failed),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun StoreSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.store_search_clear),
                    )
                }
            }
        },
        placeholder = { Text(stringResource(R.string.store_search_hint)) },
    )
}

/** The status chips and the sort menu: what makes a thousand-entry catalog navigable. */
@Composable
private fun StoreFilterRow(
    state: StoreUiState,
    onStatusSelected: (StoreStatusFilter) -> Unit,
    onSortSelected: (StoreSortOrder) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            StoreChip(
                label = stringResource(R.string.store_filter_all),
                selected = state.statusFilter == StoreStatusFilter.ALL,
                onClick = { onStatusSelected(StoreStatusFilter.ALL) },
            )
        }

        // Only offered when there is something to show, so it never sits there reading "New (0)".
        if (state.newIds.isNotEmpty()) {
            item(key = "new") {
                StoreChip(
                    label = stringResource(R.string.store_filter_new, state.newIds.size),
                    selected = state.statusFilter == StoreStatusFilter.NEW,
                    onClick = { onStatusSelected(StoreStatusFilter.NEW) },
                )
            }
        }

        item(key = "pending") {
            StoreChip(
                label = stringResource(R.string.store_filter_not_downloaded),
                selected = state.statusFilter == StoreStatusFilter.NOT_DOWNLOADED,
                onClick = { onStatusSelected(StoreStatusFilter.NOT_DOWNLOADED) },
            )
        }

        item(key = "downloaded") {
            StoreChip(
                label = stringResource(R.string.store_filter_downloaded),
                selected = state.statusFilter == StoreStatusFilter.DOWNLOADED,
                onClick = { onStatusSelected(StoreStatusFilter.DOWNLOADED) },
            )
        }

        item(key = "sort") {
            StoreSortMenu(sort = state.sort, onSortSelected = onSortSelected)
        }

        // A setting among the filters is a compromise, but it belongs next to what is downloaded
        // and it has to be reachable before a download starts, not after one fails.
        item(key = "wifi_only") {
            StoreChip(
                label = stringResource(R.string.store_wifi_only),
                selected = state.wifiOnly,
                onClick = { onWifiOnlyChange(!state.wifiOnly) },
                leadingIcon = Icons.Filled.Wifi,
            )
        }
    }
}

/**
 * What the downloads are doing, above the catalog.
 *
 * The two actions are the ones that are impossible to do by hand over a thousand rows: giving up on
 * everything at once, and starting again everything that failed.
 */
@Composable
private fun StoreDownloadsPanel(
    summary: StoreDownloadSummary,
    onRetryFailed: () -> Unit,
    onCancelAll: () -> Unit,
) {
    val parts =
        buildList {
            if (summary.queued > 0) add(stringResource(R.string.store_downloads_queued, summary.queued))
            if (summary.downloading > 0) add(stringResource(R.string.store_downloads_downloading, summary.downloading))
            if (summary.failed > 0) add(stringResource(R.string.store_downloads_failed, summary.failed))
        }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                modifier = Modifier.weight(1f),
                text = parts.joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (summary.failed > 0) {
                TextButton(onClick = onRetryFailed) {
                    Text(stringResource(R.string.store_downloads_retry_failed))
                }
            }

            TextButton(onClick = onCancelAll) {
                Text(stringResource(R.string.store_downloads_cancel_all))
            }
        }
    }
}

/**
 * The warning before queueing something that may not fit.
 *
 * Only shown when the catalog announced sizes and the device answered how much room is left, so it
 * never guesses: the numbers it shows are the ones it has.
 */
@Composable
private fun StoreSpaceDialog(
    check: StoreSpaceCheck?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.store_space_title)) },
        text = {
            Text(
                stringResource(
                    R.string.store_space_message,
                    formatBytes(check?.neededBytes ?: 0L),
                    formatBytes(check?.freeBytes ?: 0L),
                ),
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.store_space_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.store_cancel))
            }
        },
    )
}

@Composable
private fun StoreChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon =
            leadingIcon?.let { icon ->
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                }
            },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    )
}

@Composable
private fun StoreSortMenu(
    sort: StoreSortOrder,
    onSortSelected: (StoreSortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = sort != StoreSortOrder.NAME,
            onClick = { expanded = true },
            label = { Text(stringResource(sort.labelRes), style = MaterialTheme.typography.labelLarge) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(R.string.store_sort),
                )
            },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StoreSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(stringResource(order.labelRes)) },
                    onClick = {
                        expanded = false
                        onSortSelected(order)
                    },
                )
            }
        }
    }
}

/** Name of the sort order, shown on the chip and in its menu. */
private val StoreSortOrder.labelRes: Int
    get() =
        when (this) {
            StoreSortOrder.NAME -> R.string.store_sort_name
            StoreSortOrder.NEWEST -> R.string.store_sort_newest
            StoreSortOrder.SIZE -> R.string.store_sort_size
        }

@Composable
private fun StoreCenteredMessage(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun StoreShelves(
    state: StoreUiState,
    groups: List<StoreGroup>,
    onGameClick: (StoreGame) -> Unit,
    onGameLongClick: (StoreGame) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (state.issues.isNotEmpty()) {
            item(key = "issues") {
                StoreIssues(issues = state.issues)
            }
        }

        items(items = groups, key = { it.system.name }) { group ->
            StoreShelf(
                group = group,
                downloads = state.downloads,
                downloadedIds = state.downloadedIds,
                newIds = state.newIds,
                onGameClick = onGameClick,
                onGameLongClick = onGameLongClick,
            )
        }
    }
}

/**
 * The catalog as a plain vertical list: one row per game, with letter headers and a side index.
 *
 * A shelf only shows a handful of covers, which looks nice but hides how big the catalog is. A list
 * can be scrolled by the hundreds and the side index jumps straight to a letter, which is what
 * makes a thousand-entry pack navigable.
 */
@Composable
private fun StoreGameList(
    state: StoreUiState,
    groups: List<StoreGroup>,
    listState: LazyListState,
    onGameClick: (StoreGame) -> Unit,
    onGameLongClick: (StoreGame) -> Unit,
    onToggleSelection: (StoreGame) -> Unit,
) {
    val scope = rememberCoroutineScope()

    // Letters only help when the list is alphabetical, and only one system can be indexed at once.
    val showLetterHeaders = state.sort == StoreSortOrder.NAME && groups.size == 1
    val rows =
        remember(groups, state.sort, state.newIds, showLetterHeaders) {
            buildStoreRows(
                groups = groups,
                showSystemHeaders = groups.size > 1,
                showLetterHeaders = showLetterHeaders,
                newIds = state.newIds,
            )
        }
    val index = remember(rows) { rows.letterIndex() }

    // Covers are fetched a little ahead of the finger. Coil already keeps them on disk, so this only
    // ever costs anything on the first pass through the catalog; after that a fast scroll meets
    // covers that are already there instead of grey boxes.
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    // The warnings banner is an extra item at the top of the list, so the visible indices are
    // shifted by one when it is on screen.
    val rowOffset = if (state.issues.isNotEmpty()) 1 else 0
    LaunchedEffect(imageLoader, rows) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            (visible.firstOrNull()?.index ?: 0) to (visible.lastOrNull()?.index ?: 0)
        }
            .distinctUntilChanged()
            .collect { (firstVisible, lastVisible) ->
                coverPreloadRange(firstVisible - rowOffset, lastVisible - rowOffset, rows.size)
                    .forEach { index ->
                        val row = rows.getOrNull(index)
                        if (row is StoreRow.GameRow) {
                            row.game.coverUrl?.let { url ->
                                imageLoader.enqueue(
                                    ImageRequest.Builder(context)
                                        .data(url)
                                        .size(COVER_PRELOAD_SIZE_PX)
                                        .build(),
                                )
                            }
                        }
                    }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = 8.dp,
                    end = if (showLetterHeaders) LETTER_INDEX_WIDTH else 0.dp,
                    bottom = 24.dp,
                ),
        ) {
            if (state.issues.isNotEmpty()) {
                item(key = "issues") {
                    StoreIssues(issues = state.issues)
                }
            }

            items(items = rows, key = { it.key }) { row ->
                when (row) {
                    is StoreRow.SystemHeader ->
                        StoreSystemHeader(system = row.system, count = row.count)

                    is StoreRow.LetterHeader -> StoreLetterHeader(letter = row.letter)

                    is StoreRow.GameRow ->
                        StoreGameRow(
                            game = row.game,
                            isNew = row.isNew,
                            download = state.downloads[row.game.id],
                            downloaded = row.game.id in state.downloadedIds,
                            selecting = state.selectionMode,
                            selected = row.game.id in state.selection,
                            onClick = { onGameClick(row.game) },
                            onLongClick = { onGameLongClick(row.game) },
                            onToggle = { onToggleSelection(row.game) },
                        )
                }
            }
        }

        if (showLetterHeaders) {
            StoreLetterIndex(
                modifier = Modifier.align(Alignment.CenterEnd),
                letters = index,
                onLetterClick = { rowIndex -> scope.launch { listState.animateScrollToItem(rowIndex) } },
            )
        }
    }
}

/** The A-Z strip on the right edge of the list: one tap jumps to that letter. */
@Composable
private fun StoreLetterIndex(
    letters: List<Pair<String, Int>>,
    onLetterClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(end = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { (letter, rowIndex) ->
            Text(
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .clickable { onLetterClick(rowIndex) }
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                text = letter,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StoreLetterHeader(letter: String) {
    Text(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 10.dp),
        text = letter,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Name of the system and how many games it holds, so the size of the catalog is visible. */
@Composable
private fun StoreSystemHeader(
    system: SystemID,
    count: Int,
) {
    val context = LocalContext.current
    val title = remember(system) { storeSystemName(context, system) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )

        Text(
            text = pluralStringResource(R.plurals.store_system_games, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One row of the list: a small cover, the title and the state of its download. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoreGameRow(
    game: StoreGame,
    isNew: Boolean,
    download: StoreDownload?,
    downloaded: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val status = download?.status

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
            )
        }

        Box(
            modifier =
                Modifier
                    .width(ROW_COVER_WIDTH)
                    .height(ROW_COVER_HEIGHT)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            StoreCoverImage(
                modifier = Modifier.fillMaxSize(),
                url = game.coverUrl,
                title = game.title,
            )
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = game.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isNew) StoreNewBadge()
            }

            gameRowMeta(game)?.let { meta ->
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when {
                status == StoreDownloadStatus.QUEUED ->
                    Text(
                        text = stringResource(R.string.store_queued),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                status == StoreDownloadStatus.DOWNLOADING && download != null -> {
                    StoreProgressBar(
                        modifier = Modifier.padding(top = 4.dp),
                        fraction = download.fraction,
                    )

                    Text(
                        text = downloadLabel(download),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                status == StoreDownloadStatus.FAILED ->
                    Text(
                        text = download?.error.orEmpty().ifBlank { stringResource(R.string.store_retry) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
            }
        }

        if (downloaded || download != null) {
            StoreBadge(
                modifier = Modifier.padding(start = 8.dp),
                download = download,
                downloaded = downloaded,
            )
        }
    }
}

/** Size of a game, when the catalog announced one, as the row's second line. */
private fun gameRowMeta(game: StoreGame): String? = game.sizeBytes?.takeIf { it > 0L }?.let { formatBytes(it) }

@Composable
private fun StoreIssues(issues: List<String>) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.store_issues_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            issues.take(MAX_SHOWN_ISSUES).forEach { issue ->
                Text(
                    text = issue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun StoreShelf(
    group: StoreGroup,
    downloads: Map<String, StoreDownload>,
    downloadedIds: Set<String>,
    newIds: Set<String>,
    onGameClick: (StoreGame) -> Unit,
    onGameLongClick: (StoreGame) -> Unit,
) {
    val context = LocalContext.current
    val title = remember(group.system) { context.getString(MetaSystemID.fromSystemID(group.system).titleResId) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = title,
            style = MaterialTheme.typography.titleSmall,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = group.games, key = { it.id }) { game ->
                StoreGameTile(
                    game = game,
                    new = game.id in newIds,
                    download = downloads[game.id],
                    downloaded = game.id in downloadedIds,
                    onClick = { onGameClick(game) },
                    onLongClick = { onGameLongClick(game) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoreGameTile(
    game: StoreGame,
    new: Boolean,
    download: StoreDownload?,
    downloaded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(TILE_WIDTH)
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            StoreCoverImage(
                modifier = Modifier.fillMaxSize(),
                url = game.coverUrl,
                title = game.title,
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.6f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
            )

            Text(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                text = game.title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (new) {
                StoreNewBadge(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                )
            }

            StoreBadge(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                download = download,
                downloaded = downloaded,
            )
        }
    }
}

@Composable
private fun StoreBadge(
    download: StoreDownload?,
    downloaded: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(24.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                download?.status == StoreDownloadStatus.QUEUED ->
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                download?.status == StoreDownloadStatus.DOWNLOADING ->
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)

                download?.status == StoreDownloadStatus.FAILED ->
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )

                downloaded || download?.status == StoreDownloadStatus.DOWNLOADED ->
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
            }
        }
    }
}

/** Marks the games the latest scan added, so the refresh button gives visible feedback. */
@Composable
private fun StoreNewBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            text = stringResource(R.string.store_new_badge),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun StoreCoverImage(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val fallbackColor = MaterialTheme.colorScheme.surfaceVariant
    val fallbackPainter = remember(fallbackColor) { ColorPainter(fallbackColor) }

    AsyncImage(
        model = url,
        contentDescription = title,
        modifier = modifier,
        fallback = fallbackPainter,
        error = fallbackPainter,
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun StoreGameDetailDialog(
    game: StoreGame,
    download: StoreDownload?,
    downloaded: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val systemName = remember(game.system) { systemLabel(context, game) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                ) {
                    StoreHeroImage(game = game)

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        )

                        Text(
                            text = metadataLine(game, systemName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        game.description?.takeIf { it.isNotBlank() }?.let { description ->
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        StoreDownloadAction(
                            game = game,
                            download = download,
                            downloaded = downloaded,
                            onDownload = onDownload,
                            onCancel = onCancel,
                            onRetry = onRetry,
                            onDelete = { confirmingDelete = true },
                        )
                    }
                }

                IconButton(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    onClick = onDismiss,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.back))
                }

                if (confirmingDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmingDelete = false },
                        title = { Text(stringResource(R.string.store_delete_title)) },
                        text = { Text(stringResource(R.string.store_delete_message)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    confirmingDelete = false
                                    onDismiss()
                                    onDelete()
                                },
                            ) {
                                Text(
                                    text = stringResource(R.string.store_delete_confirm),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmingDelete = false }) {
                                Text(stringResource(R.string.store_cancel))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreHeroImage(game: StoreGame) {
    val url = game.screenshots.firstOrNull() ?: game.coverUrl
    val fallbackColor = MaterialTheme.colorScheme.surfaceVariant
    val fallbackPainter = remember(fallbackColor) { ColorPainter(fallbackColor) }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.catalog_detail_no_screenshot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = game.title,
                modifier = Modifier.fillMaxSize(),
                fallback = fallbackPainter,
                error = fallbackPainter,
                contentScale = ContentScale.Crop,
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.6f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        ),
                    ),
        )
    }
}

@Composable
private fun StoreDownloadAction(
    game: StoreGame,
    download: StoreDownload?,
    downloaded: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val status = download?.status

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            downloaded || status == StoreDownloadStatus.DOWNLOADED -> {
                Button(onClick = {}, enabled = false) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = stringResource(R.string.store_downloaded),
                    )
                }

                Text(
                    text = stringResource(R.string.store_installed_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.store_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            download != null && status == StoreDownloadStatus.QUEUED -> {
                Text(
                    text = stringResource(R.string.store_queued),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.store_cancel_download))
                }
            }

            download != null && status == StoreDownloadStatus.DOWNLOADING -> {
                StoreProgressBar(fraction = download.fraction)

                Text(
                    text = downloadLabel(download = download),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.store_cancel_download))
                }
            }

            download != null && status == StoreDownloadStatus.FAILED -> {
                Text(
                    text = download.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                Button(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = stringResource(R.string.store_retry),
                    )
                }
            }

            else -> {
                val sizeText = game.sizeBytes?.takeIf { it > 0L }?.let { formatBytes(it) }

                Button(onClick = onDownload) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text =
                            if (sizeText != null) {
                                "${stringResource(R.string.store_download)} · $sizeText"
                            } else {
                                stringResource(R.string.store_download)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreProgressBar(
    fraction: Float?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (fraction != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** "Downloading… 42% · 1.5 MB / 3.0 MB", dropping the parts the server did not tell us. */
@Composable
private fun downloadLabel(download: StoreDownload): String =
    buildString {
        append(stringResource(R.string.store_downloading))

        val fraction = download.fraction
        if (fraction != null) {
            append(" ")
            append((fraction * 100).toInt())
            append("%")
        }

        val total = download.totalBytes
        if (total != null && total > 0L) {
            append(" · ")
            append(formatBytes(download.bytesRead))
            append(" / ")
            append(formatBytes(total))
        }
    }

private fun metadataLine(
    game: StoreGame,
    systemName: String,
): String =
    buildString {
        append(systemName)
        game.developer?.takeIf { it.isNotBlank() }?.let {
            append(" · ")
            append(it)
        }
        game.releaseYear?.let {
            append(" · ")
            append(it)
        }
        game.sizeBytes?.takeIf { it > 0L }?.let {
            append(" · ")
            append(formatBytes(it))
        }
    }

/**
 * How a system is named, e.g. "GBA", in the same words the search box accepts.
 */
private fun systemLabel(
    context: Context,
    game: StoreGame,
): String {
    val systemId = game.resolveSystemId() ?: return game.system
    return storeSystemName(context, systemId)
}
