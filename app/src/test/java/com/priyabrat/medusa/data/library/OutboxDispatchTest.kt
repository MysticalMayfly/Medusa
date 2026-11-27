package com.priyabrat.medusa.data.library

import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxDispatchTest {
    private data class Event(val profile: String, val number: Int)
    @Test fun sendsEachProfileWithItsOwnToken() {
        val input = listOf(Event("a",1),Event("b",2),Event("a",3))
        val sent = mutableListOf<Pair<String,List<Int>>>()
        val remaining = OutboxDispatch.dispatch(input, { it.profile }, { "token-$it" }) { events, token ->
            sent.add(token to events.map { it.number }); true
        }
        assertEquals(emptyList<Event>(), remaining)
        assertEquals(listOf("token-a" to listOf(1,3), "token-b" to listOf(2)), sent)
    }
    @Test fun missingOrFailedAccountIsRetainedForRetry() {
        val input = listOf(Event("a",1),Event("b",2),Event("c",3))
        val remaining = OutboxDispatch.dispatch(input, { it.profile }, { if (it == "b") null else it }) { _, token -> token == "c" }
        assertEquals(input.take(2), remaining)
    }
}
