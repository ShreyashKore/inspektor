package models

import kotlinx.serialization.Serializable

@Serializable
data class Todo(
    val completed: Boolean,
    val id: Int,
    val title: String,
    val userId: Int
)