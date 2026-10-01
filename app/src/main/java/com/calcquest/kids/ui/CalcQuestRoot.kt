package com.calcquest.kids.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.calcquest.kids.AppContainer
import com.calcquest.kids.ui.calculator.CalculatorScreen
import com.calcquest.kids.ui.calculator.CalculatorViewModel
import com.calcquest.kids.ui.calculator.HistoryScreen
import com.calcquest.kids.ui.completion.CompletionScreen
import com.calcquest.kids.ui.completion.CompletionViewModel
import com.calcquest.kids.ui.map.MapScreen
import com.calcquest.kids.ui.map.MapViewModel
import com.calcquest.kids.ui.parent.ParentScreen
import com.calcquest.kids.ui.parent.ParentViewModel
import com.calcquest.kids.ui.parent.PrivacyScreen
import com.calcquest.kids.ui.question.QuestionScreen
import com.calcquest.kids.ui.question.QuestionViewModel
import com.calcquest.kids.ui.theme.CalcQuestTheme

private object Routes {
    const val MAP = "map"
    const val QUEST = "quest/{attemptId}"
    const val COMPLETION = "completion/{attemptId}"
    const val CALCULATOR = "calculator"
    const val HISTORY = "calculator/history"
    const val PARENTS = "parents"
    const val PRIVACY = "parents/privacy"

    fun quest(id: Long) = "quest/$id"
    fun completion(id: Long) = "completion/$id"
}

/** Opens a quest on top of the map (never stacking quests on quests). */
private fun NavHostController.openQuest(attemptId: Long) {
    navigate(Routes.quest(attemptId)) {
        popUpTo(Routes.MAP)
        launchSingleTop = true
    }
}

private fun NavHostController.backToMap() {
    if (!popBackStack(Routes.MAP, inclusive = false)) {
        navigate(Routes.MAP) { launchSingleTop = true }
    }
}

@Composable
fun CalcQuestRoot(container: AppContainer) {
    CalcQuestTheme {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = Routes.MAP) {

            composable(Routes.MAP) {
                val vm: MapViewModel = viewModel {
                    MapViewModel(container.questRepository, container.settingsRepository)
                }
                MapScreen(
                    viewModel = vm,
                    onOpenAttempt = { nav.openQuest(it) },
                    onOpenCalculator = { nav.navigate(Routes.CALCULATOR) { launchSingleTop = true } },
                    onOpenParents = { nav.navigate(Routes.PARENTS) { launchSingleTop = true } },
                )
            }

            composable(
                Routes.QUEST,
                arguments = listOf(navArgument("attemptId") { type = NavType.LongType }),
            ) { entry ->
                val attemptId = entry.arguments?.getLong("attemptId") ?: 0L
                val vm: QuestionViewModel = viewModel(key = "quest-$attemptId") {
                    QuestionViewModel(
                        attemptId = attemptId,
                        quests = container.questRepository,
                        settings = container.settingsRepository,
                        clock = container.elapsedClock,
                        timersSeenThisProcess = container.timersSeenThisProcess,
                        applicationScope = container.applicationScope,
                        playSound = { container.soundPlayer.play(it) },
                    )
                }
                QuestionScreen(
                    viewModel = vm,
                    onBack = { nav.backToMap() },
                    onShowCompletion = { id ->
                        nav.navigate(Routes.completion(id)) {
                            popUpTo(Routes.MAP)
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                Routes.COMPLETION,
                arguments = listOf(navArgument("attemptId") { type = NavType.LongType }),
            ) { entry ->
                val attemptId = entry.arguments?.getLong("attemptId") ?: 0L
                val vm: CompletionViewModel = viewModel(key = "completion-$attemptId") {
                    CompletionViewModel(attemptId, container.questRepository, container.settingsRepository)
                }
                CompletionScreen(
                    viewModel = vm,
                    onBackToMap = { nav.backToMap() },
                    onOpenAttempt = { nav.openQuest(it) },
                )
            }

            composable(Routes.CALCULATOR) {
                val vm: CalculatorViewModel = viewModel {
                    CalculatorViewModel(container.calculatorRepository, container.settingsRepository, container.applicationScope)
                }
                CalculatorScreen(
                    viewModel = vm,
                    onBack = { nav.backToMap() },
                    onOpenHistory = { nav.navigate(Routes.HISTORY) { launchSingleTop = true } },
                )
            }

            composable(Routes.HISTORY) { entry ->
                // Share the calculator's ViewModel so "Use result" updates the calculator directly.
                val calculatorEntry = remember(entry) { nav.getBackStackEntry(Routes.CALCULATOR) }
                val vm: CalculatorViewModel = viewModel(viewModelStoreOwner = calculatorEntry) {
                    CalculatorViewModel(container.calculatorRepository, container.settingsRepository, container.applicationScope)
                }
                HistoryScreen(viewModel = vm, onBack = { nav.popBackStack() })
            }

            composable(Routes.PARENTS) {
                val vm: ParentViewModel = viewModel {
                    ParentViewModel(
                        container.settingsRepository,
                        container.questRepository,
                        container.calculatorRepository,
                        container.applicationScope,
                    )
                }
                ParentScreen(
                    viewModel = vm,
                    onClose = { nav.backToMap() },
                    onOpenPrivacy = { nav.navigate(Routes.PRIVACY) { launchSingleTop = true } },
                )
            }

            composable(Routes.PRIVACY) {
                PrivacyScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
