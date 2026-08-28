package utils

import io.github.xxfast.kstore.Codec
import kotlinx.serialization.*
import kotlinx.serialization.json.Json


/**
 * A codec that stores data in memory.
 */
inline fun <reified T : @Serializable Any> KStoreInMemoryCodec(
    json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
): Codec<T> = KStoreInMemoryCodec(json, json.serializersModule.serializer())

/**
 * A codec that stores data in memory.
 */
class KStoreInMemoryCodec<T : @Serializable Any>(
    private val json: Json,
    private val serializer: KSerializer<T>
) : Codec<T> {

    private var storedData: String? = null

    override suspend fun decode(): T? =
        storedData?.let {
            try {
                json.decodeFromString(serializer, it)
            } catch (e: SerializationException) {
                null
            }
        }

    override suspend fun encode(value: T?) {
        storedData = value?.let { json.encodeToString(serializer, it) }
    }
}
