package com.swordfish.lemuroid.app.mobile.feature.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game

private val HERO_ASPECT = 16f / 9f

@Composable
fun GameDetailContent(
    modifier: Modifier = Modifier,
    game: Game,
    onPlay: () -> Unit,
    onFavoriteToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val systemName =
        remember(game.systemId) {
            context.getString(GameSystem.findById(game.systemId).shortTitleResId)
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
    ) {
        RetroCatHeroImage(game = game)

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = game.title,
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
            )

            Text(
                text =
                    buildString {
                        append(systemName)
                        game.developer?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onPlay,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = stringResource(R.string.catalog_detail_play),
                    )
                }

                OutlinedButton(onClick = { onFavoriteToggle(!game.isFavorite) }) {
                    Icon(
                        imageVector =
                            if (game.isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Filled.FavoriteBorder
                            },
                        contentDescription =
                            stringResource(
                                if (game.isFavorite) {
                                    R.string.catalog_detail_unfavorite
                                } else {
                                    R.string.catalog_detail_favorite
                                },
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Hero banner. Falls back to a neutral placeholder rather than the portrait cover art,
 * which would be badly cropped to 16:9.
 */
@Composable
private fun RetroCatHeroImage(game: Game) {
    val url = game.screenshotUrl

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(HERO_ASPECT)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            HeroPlaceholder()
        } else {
            val painter =
                rememberAsyncImagePainter(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                )

            when (painter.state) {
                is AsyncImagePainter.State.Loading -> CircularProgressIndicator()

                is AsyncImagePainter.State.Error -> HeroPlaceholder()

                else ->
                    Image(
                        painter = painter,
                        contentDescription = game.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
            }
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
private fun HeroPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.catalog_detail_no_screenshot),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun RetroCatCompactInfo(
    modifier: Modifier = Modifier,
    game: Game,
) {
    val context = LocalContext.current
    val systemName =
        remember(game.systemId) {
            context.getString(GameSystem.findById(game.systemId).shortTitleResId)
        }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = game.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = systemName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
