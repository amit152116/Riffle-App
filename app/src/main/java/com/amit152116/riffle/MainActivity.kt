package com.amit152116.riffle

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amit152116.riffle.data.AppContainer
import com.amit152116.riffle.permission.MediaPermission
import com.amit152116.riffle.permission.PermissionRationaleScreen
import com.amit152116.riffle.ui.album.AlbumDetailScreen
import com.amit152116.riffle.ui.artist.ArtistDetailScreen
import com.amit152116.riffle.ui.folder.FolderDetailScreen
import com.amit152116.riffle.ui.home.HomeScreen
import com.amit152116.riffle.ui.home.HomeSectionDetailScreen
import com.amit152116.riffle.ui.insights.DuplicateReviewScreen
import com.amit152116.riffle.ui.insights.InsightsScreen
import com.amit152116.riffle.ui.library.LibraryCatalogScreen
import com.amit152116.riffle.ui.library.LibraryViewModel
import com.amit152116.riffle.ui.list.TrackListScreen
import com.amit152116.riffle.ui.player.AudioFeaturesScreen
import com.amit152116.riffle.ui.player.MiniPlayer
import com.amit152116.riffle.ui.player.PlayerScreen
import com.amit152116.riffle.ui.player.PlayerViewModel
import com.amit152116.riffle.ui.playlist.PlaylistDetailScreen
import com.amit152116.riffle.ui.search.SearchScreen
import com.amit152116.riffle.ui.settings.SettingsScreen
import com.amit152116.riffle.ui.shuffle.ShuffleScreen
import com.amit152116.riffle.ui.theme.RiffleTheme

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Filled.Home)
    data object Library : Screen("library", "Library", Icons.Filled.LibraryMusic)
    data object Shuffle : Screen("shuffle", "Shuffle", Icons.Filled.Shuffle)
    data object Insights : Screen("insights", "Insights", Icons.Filled.Analytics)
}

/**
 * Routes reached by navigation rather than the bottom bar - no title/icon,
 * so these don't fit Screen. route is the pattern passed to composable();
 * createRoute() is what navigate() calls actually build, so the encoding
 * (or lack of it, for section - matches the pre-existing behavior) lives in
 * exactly one place instead of being retyped at every call site.
 */
sealed class DetailRoute(val route: String) {
    data object Search : DetailRoute("search")

    data object HomeSectionDetail : DetailRoute("home-section/{section}") {
        const val ARG_SECTION = "section"
        fun createRoute(section: String) = "home-section/$section"
    }

    data object Artist : DetailRoute("artist/{artistName}") {
        const val ARG_ARTIST_NAME = "artistName"
        fun createRoute(artistName: String) = "artist/${Uri.encode(artistName)}"
    }

    data object Album : DetailRoute("album/{albumName}") {
        const val ARG_ALBUM_NAME = "albumName"
        fun createRoute(albumName: String) = "album/${Uri.encode(albumName)}"
    }

    data object Folder : DetailRoute("folder/{folderPath}") {
        const val ARG_FOLDER_PATH = "folderPath"
        fun createRoute(folderPath: String) = "folder/${Uri.encode(folderPath)}"
    }

    data object DuplicateReview : DetailRoute("duplicate-review")
    data object AudioFeatures : DetailRoute("audio-features")

    data object TrackList : DetailRoute("track-list/{listType}") {
        const val ARG_LIST_TYPE = "listType"
        fun createRoute(listType: String) = "track-list/${Uri.encode(listType)}"
    }

    data object PlaylistDetail : DetailRoute("playlist/{playlistId}") {
        const val ARG_PLAYLIST_ID = "playlistId"
        fun createRoute(playlistId: Long) = "playlist/$playlistId"
    }
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Library,
    Screen.Shuffle,
    Screen.Insights
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RiffleTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val appContainer = AppContainer.getInstance(context)

                val playerViewModel: PlayerViewModel =
                    viewModel(factory = appContainer.playerViewModelFactory)
                val libraryViewModel: LibraryViewModel =
                    viewModel(factory = appContainer.libraryViewModelFactory)

                var isPlayerOpen by remember { mutableStateOf(false) }
                var isSettingsOpen by remember { mutableStateOf(false) }

                var hasMediaPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(context, MediaPermission.readAudio) ==
                                PackageManager.PERMISSION_GRANTED
                    )
                }
                var permanentlyDenied by remember { mutableStateOf(false) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasMediaPermission = granted
                    if (granted) {
                        libraryViewModel.scanLibrary()
                    } else {
                        permanentlyDenied =
                            !shouldShowRequestPermissionRationale(MediaPermission.readAudio)
                    }
                }

                LaunchedEffect(Unit) {
                    if (hasMediaPermission) {
                        // songs/albums/artists/genres are backed by a Room Flow (see
                        // LibraryViewModel.init) and update on their own as this lands -
                        // no separate "reload" call needed once it completes.
                        libraryViewModel.scanLibrary()
                    } else {
                        permissionLauncher.launch(MediaPermission.readAudio)
                    }
                }

                // Android 13+: without this the library-analysis progress notification is hidden (the foreground
                // service still runs). Asked once, after the audio permission, and only if not already decided.
                val notificationLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(hasMediaPermission) {
                    if (hasMediaPermission && android.os.Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                if (!hasMediaPermission) {
                    PermissionRationaleScreen(
                        permanentlyDenied = permanentlyDenied,
                        onRequestPermission = { permissionLauncher.launch(MediaPermission.readAudio) },
                        onOpenSettings = {
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                            context.startActivity(intent)
                        }
                    )
                    return@RiffleTheme
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        // Each destination owns its TopAppBar and therefore its
                        // top system-bar inset. The root scaffold only owns the
                        // bottom navigation layout, so don't pass the top inset
                        // into the NavHost as well.
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            Column {
                                MiniPlayer(
                                    viewModel = playerViewModel,
                                    modifier = Modifier,
                                    onClick = { isPlayerOpen = true }
                                )
                                NavigationBar(modifier = Modifier.height(68.dp)) {
                                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                                    val currentDestination = navBackStackEntry?.destination

                                    bottomNavItems.forEach { screen ->
                                        val isSelected = currentDestination?.hierarchy?.any {
                                            it.route == screen.route
                                        } == true
                                        val iconScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.15f else 1f,
                                            animationSpec = spring(stiffness = 500f),
                                            label = "bottom-nav-icon-scale"
                                        )

                                        NavigationBarItem(
                                            icon = {
                                                Icon(
                                                    screen.icon,
                                                    contentDescription = screen.title,
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .scale(iconScale)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    screen.title,
                                                    modifier = Modifier.offset(y = (-2).dp)
                                                )
                                            },
                                            selected = isSelected,
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = Color.Transparent,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            onClick = {
                                                navController.navigate(screen.route) {
                                                    // Bottom navigation should leave detail/settings pages
                                                    // behind and always show the selected top-level screen.
                                                    popUpTo(Screen.Home.route) {
                                                        saveState = false
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = false
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route,
                            modifier = Modifier.padding(innerPadding),
                            enterTransition = {
                                slideInHorizontally(
                                    animationSpec = tween(240, easing = LinearOutSlowInEasing),
                                    initialOffsetX = { it / 6 }
                                ) + fadeIn(
                                    animationSpec = tween(
                                        240,
                                        easing = LinearOutSlowInEasing
                                    )
                                )
                            },
                            exitTransition = {
                                slideOutHorizontally(
                                    animationSpec = tween(240, easing = LinearOutSlowInEasing),
                                    targetOffsetX = { -it / 6 }
                                ) + fadeOut(
                                    animationSpec = tween(
                                        240,
                                        easing = LinearOutSlowInEasing
                                    )
                                )
                            },
                            popEnterTransition = {
                                slideInHorizontally(
                                    animationSpec = tween(240, easing = LinearOutSlowInEasing),
                                    initialOffsetX = { -it / 6 }
                                ) + fadeIn(
                                    animationSpec = tween(
                                        240,
                                        easing = LinearOutSlowInEasing
                                    )
                                )
                            },
                            popExitTransition = {
                                slideOutHorizontally(
                                    animationSpec = tween(240, easing = LinearOutSlowInEasing),
                                    targetOffsetX = { it / 6 }
                                ) + fadeOut(
                                    animationSpec = tween(
                                        240,
                                        easing = LinearOutSlowInEasing
                                    )
                                )
                            }
                        ) {
                            composable(Screen.Home.route) {
                                HomeScreen(
                                    playerViewModel = playerViewModel,
                                    libraryViewModel = libraryViewModel,
                                    onNavigateToSettings = { isSettingsOpen = true },
                                    onNavigateToSection = { section ->
                                        navController.navigate(
                                            DetailRoute.HomeSectionDetail.createRoute(
                                                section
                                            )
                                        )
                                    },
                                    onNavigateToArtist = { artistName ->
                                        navController.navigate(
                                            DetailRoute.Artist.createRoute(
                                                artistName
                                            )
                                        )
                                    },
                                    onNavigateToAlbum = { albumName ->
                                        navController.navigate(
                                            DetailRoute.Album.createRoute(
                                                albumName
                                            )
                                        )
                                    },
                                    onNavigateToSearch = {
                                        navController.navigate(DetailRoute.Search.route)
                                    },
                                    onNavigateToList = { listType ->
                                        navController.navigate(
                                            DetailRoute.TrackList.createRoute(
                                                listType
                                            )
                                        )
                                    }
                                )
                            }
                            composable(
                                DetailRoute.HomeSectionDetail.route,
                                arguments = listOf(navArgument(DetailRoute.HomeSectionDetail.ARG_SECTION) {
                                    type = NavType.StringType
                                })
                            ) { backStackEntry ->
                                HomeSectionDetailScreen(
                                    type = backStackEntry.arguments?.getString(DetailRoute.HomeSectionDetail.ARG_SECTION)
                                        ?: "",
                                    libraryViewModel = libraryViewModel,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToArtist = { artistName ->
                                        navController.navigate(
                                            DetailRoute.Artist.createRoute(
                                                artistName
                                            )
                                        )
                                    },
                                    onNavigateToAlbum = { albumName ->
                                        navController.navigate(
                                            DetailRoute.Album.createRoute(
                                                albumName
                                            )
                                        )
                                    }
                                )
                            }
                            composable(Screen.Library.route) {
                                LibraryCatalogScreen(
                                    libraryViewModel = libraryViewModel,
                                    playerViewModel = playerViewModel,
                                    onNavigateToArtist = { artistName ->
                                        navController.navigate(
                                            DetailRoute.Artist.createRoute(
                                                artistName
                                            )
                                        )
                                    },
                                    onNavigateToAlbum = { albumName ->
                                        navController.navigate(
                                            DetailRoute.Album.createRoute(
                                                albumName
                                            )
                                        )
                                    },
                                    onNavigateToFolder = { folderPath ->
                                        navController.navigate(
                                            DetailRoute.Folder.createRoute(
                                                folderPath
                                            )
                                        )
                                    },
                                    onNavigateToSearch = { navController.navigate(DetailRoute.Search.route) },
                                    onOpenPlaylist = { playlistId ->
                                        navController.navigate(
                                            DetailRoute.PlaylistDetail.createRoute(
                                                playlistId
                                            )
                                        )
                                    },
                                    onNavigateToSettings = { isSettingsOpen = true }
                                )
                            }
                            composable(DetailRoute.Search.route) {
                                SearchScreen(
                                    libraryViewModel = libraryViewModel,
                                    playerViewModel = playerViewModel,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToArtist = { artistName ->
                                        navController.navigate(
                                            DetailRoute.Artist.createRoute(
                                                artistName
                                            )
                                        )
                                    },
                                    onNavigateToAlbum = { albumName ->
                                        navController.navigate(
                                            DetailRoute.Album.createRoute(
                                                albumName
                                            )
                                        )
                                    }
                                )
                            }
                            composable(
                                DetailRoute.Artist.route,
                                arguments = listOf(navArgument(DetailRoute.Artist.ARG_ARTIST_NAME) {
                                    type = NavType.StringType
                                })
                            ) { backStackEntry ->
                                val artistName =
                                    backStackEntry.arguments?.getString(DetailRoute.Artist.ARG_ARTIST_NAME)
                                        ?: ""
                                ArtistDetailScreen(
                                    artistName = artistName,
                                    libraryViewModel = libraryViewModel,
                                    playerViewModel = playerViewModel,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToAlbum = { albumName ->
                                        navController.navigate(
                                            DetailRoute.Album.createRoute(
                                                albumName
                                            )
                                        )
                                    }
                                )
                            }
                            composable(
                                DetailRoute.Album.route,
                                arguments = listOf(navArgument(DetailRoute.Album.ARG_ALBUM_NAME) {
                                    type = NavType.StringType
                                })
                            ) { backStackEntry ->
                                val albumName =
                                    backStackEntry.arguments?.getString(DetailRoute.Album.ARG_ALBUM_NAME)
                                        ?: ""
                                AlbumDetailScreen(
                                    albumName = albumName,
                                    libraryViewModel = libraryViewModel,
                                    playerViewModel = playerViewModel,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToArtist = { artistName ->
                                        navController.navigate(
                                            DetailRoute.Artist.createRoute(
                                                artistName
                                            )
                                        )
                                    }
                                )
                            }
                            composable(
                                DetailRoute.Folder.route,
                                arguments = listOf(navArgument(DetailRoute.Folder.ARG_FOLDER_PATH) {
                                    type = NavType.StringType
                                })
                            ) { backStackEntry ->
                                FolderDetailScreen(
                                    folderPath = backStackEntry.arguments?.getString(DetailRoute.Folder.ARG_FOLDER_PATH)
                                        ?: "",
                                    libraryViewModel = libraryViewModel,
                                    playerViewModel = playerViewModel,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToArtist = { artistName ->
                                        navController.navigate(
                                            DetailRoute.Artist.createRoute(
                                                artistName
                                            )
                                        )
                                    },
                                    onNavigateToAlbum = { albumName ->
                                        navController.navigate(
                                            DetailRoute.Album.createRoute(
                                                albumName
                                            )
                                        )
                                    }
                                )
                            }
                            composable(Screen.Shuffle.route) {
                                ShuffleScreen()
                            }
                            composable(Screen.Insights.route) {
                                InsightsScreen(
                                    onNavigateToDuplicates = { navController.navigate(DetailRoute.DuplicateReview.route) }
                                )
                            }
                            composable(DetailRoute.DuplicateReview.route) {
                                DuplicateReviewScreen(onBack = { navController.popBackStack() })
                            }
                            composable(DetailRoute.AudioFeatures.route) {
                                AudioFeaturesScreen(onBack = { navController.popBackStack() })
                            }

                            composable(
                                route = DetailRoute.TrackList.route,
                                arguments = listOf(navArgument(DetailRoute.TrackList.ARG_LIST_TYPE) {
                                    type = NavType.StringType
                                })
                            ) { backStackEntry ->
                                val listType =
                                    backStackEntry.arguments?.getString(DetailRoute.TrackList.ARG_LIST_TYPE)
                                        ?: ""

                                TrackListScreen(
                                    listType = listType,
                                    playerViewModel = playerViewModel,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToArtist = { artistName ->
                                        navController.navigate(
                                            DetailRoute.Artist.createRoute(
                                                artistName
                                            )
                                        )
                                    },
                                    onNavigateToAlbum = { albumName ->
                                        navController.navigate(
                                            DetailRoute.Album.createRoute(
                                                albumName
                                            )
                                        )
                                    }
                                )
                            }

                            composable(
                                route = DetailRoute.PlaylistDetail.route,
                                arguments = listOf(navArgument(DetailRoute.PlaylistDetail.ARG_PLAYLIST_ID) {
                                    type = NavType.LongType
                                })
                            ) { backStackEntry ->
                                val playlistId =
                                    backStackEntry.arguments?.getLong(DetailRoute.PlaylistDetail.ARG_PLAYLIST_ID)
                                        ?: 0L
                                PlaylistDetailScreen(
                                    playlistId = playlistId,
                                    playerViewModel = playerViewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isPlayerOpen,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        BackHandler(enabled = isPlayerOpen) {
                            isPlayerOpen = false
                        }
                        PlayerScreen(
                            viewModel = playerViewModel,
                            onNavigateToArtist = { artistName ->
                                isPlayerOpen = false
                                navController.navigate(DetailRoute.Artist.createRoute(artistName))
                            },
                            onNavigateToAlbum = { albumName ->
                                isPlayerOpen = false
                                navController.navigate(DetailRoute.Album.createRoute(albumName))
                            },
                            onClose = { isPlayerOpen = false }
                        )
                    }

                    AnimatedVisibility(
                        visible = isSettingsOpen,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        BackHandler(enabled = isSettingsOpen) {
                            isSettingsOpen = false
                        }
                        SettingsScreen(
                            playerViewModel = playerViewModel,
                            libraryViewModel = libraryViewModel,
                            onBack = { isSettingsOpen = false },
                            onNavigateToBlacklist = {
                                isSettingsOpen = false
                                navController.navigate(DetailRoute.TrackList.createRoute("blacklist"))
                            }
                        )
                    }
                }
            }
        }
    }
}
