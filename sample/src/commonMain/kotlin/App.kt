package com.gyanoba.inspektor.sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.remote.InspektorRemote
import com.gyanoba.inspektor.sample.ui.MockApiScreen
import com.gyanoba.inspektor.sample.ui.TodoDetailsScreen
import com.gyanoba.inspektor.sample.ui.TodoListScreen


@OptIn(UnstableInspektorAPI::class)
@Composable
internal fun App() = MaterialTheme {
    // Lets Eagly show this app's traffic live (docs/REMOTE_PROTOCOL.md). Called from shared code,
    // so it reaches every build; what keeps it off in release is not this call:
    // - Android prod builds against the no-op artifacts, where nothing listens;
    // - every other build (iOS, desktop, Android dev) links the real library, and start() serves
    //   only a development build: debuggable on Android, the simulator or a development-signed app
    //   on iOS, and on desktop only `./gradlew :sample:run`, which opts in (see build.gradle.kts).
    LaunchedEffect(Unit) { InspektorRemote.start() }

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
            val todoId = backStackEntry.savedStateHandle.get<String>("todoId")?.toIntOrNull()
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