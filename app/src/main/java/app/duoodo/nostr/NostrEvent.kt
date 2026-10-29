package app.duoodo.nostr

import app.duoodo.crypto.Crypto
import app.duoodo.crypto.Schnorr
import app.duoodo.crypto.hexToBytes
import app.duoodo.crypto.toHex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.int

/** A NIP-01 event. */
class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    fun tag(name: String): String? = tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)

    /** Checks that [id] matches the content and [sig] is a valid signature by [pubkey]. */
    fun isValid(): Boolean = try {
        computeId(pubkey, createdAt, kind, tags, content).toHex() == id &&
            Schnorr.verify(pubkey.hexToBytes(), id.hexToBytes(), sig.hexToBytes())
    } catch (e: IllegalArgumentException) {
        false
    }

    fun toJson(): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "pubkey" to JsonPrimitive(pubkey),
            "created_at" to JsonPrimitive(createdAt),
            "kind" to JsonPrimitive(kind),
            "tags" to tagsJson(tags),
            "content" to JsonPrimitive(content),
            "sig" to JsonPrimitive(sig),
        )
    )

    companion object {
        /** NIP-78 application-specific data; parameterized-replaceable, so a newer event replaces the older one. */
        const val KIND_APP_DATA = 30078
        const val KIND_DELETION = 5

        fun sign(privateKey: ByteArray, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): NostrEvent {
            val pubkey = Schnorr.publicKey(privateKey).toHex()
            val id = computeId(pubkey, createdAt, kind, tags, content)
            val sig = Schnorr.sign(privateKey, id)
            return NostrEvent(id.toHex(), pubkey, createdAt, kind, tags, content, sig.toHex())
        }

        fun computeId(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): ByteArray {
            val serialized = JsonArray(
                listOf(JsonPrimitive(0), JsonPrimitive(pubkey), JsonPrimitive(createdAt), JsonPrimitive(kind), tagsJson(tags), JsonPrimitive(content))
            ).toString()
            return Crypto.sha256(serialized.toByteArray())
        }

        /** Returns null for anything that isn't a well-formed event. */
        fun fromJson(json: JsonObject): NostrEvent? = try {
            NostrEvent(
                id = json["id"]!!.jsonPrimitive.content,
                pubkey = json["pubkey"]!!.jsonPrimitive.content,
                createdAt = json["created_at"]!!.jsonPrimitive.long,
                kind = json["kind"]!!.jsonPrimitive.int,
                tags = json["tags"]!!.jsonArray.map { t -> t.jsonArray.map { it.jsonPrimitive.content } },
                content = json["content"]!!.jsonPrimitive.content,
                sig = json["sig"]!!.jsonPrimitive.content,
            )
        } catch (e: Exception) {
            null
        }

        private fun tagsJson(tags: List<List<String>>) = JsonArray(tags.map { t -> JsonArray(t.map(::JsonPrimitive)) })
    }
}
