package pt.estante.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun EstanteNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "books") {
        composable("books") {
            BooksScreen(
                onNewBook = { nav.navigate("newBook") },
                onOpenBook = { id -> nav.navigate("capture/$id") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("newBook") {
            NewBookScreen(
                onCreated = { id -> nav.navigate("capture/$id") { popUpTo("books") } },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = "capture/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) { entry ->
            CaptureScreen(
                bookId = entry.arguments?.getString("bookId").orEmpty(),
                onClose = { nav.popBackStack("books", inclusive = false) },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
