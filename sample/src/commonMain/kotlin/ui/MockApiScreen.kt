package com.gyanoba.inspektor.sample.ui


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gyanoba.inspektor.sample.data.MockApi
import openInspektor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockApiScreen(
    openTodoListScreen: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Mock API") }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { openInspektor() }) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Text("Open Inspektor")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "You can trigger Mock API calls from here." +
                        " Open Inspektor to see details of the network calls.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            MethodTypeButtons()
            ResponseTypeButtons()
            StatusTypeButtons()
            ButtonGroupLayout("Json Placeholer") {
                Button(onClick = openTodoListScreen) { Text("Todo List Screen") }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
fun MethodTypeButtons() = ButtonGroupLayout("Method Type") {
    Button(onClick = { MockApi.getTodo() }) { Text("GET Todo") }
    Button(onClick = { MockApi.createTodo() }) { Text("POST Todo") }
    Button(onClick = { MockApi.updateTodo() }) { Text("PUT Todo") }
    Button(onClick = { MockApi.deleteTodo() }) { Text("DELETE Todo") }
    Button(onClick = { MockApi.patchTodo() }) { Text("PATCH Todo") }
}

@Composable
fun ResponseTypeButtons() = ButtonGroupLayout("Response Type") {
    Button(onClick = { MockApi.getJsonResponse() }) { Text("JSON") }
    Button(onClick = { MockApi.getTextResponse() }) { Text("Text") }
    Button(onClick = { MockApi.getHtmlResponse() }) { Text("HTML") }
    Button(onClick = { MockApi.getXmlResponse() }) { Text("XML") }
    Button(onClick = { MockApi.getBinaryResponse() }) { Text("Binary") }
}

@Composable
fun StatusTypeButtons() = ButtonGroupLayout("Status Type") {
    Button(onClick = { MockApi.getSuccessResponse200() }) { Text("200 Success") }
    Button(onClick = { MockApi.getErrorResponse400() }) { Text("400 Error") }
    Button(onClick = { MockApi.getErrorResponse404() }) { Text("404 Error") }
    Button(onClick = { MockApi.getErrorResponse500() }) { Text("500 Error") }
    Button(onClick = { MockApi.getNoResponse() }) { Text("No Response") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonGroupLayout(
    heading: String,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(heading, style = MaterialTheme.typography.titleMedium)
        FlowRow(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}