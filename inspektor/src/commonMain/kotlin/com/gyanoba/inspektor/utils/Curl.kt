package com.gyanoba.inspektor.utils

import com.gyanoba.inspektor.data.HttpTransaction

internal fun HttpTransaction.toCurlString(): String {
    val curlCommand = StringBuilder("curl \\\n")
    curlCommand.append("  --request $method \\\n")
    url?.let {
        curlCommand.append("  --url '$it' \\\n")
    }
    requestHeaders?.forEach { (key, values) ->
        values.forEach { value ->
            curlCommand.append("  --header \"$key: $value\" \\\n")
        }
    }
    requestContentType?.let {
        curlCommand.append("  --header \"Content-Type: $it\" \\\n")
    }

    if (isRequestBodyEncoded != true && !requestBody.isNullOrEmpty()) {
        curlCommand.append("  --data-raw '${requestBody!!.replace("'", "\\'")}' \\\n")
    }

    return curlCommand.toString()
}
