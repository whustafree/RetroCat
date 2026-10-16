package com.swordfish.lemuroid.app.mobile.feature.store

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
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
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.SystemID

private val TILE_WIDTH = 120.dp

/** Cover of a list row, at the 3:4 ratio most packs use. */
private val ROW_COVER_WIDTH = 56.dp
private val ROW_COVER_HEIGHT = 74.dp

/** How many manifest warnings to spell out before the list stops being useful. */
private const val MAX_SHOWN_ISSUES = 5

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
    var selectedGame by remember { mutableStateOf<StoreGame?>(null) }
    var editingUrl by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (!state.configured) {
            StoreSetup(onSave = viewModel::saveLocation)
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                StoreHeader(
                    name = state.name,
                    loading = state.loading,
                    viewMode = state.viewMode,
                    onToggleViewMode = viewModel::toggleViewMode,
                    onRefresh = viewModel::refresh,
                    onEditUrl = { editingUrl = true },
                )

                StoreBody(
                    state = state,
                    onGameClick = { selectedGame = it },
                )
            }
        }

        selectedGame?.let { game ->
            StoreGameDetailDialog(
                game = game,
                download = state.downloads[game.id],
                downloaded = game.id in state.downloadedIds,
                onDismiss = { selectedGame = null },
                onDownload = { viewModel.download(game) },
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

@Composable
private fun StoreHeader(
    name: String,
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
        Text(
            modifier = Modifier.weight(1f),
            text = name.ifBlank { stringResource(R.string.store_title) },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

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
    onGameClick: (StoreGame) -> Unit,
) {
    val error = state.error

    when {
        error != null -> StoreCenteredMessage { Text(error) }

        state.loading && state.isEmpty -> StoreCenteredMessage { CircularProgressIndicator() }

        state.isEmpty -> StoreCenteredMessage { Text(stringResource(R.string.store_empty)) }

        else ->
            when (state.viewMode) {
                StoreViewMode.LIST -> StoreGameList(state = state, onGameClick = onGameClick)
                StoreViewMode.SHELVES -> StoreShelves(state = state, onGameClick = onGameClick)
            }
    }
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
    onGameClick: (StoreGame) -> Unit,
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

        items(items = state.groups, key = { it.system.name }) { group ->
            StoreShelf(
                group = group,
                downloads = state.downloads,
                downloadedIds = state.downloadedIds,
                onGameClick = onGameClick,
            )
        }
    }
}

/**
 * The catalog as a plain vertical list: one row per game and a header per system.
 *
 * A shelf only shows a handful of covers, which looks nice but hides how big the catalog is. A
 * list can be scrolled by the hundreds and each header spells out how many games it holds, which
 * is what makes a thousand-entry pack readable.
 */
@Composable
private fun StoreGameList(
    state: StoreUiState,
    onGameClick: (StoreGame) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        if (state.issues.isNotEmpty()) {
            item(key = "issues") {
                StoreIssues(issues = state.issues)
            }
        }

        state.groups.forEach { group ->
            item(key = "system-${group.system.name}") {
                StoreSystemHeader(system = group.system, count = group.games.size)
            }

            items(items = group.games, key = { it.id }) { game ->
                StoreGameRow(
                    game = game,
                    download = state.downloads[game.id],
                    downloaded = game.id in state.downloadedIds,
                    onClick = { onGameClick(game) },
                )
            }
        }
    }
}

/** Name of the system and how many games it holds, so the size of the catalog is visible. */
@Composable
private fun StoreSystemHeader(
    system: SystemID,
    count: Int,
) {
    val context = LocalContext.current
    val title =
        remember(system) {
            runCatching { context.getString(GameSystem.findById(system.dbname).shortTitleResId) }
                .getOrDefault(system.dbname)
        }

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
@Composable
private fun StoreGameRow(
    game: StoreGame,
    download: StoreDownload?,
    downloaded: Boolean,
    onClick: () -> Unit,
) {
    val status = download?.status

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            Text(
                text = game.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

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
private fun gameRowMeta(game: StoreGame): String? =
    game.sizeBytes?.takeIf { it > 0L }?.let { formatBytes(it) }

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
    onGameClick: (StoreGame) -> Unit,
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
                    download = downloads[game.id],
                    downloaded = game.id in downloadedIds,
                    onClick = { onGameClick(game) },
                )
            }
        }
    }
}

@Composable
private fun StoreGameTile(
    game: StoreGame,
    download: StoreDownload?,
    downloaded: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(TILE_WIDTH)
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClick),
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
) {
    val context = LocalContext.current
    val systemName = remember(game.system) { systemLabel(context, game) }

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
            }

            download != null && status == StoreDownloadStatus.DOWNLOADING -> {
                StoreProgressBar(fraction = download.fraction)

                Text(
                    text = downloadLabel(download = download),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            download != null && status == StoreDownloadStatus.FAILED -> {
                Text(
                    text = download.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                Button(onClick = onDownload) {
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

private fun systemLabel(
    context: Context,
    game: StoreGame,
): String {
    val systemId = game.resolveSystemId() ?: return game.system
    return runCatching {
        context.getString(GameSystem.findById(systemId.dbname).shortTitleResId)
    }.getOrDefault(game.system)
}
