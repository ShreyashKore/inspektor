import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import data.Api
import models.Todo
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
internal fun App() = MaterialTheme {
    TodoListScreen()
}

@Composable
fun TodoListScreen() {
    var todos by remember { mutableStateOf(emptyList<Todo>()) }
    var todo by remember { mutableStateOf<Todo?>(null) }
    LaunchedEffect(Unit) {
        todos = Api.getTodos()
//        todo = Api.getTodo("200")
    }
    Scaffold(
        contentWindowInsets = WindowInsets(top = 60.dp)
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(todos) { todo ->
                TodoCard(todo)
            }
        }
    }
}


@Composable
fun TodoCard(todo: Todo) {
    Card(
        Modifier
            .heightIn(min = 80.dp)
            .padding(4.dp)
    ) {
        Row(
            Modifier
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
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
fun PreviewTodoListScreen() = MaterialTheme {
    TodoListScreen()
}