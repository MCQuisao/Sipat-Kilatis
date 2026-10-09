package com.example.sipatkilatis.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.sipatkilatis.model.Verdict
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
}

@Composable
fun AppNavHost(vm: MainViewModel, nav: NavHostController = rememberNavController()) {
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()
    val pendingShare by vm.pendingShare.collectAsStateWithLifecycle()
    val pendingResult by vm.pendingResult.collectAsStateWithLifecycle()

    // Text shared from another app -> open the Check screen with it filled in
    LaunchedEffect(pendingShare, onboardingDone) {
        if (pendingShare && onboardingDone) {
            nav.navigate(Routes.CHECK) { launchSingleTop = true }
            vm.shareHandled()
        }
    }

    // Scam alert tapped -> show that scan's result
    LaunchedEffect(pendingResult, onboardingDone) {
        if (pendingResult && onboardingDone) {
            nav.navigate(Routes.RESULT) { launchSingleTop = true }
            vm.resultHandled()
        }
    }

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
                onHistory = { nav.navigate(Routes.HISTORY) },
                onGuide = { nav.navigate(Routes.GUIDE) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CHECK) {
            val text by vm.draftText.collectAsStateWithLifecycle()
            val scanning by vm.scanning.collectAsStateWithLifecycle()
            CheckMessageScreen(
                text = text,
                scanning = scanning,
                onTextChange = { vm.draftText.value = it },
                onScan = { vm.scan { nav.navigate(Routes.RESULT) } },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.RESULT) {
            val result by vm.lastResult.collectAsStateWithLifecycle()
            val explanation by vm.explanation.collectAsStateWithLifecycle()
            val record by vm.currentRecord.collectAsStateWithLifecycle()
            result?.let {
                ResultScreen(
                    result = it,
                    explanation = explanation,
                    record = record,
                    onMarkSafe = vm::markCurrentSafe,
                    onReport = vm::reportCurrent,
                    onScanAnother = {
                        vm.draftText.value = ""
                        nav.popBackStack(Routes.CHECK, inclusive = false)
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
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.GUIDE) {
            ScamGuideScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val sensitivity by vm.sensitivity.collectAsStateWithLifecycle()
            val trusted by vm.trustedContacts.collectAsStateWithLifecycle()
            val aiOn by vm.aiExplanations.collectAsStateWithLifecycle()
            SettingsScreen(
                sensitivity = sensitivity,
                trustedContacts = trusted,
                aiExplanations = aiOn,
                llmInstalled = vm.llmInstalled,
                llmSizeMb = vm.llmSizeMb,
                onSensitivityChange = vm::setSensitivity,
                onAiExplanationsChange = vm::setAiExplanations,
                onExport = vm::exportReports,
                onClearHistory = vm::clearHistory,
                onAddContact = vm::addTrustedContact,
                onRemoveContact = vm::removeTrustedContact,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
