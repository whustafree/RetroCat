package com.swordfish.lemuroid.app.mobile.feature.catalog

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidEmptyView
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.RetroCatCoverImage
import com.swordfish.lemuroid.app.shared.systems.MetaSystemInfo
import com.swordfish.lemuroid.app.utils.games.GameUtils
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TILE_CORNER = 12.dp

@Composable
fun CatalogScreen(
    modifier: Modifier = Modifier,
    showChrome: Boolean = true,
    viewModel: CatalogViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGamePlay: (Game) -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit = { _, _ -> },
    onOpenPanel: () -> Unit = {},
) {
    val context = LocalContext.current
    val games = viewModel.games.collectAsLazyPagingItems()
    val systems by viewModel.systems.collectAsState(initial = emptyList())
    val uiState by viewModel.state.collectAsState(initial = CatalogUiState())
    val continuePlaying by viewModel.continuePlaying.collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    val filter = uiState.filter
    val showContinuePlaying = filter.query.isBlank() && filter.metaSystem == null
    val useLetterHeaders = uiState.groupedLetters && filter.sortOrder == CatalogSortOrder.NAME

    // Rows are built from the loaded prefix of the paging source. Reading items by index
    // registers them with paging, which is what makes the trailing sentinel request the next
    // page once the user reaches the end of the loaded content.
    val loadedCount = games.itemCount
    val loadedGames =
        remember(loadedCount) {
            List(loadedCount) { index -> games[index] }.filterNotNull()
        }

    LaunchedEffect(filter) {
        listState.scrollToItem(0)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight

        // The column count used to come straight from a fixed preference, so rotating the phone
        // stretched three tiles across a 800dp wide screen: each cover ended up taller than the
        // whole viewport and the artwork was pushed out of view. Deriving the count from the
        // real width keeps the covers the same size in either orientation.
        val targetTileWidth = uiState.gridDensity.minTileWidth
        val tileCount = catalogColumnCount(maxWidth, targetTileWidth)

        val rows =
            remember(loadedGames, tileCount, useLetterHeaders) {
                buildCatalogRows(loadedGames, tileCount, useLetterHeaders)
            }

        val empty = loadedCount == 0
        val refreshing = games.loadState.refresh is androidx.paging.LoadState.Loading

        Column(modifier = Modifier.fillMaxSize()) {
            // A landscape phone is only ~360dp tall, and the full search field plus both filter
            // rows add up to ~160dp of it. That left the carousel with almost no height, so the
            // covers were squeezed out of view.
            // Landscape drops the chrome into one compact row, and drops it entirely when the
            // in-game menu is closed. The condition used to be `landscape && showChrome`, so a
            // landscape screen with the chrome hidden fell into the `else` branch below and
            // rendered the full height search bar plus both filter rows on top of the covers.
            when {
                !landscape -> {
                    CatalogSearchBar(
                        query = filter.query,
                        onQueryChange = viewModel::changeQuery,
                    )

                    CatalogFilterBar(
                        systems = systems,
                        selectedSystem = filter.metaSystem,
                        sortOrder = filter.sortOrder,
                        gridDensity = uiState.gridDensity,
                        groupedLetters = uiState.groupedLetters,
                        onSystemSelected = viewModel::selectSystem,
                        onSortOrderSelected = viewModel::selectSortOrder,
                        onGridDensitySelected = viewModel::setGridDensity,
                        onGroupedLettersChange = viewModel::setGroupedLetters,
                    )
                }

                showChrome -> {
                    CatalogCompactBar(
                        modifier = Modifier.fillMaxWidth(),
                        onClosePanel = onOpenPanel,
                        query = filter.query,
                        systems = systems,
                        selectedSystem = filter.metaSystem,
                        sortOrder = filter.sortOrder,
                        gridDensity = uiState.gridDensity,
                        groupedLetters = uiState.groupedLetters,
                        onQueryChange = viewModel::changeQuery,
                        onSystemSelected = viewModel::selectSystem,
                        onSortOrderSelected = viewModel::selectSortOrder,
                        onGridDensitySelected = viewModel::setGridDensity,
                        onGroupedLettersChange = viewModel::setGroupedLetters,
                    )
                }
            }

            // `weight(1f)` rather than `fillMaxSize()`: the latter asks the list for the full
            // window height inside a Column that already holds the search and filter bars, which
            // pushed the bottom of the grid off screen.
            when {
                empty && refreshing -> Box(Modifier.weight(1f))
                empty ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        LemuroidEmptyView()
                    }
                landscape ->
                    CatalogCarousel(
                        games = loadedGames,
                        // Only offered when there is something to show: a filtered catalog with no
                        // matches should show the empty view instead of an empty shelf.
                        continuePlaying = continuePlaying.takeIf { showContinuePlaying }.orEmpty(),
                        context = context,
                        // Launching straight from the carousel skips the details dialog, which
                        // otherwise made "Play" open a second screen with another "Play" button.
                        onGameClick = onGameClick,
                        onGameLongClick = onGameLongClick,
                        onGameLaunch = onGamePlay,
                        onFavoriteToggle = onFavoriteToggle,
                        // Same append trigger as the grid: reading the last loaded index asks
                        // paging for the next page when the row reaches its end.
                        onReachedEnd = {
                            if (loadedCount > 0) {
                                games[loadedCount - 1]
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                else ->
                    CatalogGrid(
                        modifier = Modifier.weight(1f),
                        listState = listState,
                        rows = rows,
                        tileCount = tileCount,
                        showContinuePlaying = showContinuePlaying,
                        continuePlaying = continuePlaying,
                        context = context,
                        loadedCount = loadedCount,
                        games = games,
                        onGameClick = onGameClick,
                        onGameLongClick = onGameLongClick,
                    )
            }
        }

        // One-off controls hint. It explains the gesture before the user has to discover it, and
        // only appears the first time the catalog is seen in landscape, then fades on its own.
        var hintShown by rememberSaveable { mutableStateOf(false) }
        var hintVisible by remember { mutableStateOf(false) }

        LaunchedEffect(landscape) {
            if (landscape && !hintShown && !empty) {
                hintShown = true
                hintVisible = true
                delay(CONTROLS_HINT_DURATION_MS)
                hintVisible = false
            }
        }

        AnimatedVisibility(
            visible = hintVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(bottom = 92.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    text = stringResource(R.string.catalog_double_tap_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** How long the one-off landscape controls hint stays on screen. */
private const val CONTROLS_HINT_DURATION_MS = 4_000L

@Composable
private fun CatalogGrid(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    rows: List<CatalogRow>,
    tileCount: Int,
    showContinuePlaying: Boolean,
    continuePlaying: List<Game>,
    context: Context,
    loadedCount: Int,
    games: LazyPagingItems<Game>,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
    ) {
        if (showContinuePlaying && continuePlaying.isNotEmpty()) {
            item(key = "continue_playing") {
                ContinuePlayingRow(
                    games = continuePlaying,
                    onGameClick = onGameClick,
                    onGameLongClick = onGameLongClick,
                )
            }
        }

        items(
            items = rows,
            key = { it.key },
        ) { row ->
            when (row) {
                is CatalogRow.Header ->
                    LetterHeader(letter = row.letter)

                is CatalogRow.Games ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
                    ) {
                        row.games.forEach { game ->
                            CatalogGameTile(
                                modifier = Modifier.weight(1f),
                                game = game,
                                subtitle =
                                    remember(game.id) {
                                        GameUtils.getGameSubtitle(context, game)
                                    },
                                onClick = { onGameClick(game) },
                                onLongClick = { onGameLongClick(game) },
                            )
                        }

                        // Keep the last row's tiles the same width as full rows.
                        repeat(tileCount - row.games.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
            }
        }

        // Sentinel: composing it touches the last loaded game, which tells paging the
        // user reached the end of the loaded window and requests the next page.
        item(key = "append_trigger") {
            if (loadedCount > 0) {
                games[loadedCount - 1]
            }
        }

        if (games.loadState.append is androidx.paging.LoadState.Loading) {
            item(key = "append_loading") {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    text = stringResource(R.string.catalog_loading_more),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Landscape layout: a horizontal cover carousel that snaps to the centred game, the way console
 * libraries and Steam present covers. The centred cover grows and the neighbours shrink and fade,
 * and the details of the focused game sit underneath.
 *
 * The cover is sized from the available height rather than from a fixed width, so it always fits
 * inside the viewport no matter how short the landscape window is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogCarousel(
    games: List<Game>,
    continuePlaying: List<Game>,
    context: Context,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameLaunch: (Game) -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    onReachedEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val scope = rememberCoroutineScope()

    // Root focus target for the carousel, so a D-pad press anywhere in the row reaches the key
    // handler instead of being swallowed by a cover that has not been focused yet.
    val carouselFocusRequester = remember { FocusRequester() }

    // Selecting a game is a scroll, because the focused cover is derived from the scroll position.
    // That single choice keeps the D-pad, the arrow keys, a swipe and a tap all in agreement.
    fun focusGameAt(index: Int) {
        if (games.isEmpty()) return

        scope.launch {
            listState.animateScrollToItem(
                index = index.coerceIn(0, games.lastIndex),
                // Start-anchored: the cover lands against the leading padding instead of in the
                // middle of the viewport. A centre offset is what pushed the first two covers out
                // of frame, because it asks the row to scroll backwards past its own start.
                scrollOffset = 0,
            )
        }
    }

    // The focused cover is the leftmost one that is fully inside the viewport. Anchoring the
    // selection to the start, rather than to whatever is nearest the middle, is what keeps the
    // first games of the catalog sitting at the beginning of the shelf at rest.
    val focusedIndex by remember {
        derivedStateOf {
            val layout = listState.layoutInfo

            if (layout.visibleItemsInfo.isEmpty()) {
                0
            } else {
                layout.visibleItemsInfo
                    .firstOrNull { it.offset + it.size <= layout.viewportEndOffset }
                    ?.index
                    ?.coerceIn(0, games.lastIndex)
                    ?: 0
            }
        }
    }

    // A D-pad or arrow key press moves the selection one cover at a time rather than flinging the
    // row, which is how a console launcher behaves. Handled at preview level so it runs before the
    // list consumes the same event for its own scrolling.
    val onCarouselKeyEvent: (KeyEvent) -> Boolean =
        remember(games) {
            { keyEvent: KeyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    return@remember false
                }

                val step =
                    when (keyEvent.key) {
                        Key.DirectionRight, Key.DirectionDown -> 1
                        Key.DirectionLeft, Key.DirectionUp -> -1
                        else -> return@remember false
                    }

                focusGameAt(focusedIndex + step)

                true
            }
        }

    // Give the carousel focus as soon as it appears so a gamepad or keyboard can navigate it
    // without the user having to touch the screen first.
    LaunchedEffect(Unit) {
        runCatching { carouselFocusRequester.requestFocus() }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .onPreviewKeyEvent(onCarouselKeyEvent)
                // `focusable()` is required, not optional: a `FocusRequester` on a node that
                // cannot take focus makes `requestFocus()` throw, and the failure would be
                // swallowed by the `runCatching` below, leaving the D-pad dead with no clue why.
                .focusable()
                .focusRequester(carouselFocusRequester),
    ) {
        // The shelf only claims height when it actually has something to show, so a filtered
        // catalog with no "continue playing" games still gets the full viewport for the covers.
        val shelfBand = if (continuePlaying.isNotEmpty()) CONTINUE_PLAYING_BAND_HEIGHT else 0.dp

        // 3:4 portrait covers, fitted to the height left over once the details strip is placed.
        val coverWidth = carouselCoverSize(maxHeight - shelfBand).first

        // The focused game decides the background, so it is declared before the row that draws it.
        val focusedGame = games.getOrNull(focusedIndex)

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                // Full-bleed background taken from the focused game's own artwork, dimmed enough
                // that the covers in front of it stay readable. This is what makes the landscape
                // layout read as a hero screen rather than a list.
                CarouselHeroBackground(modifier = Modifier.fillMaxSize())

                // Continue playing sits above the carousel as a thin, scrollable shelf. It is the
                // first thing worth opening in this mode, so it gets its own row rather than being
                // buried in the catalog behind whatever filter is active.
                if (continuePlaying.isNotEmpty()) {
                    ContinuePlayingRow(
                        modifier = Modifier.align(Alignment.TopCenter),
                        games = continuePlaying,
                        onGameClick = onGameLongClick,
                        onGameLongClick = onGameLongClick,
                    )
                }

                LazyRow(
                    state = listState,
                    flingBehavior = flingBehavior,
                    modifier = Modifier.fillMaxSize(),
                    // Anchored to the start so the first cover sits [CAROUSEL_START_PADDING]
                    // from the edge. The shelf band is padding too, not an overlay, so the covers
                    // start below it instead of underneath it.
                    contentPadding =
                        PaddingValues(
                            start = CAROUSEL_START_PADDING,
                            top = shelfBand,
                            end = CATALOG_HORIZONTAL_PADDING,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(games.size, key = { games[it].id }) { index ->
                        val game = games[index]

                        CarouselCover(
                            modifier =
                                Modifier
                                    .width(coverWidth)
                                    .onFocusChanged { state ->
                                        // Taking focus with a D-pad, keyboard or Tab selects the
                                        // cover, so the details strip follows the focused item
                                        // rather than only the scroll position.
                                        if (state.isFocused) focusGameAt(index)
                                    },
                            game = game,
                            focused = index == focusedIndex,
                            // A single tap selects: bring the cover to the start of the row so the
                            // details strip and the scale animation follow it. Same start anchor
                            // as the D-pad, otherwise a tap and a key press would disagree.
                            onClick = { focusGameAt(index) },
                            onLongClick = { onGameLongClick(game) },
                            // A double tap is the "play" gesture of a console launcher, and it
                            // starts the game rather than opening the details dialog.
                            onDoubleClick = { onGameLaunch(game) },
                        )
                    }

                    // Paging sentinel, matching the grid: touching the last loaded game while this
                    // is composed tells paging the user reached the end of the loaded window and
                    // should fetch the next page. A bare 1dp box did not trigger it reliably.
                    item(key = "append_trigger") {
                        onReachedEnd()

                        Box(Modifier.size(1.dp))
                    }
                }
            }

            CarouselHero(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(CAROUSEL_DETAILS_HEIGHT),
                game = focusedGame,
                subtitle =
                    remember(focusedIndex) {
                        focusedGame?.let { GameUtils.getGameSubtitle(context, it) }
                    },
                onPlay = { focusedGame?.let(onGameLaunch) },
                onOpenDetails = { focusedGame?.let(onGameLongClick) },
                onFavoriteToggle = onFavoriteToggle,
            )
        }
    }
}

/**
 * Opaque backdrop for the carousel.
 *
 * This is deliberately a flat surface with nothing painted behind it. A blurred, half transparent
 * copy of the artwork used to sit under the row, and because the unfocused covers were drawn at
 * 40% alpha the effect read as two layers of artwork fighting each other. Big Picture wants one
 * flat field with the covers on top of it.
 */
@Composable
private fun CarouselHeroBackground(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface))
}

/**
 * The landscape hero strip: the focused game's title, its system and developer, and the play
 * control. Metadata sits on the left so it never collides with the centred cover.
 */
@Composable
private fun CarouselHero(
    game: Game?,
    subtitle: String?,
    onPlay: () -> Unit,
    onOpenDetails: () -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = game?.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (game != null) {
            IconButton(onClick = { onFavoriteToggle(game, !game.isFavorite) }) {
                Icon(
                    imageVector = if (game.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint =
                        if (game.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }

        // A secondary "more" control next to play, so the details dialog stays reachable without
        // the long press that a mouse or a trackpad user cannot easily perform.
        IconButton(onClick = onOpenDetails) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FilledTonalButton(onClick = onPlay, enabled = game != null) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                modifier = Modifier.padding(start = 6.dp),
                text =
                    if (game?.lastPlayedAt != null) {
                        stringResource(R.string.catalog_resume)
                    } else {
                        stringResource(R.string.catalog_play)
                    },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarouselCover(
    game: Game,
    focused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // `graphicsLayer` scales around the centre without changing the measured size, which is what
    // lets the focused cover grow over its neighbours the way a console carousel does. The unfocused
    // scale and alpha stay high on purpose: fading neighbours to a quarter opacity let the
    // background read straight through them, which looked like a rendering fault rather than depth.
    val scale by animateFloatAsState(if (focused) 1.08f else 0.9f, label = "coverScale")
    val alpha by animateFloatAsState(if (focused) 1f else 0.78f, label = "coverAlpha")
    val borderWidth by animateDpAsState(if (focused) 3.dp else 0.dp, label = "coverBorder")

    Box(
        modifier =
            modifier
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                // Big Picture interaction: a single tap only moves the selection onto this cover,
                // a double tap launches the game straight away and a long press opens the details.
                // The click callbacks are also the focus and keyboard activation callbacks, so a
                // D-pad or Enter on a focused cover goes through exactly the same path as a tap.
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onDoubleClick = onDoubleClick,
                ).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
    ) {
        Box(
            modifier =
                if (focused) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                },
        ) {
            RetroCatCoverImage(
                modifier = Modifier.fillMaxSize(),
                game = game,
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.6f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.75f),
                            ),
                        ),
            )

            if (borderWidth > 0.dp) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .border(
                                width = borderWidth,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                )
            }

            Text(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                text = game.title,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarouselDetails(
    game: Game?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The title and the subtitle share one line so the 64dp strip does not push the covers
        // further off screen on a short landscape display.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                modifier = Modifier.weight(1f, fill = false),
                text = game?.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!subtitle.isNullOrBlank()) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        FilledTonalButton(onClick = onClick, enabled = game != null) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                modifier = Modifier.padding(start = 6.dp),
                text = stringResource(R.string.catalog_play),
            )
        }
    }
}

private sealed interface CatalogRow {
    val key: String

    data class Header(val letter: String) : CatalogRow {
        override val key: String = "header_$letter"
    }

    data class Games(val rowIndex: Int, val games: List<Game>) : CatalogRow {
        override val key: String = "row_$rowIndex"
    }
}

private fun buildCatalogRows(
    games: List<Game>,
    tileCount: Int,
    useLetterHeaders: Boolean,
): List<CatalogRow> {
    val rows = mutableListOf<CatalogRow>()
    var pending = mutableListOf<Game>()
    var lastLetter: String? = null
    var rowIndex = 0

    fun flush() {
        if (pending.isEmpty()) return
        rows += CatalogRow.Games(rowIndex++, pending.toList())
        pending = mutableListOf()
    }

    games.forEach { game ->
        if (useLetterHeaders) {
            val letter = game.title.firstLetterOrNull() ?: "#"
            if (letter != lastLetter) {
                flush()
                lastLetter = letter
                rows += CatalogRow.Header(letter)
            }
        }
        pending += game
        if (pending.size == tileCount) flush()
    }
    flush()

    return rows
}

private fun String.firstLetterOrNull(): String {
    val char = firstOrNull { it.isLetterOrDigit() }
    return char?.uppercaseChar()?.toString() ?: "#"
}

@Composable
private fun LetterHeader(letter: String) {
    Text(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 0.dp),
        text = letter,
        style =
            MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
            ),
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogGameTile(
    modifier: Modifier = Modifier,
    game: Game,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(TILE_CORNER))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
        ) {
            RetroCatCoverImage(
                modifier = Modifier.fillMaxSize(),
                game = game,
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
            )

            Text(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                text = game.title,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (subtitle.isNotBlank()) {
            Text(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinuePlayingRow(
    games: List<Game>,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
            text = stringResource(R.string.catalog_continue_playing),
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
        ) {
            items(games.size, key = { games[it].id }) { index ->
                val game = games[index]

                ContinuePlayingCard(
                    game = game,
                    subtitle = remember(game.id) { GameUtils.getGameSubtitle(context, game) },
                    onClick = { onGameClick(game) },
                    onLongClick = { onGameLongClick(game) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinuePlayingCard(
    game: Game,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .size(width = 168.dp, height = 108.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = game.screenshotUrl ?: game.coverFrontUrl,
            contentDescription = game.title,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            contentScale = ContentScale.Crop,
        )

        Text(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            text = game.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogSearchBar(
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
                        contentDescription = stringResource(R.string.catalog_search_clear),
                    )
                }
            }
        },
        placeholder = { Text(stringResource(R.string.catalog_search_hint)) },
    )
}

/**
 * Single-row chrome for landscape. Keeps the search field and every filter reachable while
 * leaving the rest of the short viewport to the covers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogCompactBar(
    query: String,
    systems: List<MetaSystemInfo>,
    selectedSystem: MetaSystemID?,
    sortOrder: CatalogSortOrder,
    gridDensity: GridDensity,
    groupedLetters: Boolean,
    onQueryChange: (String) -> Unit,
    onSystemSelected: (MetaSystemID?) -> Unit,
    onSortOrderSelected: (CatalogSortOrder) -> Unit,
    onGridDensitySelected: (GridDensity) -> Unit,
    onGroupedLettersChange: (Boolean) -> Unit,
    onClosePanel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val selectedSystemLabel =
        if (selectedSystem == null) {
            stringResource(R.string.catalog_filter_all)
        } else {
            systems.firstOrNull { it.metaSystem == selectedSystem }?.let {
                remember(it.metaSystem) { context.getString(it.metaSystem.titleResId) }
            } ?: stringResource(R.string.catalog_filter_all)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.catalog_search_clear),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.catalog_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            },
        )

        CatalogCompactMenu(
            icon = Icons.Filled.FilterList,
            contentDescription = selectedSystemLabel,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.catalog_filter_all)) },
                onClick = {
                    onSystemSelected(null)
                    it()
                },
            )

            systems.forEach { system ->
                DropdownMenuItem(
                    text = { Text(context.getString(system.metaSystem.titleResId)) },
                    onClick = {
                        onSystemSelected(system.metaSystem)
                        it()
                    },
                )
            }
        }

        CatalogCompactMenu(
            icon = Icons.Filled.Sort,
            contentDescription = stringResource(R.string.catalog_sort_name),
        ) {
            CatalogSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(stringResource(order.labelRes)) },
                    onClick = {
                        onSortOrderSelected(order)
                        it()
                    },
                )
            }
        }

        CatalogCompactMenu(
            icon = Icons.Filled.ViewModule,
            contentDescription = stringResource(R.string.catalog_layout),
        ) {
            GridDensity.entries.forEach { density ->
                DropdownMenuItem(
                    text = { Text(stringResource(density.labelRes)) },
                    onClick = {
                        onGridDensitySelected(density)
                        it()
                    },
                )
            }

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text(stringResource(R.string.catalog_group_letters)) },
                onClick = {
                    onGroupedLettersChange(!groupedLetters)
                    it()
                },
            )
        }

        // Dismisses the whole panel, so the covers get the full viewport again. This is the
        // in-panel counterpart of the floating close button on the chrome itself.
        CatalogCompactMenu(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.catalog_hide_menu),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.catalog_hide_menu)) },
                onClick = {
                    onClosePanel()
                    it()
                },
            )
        }
    }
}

/** Icon button that opens a dropdown; the content lambda gets a dismiss callback. */
@Composable
private fun CatalogCompactMenu(
    icon: ImageVector,
    contentDescription: String,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

@Composable
private fun CatalogFilterBar(
    systems: List<MetaSystemInfo>,
    selectedSystem: MetaSystemID?,
    sortOrder: CatalogSortOrder,
    gridDensity: GridDensity,
    groupedLetters: Boolean,
    onSystemSelected: (MetaSystemID?) -> Unit,
    onSortOrderSelected: (CatalogSortOrder) -> Unit,
    onGridDensitySelected: (GridDensity) -> Unit,
    onGroupedLettersChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all") {
                CatalogFilterChip(
                    label = stringResource(R.string.catalog_filter_all),
                    selected = selectedSystem == null,
                    onClick = { onSystemSelected(null) },
                )
            }

            items(systems.size, key = { systems[it].metaSystem.name }) { index ->
                val system = systems[index]

                CatalogFilterChip(
                    label =
                        remember(system.metaSystem) {
                            context.getString(system.metaSystem.titleResId)
                        },
                    selected = selectedSystem == system.metaSystem,
                    onClick = { onSystemSelected(system.metaSystem) },
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = CatalogSortOrder.NAME.name) {
                CatalogFilterChip(
                    label = stringResource(R.string.catalog_sort_name),
                    selected = sortOrder == CatalogSortOrder.NAME,
                    onClick = { onSortOrderSelected(CatalogSortOrder.NAME) },
                )
            }
            item(key = CatalogSortOrder.RECENT.name) {
                CatalogFilterChip(
                    label = stringResource(R.string.catalog_sort_recent),
                    selected = sortOrder == CatalogSortOrder.RECENT,
                    onClick = { onSortOrderSelected(CatalogSortOrder.RECENT) },
                )
            }
            item(key = CatalogSortOrder.FAVORITES.name) {
                CatalogFilterChip(
                    label = stringResource(R.string.catalog_sort_favorites),
                    selected = sortOrder == CatalogSortOrder.FAVORITES,
                    onClick = { onSortOrderSelected(CatalogSortOrder.FAVORITES) },
                )
            }
            item(key = "layout_options") {
                LayoutOptionsMenu(
                    gridDensity = gridDensity,
                    groupedLetters = groupedLetters,
                    onGridDensitySelected = onGridDensitySelected,
                    onGroupedLettersChange = onGroupedLettersChange,
                )
            }
        }
    }
}

@Composable
private fun LayoutOptionsMenu(
    gridDensity: GridDensity,
    groupedLetters: Boolean,
    onGridDensitySelected: (GridDensity) -> Unit,
    onGroupedLettersChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        CatalogFilterChip(
            label = stringResource(R.string.catalog_layout),
            selected = false,
            onClick = { expanded = true },
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GridDensity.entries.forEach { density ->
                DropdownMenuItem(
                    text = { Text(stringResource(density.labelRes)) },
                    onClick = {
                        onGridDensitySelected(density)
                        expanded = false
                    },
                )
            }

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text(stringResource(R.string.catalog_group_letters)) },
                onClick = {
                    onGroupedLettersChange(!groupedLetters)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun HorizontalDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun CatalogFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    )
}
