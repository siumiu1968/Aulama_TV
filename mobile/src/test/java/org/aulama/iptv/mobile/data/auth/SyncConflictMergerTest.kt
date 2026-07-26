package org.aulama.iptv.mobile.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncConflictMergerTest {
    @Test
    fun mergePreservesRemoteDataAndKeepsLocalManualPriorityFirst() {
        val local = SyncPayload(
            favorites = listOf("local"),
            customSources = listOf(
                AulamaCustomSource("same", "Local", "https://local.example/list.m3u", "10"),
                AulamaCustomSource("local", "Local only", "https://local.example/only.m3u"),
            ),
            routePriorities = mapOf("jade" to listOf("local-route", "shared")),
        )
        val remote = SyncPayload(
            favorites = listOf("remote"),
            customSources = listOf(
                AulamaCustomSource("same", "Remote", "https://remote.example/list.m3u", "20"),
            ),
            routePriorities = mapOf("jade" to listOf("remote-route", "shared")),
        )

        val merged = SyncConflictMerger.merge(local, remote)

        assertEquals(listOf("remote", "local"), merged.favorites)
        assertEquals("https://remote.example/list.m3u", merged.customSources.first { it.id == "same" }.url)
        assertEquals(2, merged.customSources.size)
        assertEquals(
            listOf("local-route", "shared", "remote-route"),
            merged.routePriorities.getValue("jade"),
        )
    }

    @Test
    fun codecRoundTripsSyncDocument() {
        val payload = SyncPayload(
            favorites = listOf("jade"),
            customSources = listOf(
                AulamaCustomSource("home", "Home", "https://example.com/list.m3u", "123"),
            ),
            routePriorities = mapOf("jade" to listOf("https://example.com/live.m3u8")),
        )
        val encoded = "{\"revision\":7,\"sync\":${SyncJsonCodec.toJson(payload)}}"
        assertEquals(SyncDocument(7, payload), SyncJsonCodec.parseDocument(encoded))
    }
}
