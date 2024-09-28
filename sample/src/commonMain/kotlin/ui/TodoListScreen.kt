package com.gyanoba.inspektor.sample.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyanoba.inspektor.openInspektor
import com.gyanoba.inspektor.sample.ui.components.Center
import data.JsonPlaceHolderApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import models.Todo
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    onClick: (Todo) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = viewModel { TodoListViewModel() }
    val todos by viewModel.todos.collectAsState()

    Scaffold(modifier = Modifier.safeDrawingPadding(), topBar = {
        CenterAlignedTopAppBar(
            title = {
                Text("Sample")
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = {
                    viewModel.getTodos()
                }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                }
            },
        )
    }, contentWindowInsets = WindowInsets(top = 60.dp), floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { openInspektor() }) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Text("Open Inspektor")
        }
    }) { padding ->
        Center {
            if (todos.isFailure) {
                Column {
                    Text(
                        todos.exceptionOrNull()?.message ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = {
                            viewModel.getTodos()
                        },
                    ) {
                        Text("Retry")
                    }
                }

                return@Center
            }

            if (todos.getOrNull() == null) {
                CircularProgressIndicator()
                return@Center
            }
            LazyColumn(Modifier.padding(padding)) {
                items(todos.getOrThrow()) { todo ->
                    TodoCard(todo, onClick)
                }
            }
        }
    }
}

class TodoListViewModel : ViewModel() {
    private val _todos = MutableStateFlow<Result<List<Todo>>>(Result.success(emptyList()))
    val todos = _todos.asStateFlow()

    init {
        getTodos()
    }

    fun getTodos() = viewModelScope.launch {
        _todos.value = runCatching { JsonPlaceHolderApi.getTodos() }
    }

}


@Composable
fun TodoCard(todo: Todo, onClick: (Todo) -> Unit) {
    Card(Modifier.heightIn(min = 80.dp).padding(4.dp).clickable { onClick(todo) }) {
        Row(
            Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Text(
                "${todo.id}",
                fontWeight = FontWeight.W600,
                modifier = Modifier.width(60.dp)
            )
            Text(text = todo.title, Modifier.weight(1f))
            Icon(
                if (todo.completed) Icons.Rounded.Done else Icons.Rounded.Check,
                contentDescription = null
            )
        }
    }
}


@Preview
@Composable
private fun PreviewTodoListScreen() = MaterialTheme {
    TodoListScreen({}, {})
}