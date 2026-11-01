package com.swordfish.lemuroid.app.mobile.feature.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.flow.Flow

/**
 * Hosts the detail dialog for a single game id and keeps it in sync with the database, so
 * changes made elsewhere (context menu, favorites) are reflected without a manual copy.
 */
@Composable
fun GameDetailDialogHost(
    gameId: Int?,
    observeGame: (Int) -> Flow<Game?>,
    onDismiss: () -> Unit,
    onPlay: (Game) -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
) {
    if (gameId == null) return

    val gameFlow = remember(gameId) { observeGame(gameId) }
    val game by gameFlow.collectAsState(initial = null)

    game?.let {
        GameDetailDialog(
            game = it,
            onDismiss = onDismiss,
            onPlay = { onPlay(it) },
            onFavoriteToggle = { isFavorite -> onFavoriteToggle(it, isFavorite) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailDialog(
    game: Game,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onFavoriteToggle: (Boolean) -> Unit,
) {
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
                GameDetailContent(
                    modifier = Modifier.fillMaxSize(),
                    game = game,
                    onPlay = onPlay,
                    onFavoriteToggle = onFavoriteToggle,
                )

                IconButton(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    onClick = onDismiss,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            }
        }
    }
}
