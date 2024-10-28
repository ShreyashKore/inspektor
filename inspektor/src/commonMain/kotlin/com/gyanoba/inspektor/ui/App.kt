package com.gyanoba.inspektor.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gyanoba.inspektor.ui.overriding.EditOverrideScreen
import com.gyanoba.inspektor.ui.overriding.OverridesListScreen
import com.gyanoba.inspektor.ui.theme.InspektorTheme

@Composable
internal fun App() = InspektorTheme {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "transactions") {
        composable("transactions") {
            TransactionListScreen(
                openTransaction = { id ->
                    navController.navigate("transaction/${id}")
                },
                openOverridesScreen = {
                    navController.navigate("overrides")
                },
                openAddOverrideScreen = { id ->
                    navController.navigate("add-override?transaction=${id}")
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
                    openAddOverrideScreen = {
                        navController.navigate("add-override?transactionId=${id}")
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
                overrideId = id ?: 0,
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        composable("add-override?transaction={transactionId}",
            arguments = listOf(navArgument("transactionId") {
                type = NavType.StringType; defaultValue = null; nullable = true
            })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getString("transactionId")?.toLongOrNull()
            EditOverrideScreen(
                overrideId = 0,
                onBack = {
                    navController.popBackStack()
                },
                transactionId = transactionId
            )
        }
    }
}