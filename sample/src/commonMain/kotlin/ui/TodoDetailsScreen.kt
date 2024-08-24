package com.gyanoba.inspektor.sample.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.sample.ui.components.Center
import data.JsonPlaceHolderApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import models.Todo
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoDetailsScreen(
    todoId: Int,
    onBack: () -> Unit,
) {
    val viewModel = viewModel { TodoDetailsViewModel(todoId) }

    val todoResult by viewModel.todos.collectAsState()

    val todo = todoResult.getOrNull()

    Scaffold(contentWindowInsets = WindowInsets(top = 60.dp), topBar = {
        CenterAlignedTopAppBar(title = {
            Text("Todo #$todoId")
        }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        }, actions = {
            IconButton(onClick = { viewModel.getTodoDetails(todoId) }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
        })
    }) { padding ->
        Center(Modifier.padding(padding).fillMaxSize()) {
            if (todoResult.isFailure) {
                Text(
                    todoResult.exceptionOrNull()?.message ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error
                )
                return@Center
            }
            if (todo == null) {
                CircularProgressIndicator()
                return@Center
            }
            Text(text = todo.title, Modifier.padding(8.dp))
        }
    }
}

class TodoDetailsViewModel(
    todoId: Int,
) : ViewModel() {
    private val _todos = MutableStateFlow<Result<Todo?>>(Result.success(null))
    val todos = _todos.asStateFlow()

    init {
        getTodoDetails(todoId)
    }

    fun getTodoDetails(todoId: Int) = viewModelScope.launch {
        _todos.value = runCatching { JsonPlaceHolderApi.getTodo(todoId) }
    }
}


@Preview
@Composable
private fun PreviewTodoDetailsScreen() = MaterialTheme {
    TodoDetailsScreen(
        todoId = 1,
        onBack = {},
    )
}