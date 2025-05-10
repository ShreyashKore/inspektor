package com.gyanoba.inspektor.platform

internal expect fun FileSharer(): FileSharer

internal interface FileSharer {
    fun shareFile(filePath: String, mimeType: String)
}
