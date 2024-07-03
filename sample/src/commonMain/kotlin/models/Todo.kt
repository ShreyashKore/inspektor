package models

import kotlinx.serialization.Serializable

@Serializable
data class Todo(
    val completed: Boolean,
    val id: Int,
    val title: String,
    val userId: Int
)

@Serializable
data class Response(
    val id: Int,
    val content: String,
)