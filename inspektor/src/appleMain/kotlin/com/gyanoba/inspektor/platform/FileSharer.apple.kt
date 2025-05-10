package com.gyanoba.inspektor.platform

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

internal actual fun FileSharer(): FileSharer = FileSharerImpl()

internal class FileSharerImpl : FileSharer {
    override fun shareFile(filePath: String, mimeType: String) {
        val fileUrl = NSURL.fileURLWithPath(filePath)
        val activityVC = UIActivityViewController(listOf(fileUrl), null)

        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootVC?.presentViewController(activityVC, animated = true, completion = null)
    }
}