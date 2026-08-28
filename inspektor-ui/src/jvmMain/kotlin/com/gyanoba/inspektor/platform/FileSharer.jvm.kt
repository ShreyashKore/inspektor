package com.gyanoba.inspektor.platform

import java.awt.Desktop
import java.io.File

internal actual fun FileSharer(): FileSharer = FileSharerImpl()

internal class FileSharerImpl : FileSharer {
    override fun shareFile(filePath: String, mimeType: String) {
        val file = File(filePath)
        if (file.exists()) {
            Desktop.getDesktop().open(file)
        } else {
            println("File not found: $filePath")
        }
    }
}