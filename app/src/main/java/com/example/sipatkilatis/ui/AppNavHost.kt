package com.example.sipatkilatis.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.sipatkilatis.ui.components.FloatingNavBar
import com.example.sipatkilatis.ui.components.MainTabs
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.sipatkilatis.detection.RiskScorer
import com.example.sipatkilatis.model.Verdict
import com.example.sipatkilatis.ui.demo.DemoScreen
import com.example.sipatkilatis.ui.screens.CheckMessageScreen
import com.example.sipatkilatis.ui.screens.HistoryScreen
import com.example.sipatkilatis.ui.screens.HomeScreen
import com.example.sipatkilatis.ui.screens.OnboardingScreen
import com.example.sipatkilatis.ui.screens.ResultScreen
import com.example.sipatkilatis.ui.screens.ScamGuideScreen
import com.example.sipatkilatis.ui.screens.SettingsScreen

/** Screen routes. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CHECK = "check"
    const val RESULT = "result"
    const val HISTORY = "history"
    const val GUIDE = "guide"
    const val SETTINGS = "settings"
    const val DEMO = "demo"
}

@Composable
fun AppNavHost(vm: MainViewModel, nav: NavHostController = rememberNavController()) {
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()
    val pendingShare by vm.pendingShare.collectAsStateWithLifecycle()
    val pendingResult by vm.pendingResult.collectAsStateWithLifecycle()
    val pendingDemo by vm.pendingDemo.collectAsStateWithLifecycle()

    // Text shared from another app -> open the Check screen with it filled in.
    // The share is cleared by the Check screen itself, so it is not lost if this screen is destroyed mid-way.
    LaunchedEffect(pendingShare, onboardingDone) {
        if (pendingShare != null && onboardingDone) nav.navigate(Routes.CHECK) { launchSingleTop = true }
    }

    // Demo mode opened from adb ("open_demo" extra), used to check a build on a phone through logs only
    LaunchedEffect(pendingDemo, onboardingDone) {
        if (pendingDemo && onboardingDone) {
            nav.navigate(Routes.DEMO) { launchSingleTop = true }
            vm.demoHandled()
        }
    }

    // Scam alert tapped -> show that scan's result
    LaunchedEffect(pendingResult, onboardingDone) {
        if (pendingResult && onboardingDone) {
            nav.navigate(Routes.RESULT) { launchSingleTop = true }
            vm.resultHandled()
        }
    }

    // Floating nav bar on the four main screens only (not on Check, Result, Onboarding, Demo)
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val showNav = MainTabs.any { it.route == route }
    // Switch tabs like a bottom bar: one copy of each tab, back from any tab goes Home
    fun openTab(tab: String) = nav.navigate(tab) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Box(Modifier.fillMaxSize()) {
    NavHost(navController = nav, startDestination = if (onboardingDone) Routes.HOME else Routes.ONBOARDING) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinish = {
                vm.finishOnboarding()
                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            val history by vm.history.collectAsStateWithLifecycle()
            val protectionOn by vm.protectionOn.collectAsStateWithLifecycle()
            HomeScreen(
                protectionOn = protectionOn,
                scannedCount = history.size,
                scamCount = history.count { it.verdict == Verdict.SCAM },
                onProtectionChange = vm::setProtection,
                onCheckMessage = { nav.navigate(Routes.CHECK) },

            )
        }
        composable(Routes.CHECK) {
            LaunchedEffect(pendingShare) { if (pendingShare != null) vm.shareHandled() }
            val text by vm.draftText.collectAsStateWithLifecycle()
            val sender by vm.draftSender.collectAsStateWithLifecycle()
            val scanning by vm.scanning.collectAsStateWithLifecycle()
            CheckMessageScreen(
                text = text,
                sender = sender,
                scanning = scanning,
                onTextChange = { vm.draftText.value = it },
                onSenderChange = { vm.draftSender.value = it },
                onScan = { vm.scan { nav.navigate(Routes.RESULT) } },
                onCancel = vm::cancelScan,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.RESULT) {
            val result by vm.lastResult.collectAsStateWithLifecycle()
            val explanation by vm.explanation.collectAsStateWithLifecycle()
            val record by vm.currentRecord.collectAsStateWithLifecycle()
            val marks by vm.marks.collectAsStateWithLifecycle()
            val sensitivity by vm.sensitivity.collectAsStateWithLifecycle()
            result?.let {
                ResultScreen(
                    result = it,
                    explanation = explanation,
                    record = record,
                    marks = marks,
                    thresholds = RiskScorer.thresholds(sensitivity),
                    onMarkSafe = vm::markCurrentSafe,
                    onReport = vm::reportCurrent,
                    onScanAnother = {
                        vm.draftText.value = ""
                        vm.draftSender.value = ""
                        // Back to the Check screen (or open it when the result came from History / an alert)
                        if (!nav.popBackStack(Routes.CHECK, inclusive = false)) nav.navigate(Routes.CHECK)
                    },
                    onBack = { nav.popBackStack() },
                )
            }
        }
        composable(Routes.HISTORY) {
            val history by vm.history.collectAsStateWithLifecycle()
            HistoryScreen(
                history = history,
                onOpen = { id -> vm.openScan(id) { nav.navigate(Routes.RESULT) } },
                onDelete = vm::deleteScan,
                onRestore = vm::restoreScan,
            )
        }
        composable(Routes.DEMO) {
            DemoScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.GUIDE) {
            ScamGuideScreen()
        }
        composable(Routes.SETTINGS) {
            val sensitivity by vm.sensitivity.collectAsStateWithLifecycle()
            val trusted by vm.trustedContacts.collectAsStateWithLifecycle()
            val aiOn by vm.aiExplanations.collectAsStateWithLifecycle()
            val appearance by vm.appearance.collectAsStateWithLifecycle()
            SettingsScreen(
                sensitivity = sensitivity,
                appearance = appearance,
                trustedContacts = trusted,
                aiExplanations = aiOn,
                llmInstalled = vm.llmInstalled,
                llmSizeMb = vm.llmSizeMb,
                onSensitivityChange = vm::setSensitivity,
                onAppearanceChange = vm::setAppearance,
                onAiExplanationsChange = vm::setAiExplanations,
                onExport = vm::exportReports,
                onClearHistory = vm::clearHistory,
                onOpenDemo = { nav.navigate(Routes.DEMO) },
                onAddContact = vm::addTrustedContact,
                onRemoveContact = vm::removeTrustedContact,
            )
        }
    }
        AnimatedVisibility(
            visible = showNav,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            FloatingNavBar(current = route, onSelect = ::openTab)
        }
    }
}
