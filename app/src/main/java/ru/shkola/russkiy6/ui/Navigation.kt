package ru.shkola.russkiy6.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val HOME = "home"
    const val SECTION = "section/{sid}"
    const val THEORY = "theory/{sid}"
    const val EXERCISE = "exercise/{sid}/{id}"
    const val TEST = "test/{sid}/{id}"
    const val DICTATION = "dictation/{sid}/{id}"
    const val EDITOR = "editor?sid={sid}&type={type}"
    const val HELP = "help"

    fun section(sid: String) = "section/$sid"
    fun theory(sid: String) = "theory/$sid"
    fun exercise(sid: String, id: String) = "exercise/$sid/$id"
    fun test(sid: String, id: String) = "test/$sid/$id"
    fun dictation(sid: String, id: String) = "dictation/$sid/$id"
    fun editor(sid: String = "", type: String = ""): String {
        val params = listOfNotNull(
            sid.takeIf { it.isNotEmpty() }?.let { "sid=$it" },
            type.takeIf { it.isNotEmpty() }?.let { "type=$it" }
        )
        return if (params.isEmpty()) "editor" else "editor?" + params.joinToString("&")
    }
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val back: () -> Unit = { if (nav.previousBackStackEntry != null) nav.popBackStack() }
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                openSection = { nav.navigate(Routes.section(it)) },
                openEditor = { nav.navigate(Routes.editor()) },
                openHelp = { nav.navigate(Routes.HELP) }
            )
        }
        composable(Routes.SECTION) { e ->
            val sid = e.arguments?.getString("sid").orEmpty()
            SectionScreen(
                sid = sid,
                onBack = back,
                openTheory = { nav.navigate(Routes.theory(sid)) },
                openExercise = { nav.navigate(Routes.exercise(sid, it)) },
                openTest = { nav.navigate(Routes.test(sid, it)) },
                openDictation = { nav.navigate(Routes.dictation(sid, it)) },
                openEditor = { type -> nav.navigate(Routes.editor(sid, type)) }
            )
        }
        composable(Routes.THEORY) { e ->
            TheoryScreen(e.arguments?.getString("sid").orEmpty(), back)
        }
        composable(Routes.EXERCISE) { e ->
            ExerciseScreen(e.arguments?.getString("sid").orEmpty(), e.arguments?.getString("id").orEmpty(), back)
        }
        composable(Routes.TEST) { e ->
            TestScreen(e.arguments?.getString("sid").orEmpty(), e.arguments?.getString("id").orEmpty(), back)
        }
        composable(Routes.DICTATION) { e ->
            DictationScreen(e.arguments?.getString("sid").orEmpty(), e.arguments?.getString("id").orEmpty(), back)
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(
                navArgument("sid") { type = NavType.StringType; defaultValue = "" },
                navArgument("type") { type = NavType.StringType; defaultValue = "" }
            )
        ) { e ->
            EditorScreen(
                initialSection = e.arguments?.getString("sid").orEmpty(),
                initialType = e.arguments?.getString("type").orEmpty(),
                onBack = back
            )
        }
        composable(Routes.HELP) { HelpScreen(back) }
    }
}
