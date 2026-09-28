package io.github.assworkbench.domain

import java.security.MessageDigest

object ReviewEventKey {
    fun keys(events: List<AssEvent>): Map<Long, String> {
        val occurrences = mutableMapOf<String, Int>()
        val result = linkedMapOf<Long, String>()
        events.forEach { event ->
            val base = key(event)
            val occurrence = occurrences.getOrDefault(base, 0)
            occurrences[base] = occurrence + 1
            result[event.id] = if (occurrence == 0) base else "$base:$occurrence"
        }
        return result
    }

    fun key(event: AssEvent): String = "e2:" + sha256(canonical(event))

    private fun canonical(event: AssEvent): String = buildString {
        fun field(value: String) {
            append(value.length).append(':').append(value)
        }
        field("ASSWB_EVENT_KEY_V2")
        field(event.layer.toString())
        field(event.start.millis.toString())
        field(event.end.millis.toString())
        field(event.style)
        field(event.name)
        field(event.marginL.toString())
        field(event.marginR.toString())
        field(event.marginV.toString())
        field(event.effect)
        field(if (event.comment) "1" else "0")
        event.extraFields.toSortedMap().forEach { (name, value) ->
            field(name)
            field(value)
        }
        field(event.text)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
