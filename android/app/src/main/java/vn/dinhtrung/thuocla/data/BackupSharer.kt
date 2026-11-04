package vn.dinhtrung.thuocla.data

import java.io.File

/**
 * Writes the share copy of the JSON backup into [directory] (the app's `cacheDir/backups/`, exposed by
 * `FileProvider` through `res/xml/file_paths.xml`).
 *
 * Why a cache directory rather than `Downloads`: writing to shared storage needs a permission on
 * API 26–28 and `MediaStore` on 29+, both more machinery than this needs. A cache file leaves the app
 * only through `content://` + a temporary read grant, needs no permission at all, and the OS may clear
 * it when space runs low (design decision D3, spec requirement "File bản sao để chia sẻ").
 *
 * The copy is written fresh on every share: [writeShareCopy] first deletes whatever the previous share
 * left in [directory], so the directory holds at most the newest file and cannot accumulate one per
 * day (spec requirement "Dọn file tạm của lần chia sẻ trước").
 *
 * [writeShareCopy] returns null — and writes nothing — when the store is empty, so the screen can show
 * the same "nothing to export" message the SAF export path shows instead of sharing an empty file.
 */
class BackupSharer(
    private val portability: DataPortability,
    /** Directory the share copies live in; created on first write. */
    private val directory: File,
) {

    /** The file just written, plus the display name to hand the receiving app. */
    data class ShareCopy(val file: File, val fileName: String)

    /**
     * Writes the JSON backup into [directory] and returns the copy, or null when there is nothing to
     * share. Blocking IO: call from a background dispatcher.
     */
    suspend fun writeShareCopy(): ShareCopy? {
        // Ask the store first: an empty store must not leave an empty file behind.
        val json = portability.exportJson() ?: return null

        clearPreviousCopies()
        if (!directory.isDirectory && !directory.mkdirs()) return null

        // Same name the SAF export uses, so a receiver's file matches the PWA's backup convention.
        val fileName = portability.jsonFileName()
        val file = File(directory, fileName)
        file.writeText(json, Charsets.UTF_8)
        return ShareCopy(file = file, fileName = fileName)
    }

    /** Removes share copies left by an earlier share; ignores a directory it cannot read. */
    fun clearPreviousCopies() {
        directory.listFiles()?.forEach { leftover -> leftover.delete() }
    }

    companion object {
        /** Sub-directory of `cacheDir` declared as the provider's only root in `res/xml/file_paths.xml`. */
        const val DIRECTORY_NAME = "backups"
    }
}
