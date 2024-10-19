package com.gyanoba.inspektor.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gyanoba.inspektor.ui.overriding.EditOverrideScreen
import com.gyanoba.inspektor.ui.overriding.OverridesListScreen

@Composable
internal fun App() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "transactions") {
        composable("transactions") {
            TransactionListScreen(
                openTransaction = { id ->
                    navController.navigate("transaction/${id}")
                },
                openOverridesScreen = {
                    navController.navigate("overrides")
                }
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

        composable("overrides") {
            OverridesListScreen(
                openEditOverrideScreen = {
                    navController.navigate(
                        if (it == null) "add-override" else "edit-override/${it}"
                    )
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        composable("edit-override/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
            EditOverrideScreen(
                id = id,
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        composable("add-override") {
            EditOverrideScreen(
                id = null,
                onBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}