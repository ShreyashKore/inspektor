package com.gyanoba.inspektor.data.adapters

import app.cash.sqldelight.ColumnAdapter
import kotlinx.serialization.json.Json

internal val setMapEntryAdapter =
    object : ColumnAdapter<Set<Map.Entry<String, List<String>>>, String> {
        override fun decode(databaseValue: String): Set<Map.Entry<String, List<String>>> {
            return Json.decodeFromString<Set<Map.Entry<String, List<String>>>>(databaseValue)
        }

        override fun encode(value: Set<Map.Entry<String, List<String>>>): String {
            return Json.encodeToString(value)
        }
    }