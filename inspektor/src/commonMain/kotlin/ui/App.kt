package ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gyanoba.inspektor.data.entites.HttpTransaction

@Composable
internal fun App() {
    var selectedTransaction by remember { mutableStateOf<HttpTransaction?>(null) }

    AnimatedContent(
        targetState = selectedTransaction,
        modifier = Modifier.fillMaxSize()
    ) { transaction ->
        when (transaction) {
            null -> {
                TransactionListScreen(
                    openTransaction = {
                        selectedTransaction = it
                    },
                )
            }

            else -> {
                TransactionDetailsScreen(
                    transaction.id,
                    onBack = {
                        selectedTransaction = null
                    },
                )
            }
        }
    }
}