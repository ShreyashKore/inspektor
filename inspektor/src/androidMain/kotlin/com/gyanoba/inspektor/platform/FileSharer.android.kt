package com.gyanoba.inspektor.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.gyanoba.inspektor.utils.ContextInitializer.Companion.appContext
import java.io.File

internal actual fun FileSharer(): FileSharer = FileSharerImpl(appContext)

internal class FileSharerImpl(private val context: Context) : FileSharer {
    override fun shareFile(filePath: String, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.inspektor.file_provider",
            File(filePath)
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share file"))
    }
}