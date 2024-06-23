import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import data.Api
import models.Todo
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
internal fun App() = MaterialTheme {
    var currentScreen by remember { mutableStateOf<Page>(Page.ListScreen) }
    AnimatedContent(currentScreen) {
        when (it) {
            is Page.ListScreen -> TodoListScreen(
                onClick = { todo -> currentScreen = Page.DetailsScreen(todo.id) },
            )

            is Page.DetailsScreen -> TodoDetailsScreen(it.todo)
        }
    }
}

@Composable
fun TodoListScreen(
    onClick: (Todo) -> Unit
) {
    var todos by remember { mutableStateOf<Result<List<Todo>>?>(null) }
    LaunchedEffect(Unit) {
        todos = runCatching {
            Api.getTodos()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(top = 60.dp)
    ) { padding ->
        Center {
            if (todos == null) {
                CircularProgressIndicator()
                return@Center
            }
            if (todos!!.isFailure) {
                Text(todos!!.exceptionOrNull()?.message ?: "Unknown error")
                return@Center
            }
            LazyColumn(Modifier.padding(padding)) {
                items(todos!!.getOrThrow()) { todo ->
                    TodoCard(todo, onClick)
                }
            }
        }
    }
}


@Composable
fun TodoCard(todo: Todo, onClick: (Todo) -> Unit) {
    Card(Modifier.heightIn(min = 80.dp).padding(4.dp).clickable { onClick(todo) }) {
        Row(
            Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Text(text = todo.title, Modifier.weight(1f))
            Icon(
                if (todo.completed) Icons.Rounded.Done else Icons.Rounded.Check,
                contentDescription = null
            )
        }
    }
}

@Composable
fun Center(modifier: Modifier = Modifier.fillMaxSize(), content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        content()
    }
}


@Composable
fun TodoDetailsScreen(
    todoId: Int
) {
    var todo by remember { mutableStateOf<Todo?>(null) }
    LaunchedEffect(todoId) {
        todo = Api.getTodo(todoId)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(top = 60.dp)
    ) { padding ->
        if (todo == null) {
            CircularProgressIndicator()
            return@Scaffold
        }
        Text(text = todo!!.title, Modifier.padding(padding))
    }
}

sealed class Page {
    data object ListScreen : Page()
    data class DetailsScreen(val todo: Int) : Page()
}


@Preview
@Composable
fun PreviewTodoListScreen() = MaterialTheme {
    TodoListScreen({})
}