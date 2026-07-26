package org.aulama.iptv.mobile.data.auth

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

internal interface LocalSyncStore {
    fun load(): SyncDocument
    fun save(document: SyncDocument)
}

internal class FileLocalSyncStore(context: Context) : LocalSyncStore {
    private val file = File(context.noBackupFilesDir, "aulama_mobile_sync.json")

    override fun load(): SyncDocument {
        if (!file.isFile || file.length() !in 1..MAX_FILE_BYTES) return EMPTY_DOCUMENT
        return SyncJsonCodec.parseDocument(file.readText()) ?: EMPTY_DOCUMENT
    }

    override fun save(document: SyncDocument) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(
            kotlinx.serialization.json.buildJsonObject {
                put("revision", kotlinx.serialization.json.JsonPrimitive(document.revision))
                put("sync", SyncJsonCodec.toJson(document.payload))
            }.toString()
        )
        if (!temporary.renameTo(file)) {
            temporary.delete()
            error("Unable to replace local sync file")
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 1024 * 1024L
        val EMPTY_DOCUMENT = SyncDocument(0L, SyncPayload())
    }
}

internal class AulamaSyncManager(
    private val accountManager: AulamaAccountManager,
    private val localStore: LocalSyncStore,
) {
    private val mutex = Mutex()

    fun localDocument(): SyncDocument = localStore.load()

    suspend fun reconcile(): SyncDocument = mutex.withLock {
        val local = localStore.load()
        val remote = accountManager.getSync()
        val merged = SyncConflictMerger.merge(local.payload, remote.payload)
        val resolved = if (merged == remote.payload) {
            remote
        } else {
            putWithConflictRetry(SyncDocument(remote.revision, merged))
        }
        localStore.save(resolved)
        resolved
    }

    suspend fun push(payload: SyncPayload): SyncDocument = mutex.withLock {
        val local = localStore.load()
        val candidate = SyncDocument(local.revision, payload)
        val resolved = putWithConflictRetry(candidate)
        localStore.save(resolved)
        resolved
    }

    fun saveLocal(payload: SyncPayload) {
        val current = localStore.load()
        localStore.save(current.copy(payload = payload))
    }

    private suspend fun putWithConflictRetry(candidate: SyncDocument): SyncDocument {
        return try {
            accountManager.putSync(candidate)
        } catch (_: AulamaApiException.Conflict) {
            val remote = accountManager.getSync()
            val merged = SyncConflictMerger.merge(candidate.payload, remote.payload)
            accountManager.putSync(SyncDocument(remote.revision, merged))
        }
    }
}
