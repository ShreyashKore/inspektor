package com.gyanoba.inspektor.sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gyanoba.inspektor.sample.ui.MockApiScreen
import com.gyanoba.inspektor.sample.ui.TodoDetailsScreen
import com.gyanoba.inspektor.sample.ui.TodoListScreen


@Composable
internal fun App() = MaterialTheme {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "mock") {
        composable("mock") {
            MockApiScreen(
                openTodoListScreen = { navController.navigate("todos") },
            )
        }
        composable("todos") {
            TodoListScreen(
                onClick = { todo -> navController.navigate("todos/" + todo.id) },
                onBack = { navController.popBackStack() },
            )
        }
        composable("todos/{todoId}") { backStackEntry ->
            val todoId = backStackEntry.arguments?.getString("todoId")?.toIntOrNull()
            if (todoId != null) {
                TodoDetailsScreen(
                    todoId = todoId,
                    onBack = { navController.popBackStack() },
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
    }
}