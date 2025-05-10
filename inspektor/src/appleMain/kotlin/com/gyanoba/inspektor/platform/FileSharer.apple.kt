package com.gyanoba.inspektor.platform

import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication

internal actual fun FileSharer(): FileSharer = FileSharerImpl()

internal class FileSharerImpl : FileSharer {
    override fun shareFile(filePath: String, mimeType: String) {
        val fileUrl = NSURL.fileURLWithPath(filePath)
        val topController = UIApplication.sharedApplication.keyWindow?.rootViewController?.presentedViewController
            ?: throw IllegalStateException("No key window or root view controller found")

        if (fileUrl.path != null && !NSFileManager.defaultManager()
                .fileExistsAtPath(fileUrl.path!!)
        ) {
            print("Error: File not found at path $filePath")
            val alert = UIAlertController.alertControllerWithTitle(
                "Error",
                "File not found.",
                UIAlertControllerStyleAlert
            )
            alert.addAction(UIAlertAction.actionWithTitle("OK", UIAlertActionStyleDefault, null))
            topController.presentViewController(alert, true, null)
            return
        }

        val activityViewController = UIActivityViewController(listOf(fileUrl), null)

        // Configure for iPad to avoid a crash
//            val popoverController = activityViewController.popoverPresentationController().apply {
//                popoverController.sourceView = topController.view
//                popoverController.sourceRect = CGRect(x: presentingViewController.view.bounds.midX, y: presentingViewController.view.bounds.midY, width: 0, height: 0)
//                popoverController.permittedArrowDirections = [] // Set to empty to avoid arrow on iPad
//            }
    //            topController.present(activityViewController, animated: true, completion: nil)
    //    }

        topController.presentViewController(activityViewController, animated = true, completion = null)
    }
}