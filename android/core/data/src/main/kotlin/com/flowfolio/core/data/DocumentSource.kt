package com.flowfolio.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject

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
            // Already released or never held: nothing to do.
        }
    }
}
