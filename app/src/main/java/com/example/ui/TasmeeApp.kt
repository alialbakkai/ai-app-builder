package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RecitationScreen
import com.example.ui.screens.ReportScreen
import com.example.ui.screens.ReviewAndHistoryScreen
import com.example.ui.screens.SettingsAndSourcesScreen
import com.example.ui.theme.TasmeeTheme
import com.example.ui.viewmodel.TasmeeViewModel

object TasmeeRoutes {
    const val HOME = "home"
    const val RECITE = "recite"
    const val REPORT = "report"
    const val REVIEW = "review"
    const val SETTINGS = "settings"
}

@Composable
fun TasmeeApp(viewModel: TasmeeViewModel = viewModel()) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val integrityStatus by viewModel.integrityStatus.collectAsStateWithLifecycle()
    val surahs by viewModel.surahs.collectAsStateWithLifecycle()
    val recitationUi by viewModel.recitationUi.collectAsStateWithLifecycle()
    val sessions by viewModel.sessionsHistory.collectAsStateWithLifecycle()
    val dueReviews by viewModel.dueReviews.collectAsStateWithLifecycle()
    val allReviewSchedules by viewModel.allReviewSchedules.collectAsStateWithLifecycle()
    val selectedReport by viewModel.selectedReport.collectAsStateWithLifecycle()
    val micRms by viewModel.micRmsLevel.collectAsStateWithLifecycle()
    val isSpeechDetected by viewModel.isSpeechDetected.collectAsStateWithLifecycle()
    val audioDiagnostic by viewModel.audioDiagnostic.collectAsStateWithLifecycle()
    val audioPlaybackState by viewModel.audioPlaybackState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route ?: TasmeeRoutes.HOME

    val showChromeNavigation = currentRoute in setOf(
        TasmeeRoutes.HOME,
        TasmeeRoutes.REVIEW,
        TasmeeRoutes.SETTINGS
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        TasmeeTheme(themePreference = preferences.themePreference) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 720.dp

                val navItems = listOf(
                    Triple(
                        TasmeeRoutes.HOME,
                        "التسميع",
                        Pair(Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook)
                    ),
                    Triple(
                        TasmeeRoutes.REVIEW,
                        "المراجعة",
                        Pair(Icons.Filled.History, Icons.Outlined.History)
                    ),
                    Triple(
                        TasmeeRoutes.SETTINGS,
                        "الإعدادات",
                        Pair(Icons.Filled.Settings, Icons.Outlined.Settings)
                    )
                )

                val navigateTopLevel: (String) -> Unit = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        if (showChromeNavigation && !useNavigationRail) {
                            NavigationBar {
                                navItems.forEach { (route, label, icons) ->
                                    val selected = currentDestination?.hierarchy?.any { it.route == route } == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTopLevel(route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) icons.first else icons.second,
                                                contentDescription = label
                                            )
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("nav_tab_$route")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (showChromeNavigation && useNavigationRail) {
                            NavigationRail {
                                navItems.forEach { (route, label, icons) ->
                                    val selected = currentDestination?.hierarchy?.any { it.route == route } == true
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateTopLevel(route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) icons.first else icons.second,
                                                contentDescription = label
                                            )
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("rail_tab_$route")
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = TasmeeRoutes.HOME,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(TasmeeRoutes.HOME) {
                                HomeScreen(
                                    integrityStatus = integrityStatus,
                                    surahs = surahs,
                                    dueReviews = dueReviews,
                                    recentSessions = sessions,
                                    onStartFreeMode = {
                                        viewModel.prepareFreeSession()
                                        navController.navigate(TasmeeRoutes.RECITE)
                                    },
                                    onStartSurahMode = { surahNo, mode ->
                                        viewModel.prepareSurahSession(surahNo, mode)
                                        navController.navigate(TasmeeRoutes.RECITE)
                                    },
                                    onStartJozz = { jozz ->
                                        viewModel.prepareJozzSession(jozz)
                                        navController.navigate(TasmeeRoutes.RECITE)
                                    },
                                    onStartHizb = { hizb ->
                                        viewModel.prepareHizbSession(hizb)
                                        navController.navigate(TasmeeRoutes.RECITE)
                                    },
                                    onStartReviewErrors = {
                                        viewModel.prepareReviewPastErrorsSession()
                                        navController.navigate(TasmeeRoutes.RECITE)
                                    }
                                )
                            }

                            composable(TasmeeRoutes.RECITE) {
                                RecitationScreen(
                                    uiState = recitationUi,
                                    micRmsLevel = micRms,
                                    isSpeechDetected = isSpeechDetected,
                                    audioDiagnostic = audioDiagnostic,
                                    audioPlaybackState = audioPlaybackState,
                                    fontSizeSp = preferences.mushafFontSizeSp,
                                    autoScrollEnabled = preferences.autoScrollEnabled,
                                    cloudVerificationEnabled = preferences.cloudVerificationEnabled,
                                    onBack = { navController.popBackStack() },
                                    onStartOrResumeMic = { viewModel.startOrResumeListening() },
                                    onPauseMic = { viewModel.pauseListening() },
                                    onRepeatAyah = { viewModel.repeatCurrentAyah() },
                                    onRequestHint = { viewModel.revealNextHint() },
                                    onToggleReferenceAudio = { viewModel.toggleReferenceAudioForCurrentSurah() },
                                    onAdjustFontSize = { delta -> viewModel.adjustFontSize(delta) },
                                    onToggleAutoScroll = {
                                        viewModel.setAutoScrollEnabled(!preferences.autoScrollEnabled)
                                    },
                                    onJumpToAyah = { idx -> viewModel.jumpToAyahIndex(idx) },
                                    onFinishSession = {
                                        viewModel.finishSessionAndSave {
                                            navController.navigate(TasmeeRoutes.REPORT) {
                                                popUpTo(TasmeeRoutes.HOME)
                                            }
                                        }
                                    }
                                )
                            }

                            composable(TasmeeRoutes.REPORT) {
                                ReportScreen(
                                    report = selectedReport,
                                    resolveWord = { tokenId -> viewModel.resolveDisplayWord(tokenId) },
                                    onBack = { navController.popBackStack() },
                                    onPracticeErrors = {
                                        viewModel.prepareReviewPastErrorsSession()
                                        navController.navigate(TasmeeRoutes.RECITE) {
                                            popUpTo(TasmeeRoutes.HOME)
                                        }
                                    }
                                )
                            }

                            composable(TasmeeRoutes.REVIEW) {
                                ReviewAndHistoryScreen(
                                    sessions = sessions,
                                    schedules = allReviewSchedules,
                                    onStartDueReview = {
                                        viewModel.prepareReviewPastErrorsSession()
                                        navController.navigate(TasmeeRoutes.RECITE)
                                    },
                                    onOpenReport = { sessionId ->
                                        viewModel.loadSessionReport(sessionId)
                                        navController.navigate(TasmeeRoutes.REPORT)
                                    },
                                    onDeleteSession = { sessionId -> viewModel.deleteSession(sessionId) },
                                    onWipeAllHistory = { viewModel.wipeAllHistory() }
                                )
                            }

                            composable(TasmeeRoutes.SETTINGS) {
                                SettingsAndSourcesScreen(
                                    preferences = preferences,
                                    integrityStatus = integrityStatus,
                                    onSetTheme = { viewModel.setThemePreference(it) },
                                    onAdjustFontDelta = { viewModel.adjustFontSize(it) },
                                    onSetAutoScroll = { viewModel.setAutoScrollEnabled(it) },
                                    onSetCloudVerification = { viewModel.setCloudVerificationEnabled(it) },
                                    onSelectReciter = { viewModel.setSelectedReciterId(it) },
                                    onWipeHistory = { viewModel.wipeAllHistory() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
