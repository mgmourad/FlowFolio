package com.flowfolio.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject

/** A book file found in a picked folder: its document [uri], and its [folder] path, the picked folder's name first. */
data class FolderFile(val uri: String, val folder: String)

/** Access to user-chosen documents (Storage Access Framework). Abstracted so import logic is testable. */
interface DocumentSource {
    /** Opens [uri] read-only, or returns null when it cannot be opened (missing, moved, access revoked). */
    fun open(uri: String): ParcelFileDescriptor?

    /** The name the user sees for [uri], when the provider reports one. */
    fun displayName(uri: String): String?

    /** File size in bytes when the provider reports it. */
    fun size(uri: String): Long?

    /**
     * Keeps read access to [uri] across restarts. Returns false when the provider does not allow it, which is the
     * case for files shared from other apps (share sheet, "Open with"): those can only be copied.
     */
    fun persistReadAccess(uri: String): Boolean

    fun releaseReadAccess(uri: String)

    /**
     * Every PDF and EPUB anywhere under the folder [treeUri] (from the system's folder picker), each with the folder it
     * is in ("Books/Physics"). Hidden folders are skipped. Null when the folder cannot be read.
     */
    fun listFolder(treeUri: String): List<FolderFile>?

    /** Keeps read access to the folder [treeUri] and everything under it across restarts; false when not allowed. */
    fun persistFolderAccess(treeUri: String): Boolean

    /** The folders FlowFolio keeps access to (picked for reading in place, or kept for syncing). */
    fun heldFolders(): List<String> = emptyList()
}

class AndroidDocumentSource @Inject constructor(@ApplicationContext private val context: Context) : DocumentSource {
    private val resolver get() = context.contentResolver

    override fun open(uri: String): ParcelFileDescriptor? = try {
        resolver.openFileDescriptor(Uri.parse(uri), "r")
    } catch (_: FileNotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    override fun displayName(uri: String): String? = try {
        resolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: Uri.parse(uri).lastPathSegment
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    override fun size(uri: String): Long? = try {
        resolver.query(Uri.parse(uri), arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    override fun persistReadAccess(uri: String): Boolean = try {
        resolver.takePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: SecurityException) {
        false
    }

    override fun releaseReadAccess(uri: String) {
        try {
            resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Already released or never held (a file inside a folder the app holds): nothing to do.
        }
    }

    override fun listFolder(treeUri: String): List<FolderFile>? = try {
        walk(Uri.parse(treeUri))
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun walk(tree: Uri): List<FolderFile> {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootName = displayName(DocumentsContract.buildDocumentUriUsingTree(tree, rootId).toString())
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_FOLDER
        val found = ArrayList<FolderFile>()
        // Breadth first, so a very deep or looping provider stops after MAX_FOLDERS folders rather than never.
        val todo = ArrayDeque(listOf(rootId to rootName))
        var visited = 0
        while (todo.isNotEmpty() && visited++ < MAX_FOLDERS) {
            val (id, path) = todo.removeFirst()
            for (child in children(tree, id)) {
                when {
                    child.name.startsWith(".") -> Unit

                    child.mime == Document.MIME_TYPE_DIR -> todo += child.id to "$path/${child.name}"

                    isBook(child.name, child.mime) -> found += FolderFile(
                        DocumentsContract.buildDocumentUriUsingTree(tree, child.id).toString(),
                        path
                    )
                }
            }
        }
        return found
    }

    override fun persistFolderAccess(treeUri: String): Boolean = persistReadAccess(treeUri)

    override fun heldFolders(): List<String> = resolver.persistedUriPermissions
        .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
        .map { it.uri.toString() }

    private class Child(val id: String, val name: String, val mime: String?)

    /** What is directly inside the folder [id] of [tree]. */
    private fun children(tree: Uri, id: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        return resolver.query(uri, CHILD_COLUMNS, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val childId = c.getString(0)
                    val name = c.getString(1)
                    if (childId != null && name != null) add(Child(childId, name, c.getString(2)))
                }
            }
        }.orEmpty()
    }

    private fun isBook(name: String, mime: String?): Boolean =
        mime in BOOK_TYPES || BOOK_EXTENSION.containsMatchIn(name)

    private companion object {
        val CHILD_COLUMNS =
            arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val BOOK_TYPES = setOf("application/pdf", "application/epub+zip")
        val BOOK_EXTENSION = Regex("\\.(pdf|epub)$", RegexOption.IGNORE_CASE)
        const val DEFAULT_FOLDER = "Folder"
        const val MAX_FOLDERS = 5_000
    }
}
