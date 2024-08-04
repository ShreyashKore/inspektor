package ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
internal fun App() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "transactions") {
        composable("transactions") {
            TransactionListScreen(
                openTransaction = {
                    navController.navigate("transaction/${it.id}")
                },
            )
        }
        composable("transaction/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
            if (id != null) {
                TransactionDetailsScreen(
                    id,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}