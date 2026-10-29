package app.duoodo.nostr

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** In-memory relay with NIP-01 replaceable-event, NIP-09 deletion and signature-check semantics. */
class FakeRelays : Relays {
    val stored = mutableListOf<NostrEvent>()
    var online = true

    override suspend fun publish(event: NostrEvent): Int {
        if (!online || !event.isValid()) return 0
        when (event.kind) {
            NostrEvent.KIND_DELETION -> event.tags.filter { it[0] == "a" }.forEach { a ->
                val (kind, pub, d) = a[1].split(":", limit = 3)
                if (pub == event.pubkey) stored.removeAll { it.kind == kind.toInt() && it.pubkey == pub && it.tag("d") == d }
            }
            in 30000..39999 -> {
                stored.removeAll { it.kind == event.kind && it.pubkey == event.pubkey && it.tag("d") == event.tag("d") }
                stored += event
            }
            else -> stored += event
        }
        return 1
    }

    override suspend fun query(filter: JsonObject): List<NostrEvent> {
        if (!online) return emptyList()
        val kinds = filter["kinds"]?.jsonArray?.map { it.jsonPrimitive.int }
        val authors = filter["authors"]?.jsonArray?.map { it.jsonPrimitive.content }
        val dTags = filter["#d"]?.jsonArray?.map { it.jsonPrimitive.content }
        return stored.filter { e ->
            (kinds == null || e.kind in kinds) && (authors == null || e.pubkey in authors) && (dTags == null || e.tag("d") in dTags)
        }
    }
}
