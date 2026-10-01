package com.swordfish.lemuroid.app.mobile.feature.main

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.catalog.CatalogPreferences
import com.swordfish.lemuroid.app.mobile.feature.catalog.CatalogScreen
import com.swordfish.lemuroid.app.mobile.feature.catalog.CatalogViewModel
import com.swordfish.lemuroid.app.mobile.feature.catalog.GameDetailDialogHost
import com.swordfish.lemuroid.app.mobile.feature.favorites.FavoritesScreen
import com.swordfish.lemuroid.app.mobile.feature.favorites.FavoritesViewModel
import com.swordfish.lemuroid.app.mobile.feature.games.GamesScreen
import com.swordfish.lemuroid.app.mobile.feature.games.GamesViewModel
import com.swordfish.lemuroid.app.mobile.feature.home.HomeScreen
import com.swordfish.lemuroid.app.mobile.feature.home.HomeViewModel
import com.swordfish.lemuroid.app.mobile.feature.search.SearchScreen
import com.swordfish.lemuroid.app.mobile.feature.search.SearchViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.advanced.AdvancedSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.advanced.AdvancedSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.mobile.feature.systems.MetaSystemsScreen
import com.swordfish.lemuroid.app.mobile.feature.systems.MetaSystemsViewModel
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.common.coroutines.safeLaunch
import com.swordfish.lemuroid.ext.feature.review.ReviewManager
import com.swordfish.lemuroid.lib.android.RetrogradeComponentActivity
import com.swordfish.lemuroid.lib.bios.BiosManager
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.injection.PerActivity
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import dagger.Provides
import de.charlex.compose.material3.HtmlText
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
/** How long the landscape menu stays on screen after the last interaction before hiding itself. */
private const val LANDSCAPE_CHROME_TIMEOUT_MS = 5_000L

class MainActivity : RetrogradeComponentActivity(), BusyActivity {
    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    @Inject
    lateinit var biosManager: BiosManager

    @Inject
    lateinit var coresSelection: CoresSelection

    @Inject
    lateinit var settingsInteractor: SettingsInteractor

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    private val reviewManager = ReviewManager()

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext, saveSyncManager)
    }

    // Hoisted to the activity so the catalog screen and the detail dialog share the
    // same instance and therefore the same filters and observed game.
    private val catalogViewModel: CatalogViewModel by viewModels {
        CatalogViewModel.Factory(retrogradeDb, CatalogPreferences(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        GlobalScope.safeLaunch {
            reviewManager.initialize(applicationContext)
        }

        setContent {
            val navController = rememberNavController()
            MainScreen(navController)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen(navController: NavHostController) {
        AppTheme {
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry.value?.destination
            val currentRoute =
                currentDestination?.route
                    ?.let { MainRoute.findByRoute(it) }
                    ?: MainRoute.HOME

            val infoDialogDisplayed =
                remember {
                    mutableStateOf(false)
                }

            LaunchedEffect(currentRoute) {
                mainViewModel.changeRoute(currentRoute)
            }

            val selectedGameState =
                remember {
                    mutableStateOf<Game?>(null)
                }

            val detailGameIdState =
                remember {
                    mutableStateOf<Int?>(null)
                }

            val onGameLongClick = { game: Game ->
                selectedGameState.value = game
            }

            val onGameClick = { game: Game ->
                gameInteractor.onGamePlay(game)
            }

            val onGameFavoriteToggle = { game: Game, isFavorite: Boolean ->
                gameInteractor.onFavoriteToggle(game, isFavorite)
            }

            val onHelpPressed = {
                infoDialogDisplayed.value = true
            }

            val mainUIState =
                mainViewModel.state
                    .collectAsState(MainViewModel.UiState())
                    .value

            // Landscape hides the top bar, the bottom navigation and the catalog search bar so
            // the covers get the whole screen. The chrome is still reachable through a floating
            // button, otherwise there would be no way back to settings or the other sections.
            val isLandscape = LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE
            // The landscape catalog starts chrome-less and the button toggles from there, so the
            // menu can be opened and closed again instead of only being revealed once.
            var chromeVisible by rememberSaveable { mutableStateOf(false) }
            // Bumped by any touch on the screen. The landscape menu hides itself again once this
            // stops changing, so recovering it never leaves the chrome stuck on top of the covers.
            var lastInteraction by remember { mutableIntStateOf(0) }
            // Only the catalog goes chrome-less: every other route keeps its back button and,
            // for the settings screens, has no bottom navigation of its own to fall back on.
            val showChrome =
                !isLandscape ||
                    chromeVisible ||
                    currentRoute != MainRoute.CATALOG

            // Auto-hide for the landscape menu, the way a console launcher does it. Only runs while
            // the chrome is actually showing over the catalog in landscape.
            LaunchedEffect(isLandscape, chromeVisible, currentRoute, lastInteraction) {
                if (isLandscape && chromeVisible && currentRoute == MainRoute.CATALOG) {
                    delay(LANDSCAPE_CHROME_TIMEOUT_MS)
                    chromeVisible = false
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // A press anywhere counts as activity, which restarts the auto-hide timer.
                        // `pointerInput` at the root observes without consuming the event.
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent()
                                    lastInteraction++
                                }
                            }
                        },
            ) {
                Scaffold(
                    topBar = {
                        AnimatedVisibility(showChrome, enter = slideInVertically(), exit = slideOutVertically()) {
                            MainTopBar(
                                currentRoute = currentRoute,
                                navController = navController,
                                onHelpPressed = onHelpPressed,
                                mainUIState = mainUIState,
                                onUpdateQueryString = { mainViewModel.changeQueryString(it) },
                            )
                        }
                    },
                    bottomBar = {
                        AnimatedVisibility(showChrome, enter = slideInVertically(), exit = slideOutVertically()) {
                            MainNavigationBar(currentRoute, navController)
                        }
                    },
                ) { padding ->
                    NavHost(
                        modifier = Modifier.fillMaxSize(),
                        navController = navController,
                        startDestination = MainRoute.CATALOG.route,
                    ) {
                        composable(MainRoute.CATALOG) {
                            CatalogScreen(
                                modifier = Modifier.padding(padding),
                                showChrome = showChrome,
                                viewModel = catalogViewModel,
                                onGameClick = { game ->
                                    detailGameIdState.value = game.id
                                },
                                onGameLongClick = onGameLongClick,
                                // The landscape carousel plays on tap instead of opening the
                                // details dialog, so it launches the game directly.
                                onGamePlay = onGameClick,
                                onFavoriteToggle = onGameFavoriteToggle,
                                // The in-panel close control hides the landscape chrome again,
                                // which is also what the auto-hide timer does after a few seconds.
                                onOpenPanel = { chromeVisible = false },
                            )
                        }
                        composable(MainRoute.HOME) {
                            HomeScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            HomeViewModel.Factory(
                                                applicationContext,
                                                retrogradeDb,
                                                coresSelection,
                                            ),
                                    ),
                                onGameClick = onGameClick,
                                onGameLongClick = onGameLongClick,
                                onOpenCoreSelection = {
                                    navController.navigateToRoute(
                                        MainRoute.SETTINGS_CORES_SELECTION,
                                    )
                                },
                            )
                        }
                        composable(MainRoute.FAVORITES) {
                            FavoritesScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory = FavoritesViewModel.Factory(retrogradeDb),
                                    ),
                                onGameClick = onGameClick,
                                onGameLongClick = onGameLongClick,
                            )
                        }
                        composable(MainRoute.SEARCH) {
                            SearchScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory = SearchViewModel.Factory(retrogradeDb),
                                    ),
                                searchQuery = mainUIState.searchQuery,
                                onGameClick = onGameClick,
                                onGameLongClick = onGameLongClick,
                                onGameFavoriteToggle = onGameFavoriteToggle,
                                onResetSearchQuery = { mainViewModel.changeQueryString("") },
                            )
                        }
                        composable(MainRoute.SYSTEMS) {
                            MetaSystemsScreen(
                                modifier = Modifier.padding(padding),
                                navController = navController,
                                viewModel =
                                    viewModel(
                                        factory =
                                            MetaSystemsViewModel.Factory(
                                                retrogradeDb,
                                                applicationContext,
                                            ),
                                    ),
                            )
                        }
                        composable(MainRoute.SYSTEM_GAMES) { entry ->
                            val metaSystemId = entry.arguments?.getString("metaSystemId")
                            GamesScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            GamesViewModel.Factory(
                                                retrogradeDb,
                                                MetaSystemID.valueOf(metaSystemId!!),
                                            ),
                                    ),
                                onGameClick = onGameClick,
                                onGameLongClick = onGameLongClick,
                                onGameFavoriteToggle = onGameFavoriteToggle,
                            )
                        }
                        composable(MainRoute.SETTINGS) {
                            SettingsScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            SettingsViewModel.Factory(
                                                applicationContext,
                                                settingsInteractor,
                                                saveSyncManager,
                                                FlowSharedPreferences(
                                                    SharedPreferencesHelper.getLegacySharedPreferences(
                                                        applicationContext,
                                                    ),
                                                ),
                                            ),
                                    ),
                                navController = navController,
                            )
                        }
                        composable(MainRoute.SETTINGS_ADVANCED) {
                            AdvancedSettingsScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            AdvancedSettingsViewModel.Factory(
                                                applicationContext,
                                                settingsInteractor,
                                            ),
                                    ),
                                navController = navController,
                            )
                        }
                        composable(MainRoute.SETTINGS_BIOS) {
                            BiosScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory = BiosSettingsViewModel.Factory(biosManager),
                                    ),
                            )
                        }
                        composable(MainRoute.SETTINGS_CORES_SELECTION) {
                            CoresSelectionScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            CoresSelectionViewModel.Factory(
                                                applicationContext,
                                                coresSelection,
                                            ),
                                    ),
                            )
                        }
                        composable(MainRoute.SETTINGS_INPUT_DEVICES) {
                            InputDevicesSettingsScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            InputDevicesSettingsViewModel.Factory(
                                                applicationContext,
                                                inputDeviceManager,
                                            ),
                                    ),
                            )
                        }
                        composable(MainRoute.SETTINGS_SAVE_SYNC) {
                            SaveSyncSettingsScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            SaveSyncSettingsViewModel.Factory(
                                                application,
                                                saveSyncManager,
                                            ),
                                    ),
                            )
                        }
                    }
                }

                // Chrome toggle for the chrome-less landscape catalog. It is always available in
                // landscape on the catalog so the menu can be opened and closed again.
                if (!showChrome) {
                    ChromeRevealButton(
                        onClick = { chromeVisible = true },
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                                .padding(16.dp),
                    )
                } else if (isLandscape && currentRoute == MainRoute.CATALOG) {
                    ChromeHideButton(
                        onClick = { chromeVisible = false },
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                                .padding(16.dp),
                    )
                }
            }

            MainGameContextActions(
                selectedGameState = selectedGameState,
                shortcutSupported = gameInteractor.supportShortcuts(),
                onGamePlay = { gameInteractor.onGamePlay(it) },
                onGameRestart = { gameInteractor.onGameRestart(it) },
                onFavoriteToggle = { game: Game, isFavorite: Boolean ->
                    gameInteractor.onFavoriteToggle(game, isFavorite)
                },
                onCreateShortcut = { gameInteractor.onCreateShortcut(it) },
            )

            // Observing by id keeps the dialog in sync with the database, so a favorite
            // toggled from the context menu is reflected without a manual copy.
            GameDetailDialogHost(
                gameId = detailGameIdState.value,
                observeGame = { id -> catalogViewModel.observeGame(id) },
                onDismiss = { detailGameIdState.value = null },
                onPlay = { game ->
                    detailGameIdState.value = null
                    gameInteractor.onGamePlay(game)
                },
                onFavoriteToggle = { game, isFavorite ->
                    gameInteractor.onFavoriteToggle(game, isFavorite)
                },
            )

            if (infoDialogDisplayed.value) {
                val message =
                    remember {
                        val systemFolders =
                            SystemID.values()
                                .joinToString(", ") { "<i>${it.dbname}</i>" }

                        getString(R.string.lemuroid_help_content)
                            .replace("\$SYSTEMS", systemFolders)
                    }

                AlertDialog(
                    text = { HtmlText(text = message) },
                    onDismissRequest = { infoDialogDisplayed.value = false },
                    confirmButton = { },
                )
            }
        }
    }

    /**
     * Small always-visible handle that brings the hidden top bar and bottom navigation back in
     * landscape, so hiding the chrome never traps the user out of settings and the other tabs.
     */
    @Composable
    private fun ChromeRevealButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        FilledTonalIconButton(onClick = onClick, modifier = modifier) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = stringResource(R.string.catalog_show_menu),
            )
        }
    }

    /** Closes the landscape menu again, returning the covers the whole screen. */
    @Composable
    private fun ChromeHideButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        FilledTonalIconButton(onClick = onClick, modifier = modifier) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.catalog_hide_menu),
            )
        }
    }

    override fun activity(): Activity = this

    override fun isBusy(): Boolean = mainViewModel.state.value.operationInProgress ?: false

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            BaseGameActivity.REQUEST_PLAY_GAME -> {
                GlobalScope.safeLaunch {
                    gameLaunchTaskHandler.handleGameFinish(
                        true,
                        this@MainActivity,
                        resultCode,
                        data,
                    )
                }
            }
        }
    }

    @dagger.Module
    abstract class Module {
        @dagger.Module
        companion object {
            @Provides
            @PerActivity
            @JvmStatic
            fun settingsInteractor(
                activity: MainActivity,
                directoriesManager: DirectoriesManager,
            ) = SettingsInteractor(activity, directoriesManager)

            @Provides
            @PerActivity
            @JvmStatic
            fun gameInteractor(
                activity: MainActivity,
                retrogradeDb: RetrogradeDatabase,
                shortcutsGenerator: ShortcutsGenerator,
                gameLauncher: GameLauncher,
            ) = GameInteractor(activity, retrogradeDb, false, shortcutsGenerator, gameLauncher)
        }
    }
}
