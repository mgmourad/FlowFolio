package com.flowfolio.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    /** SHA-256 of the file bytes, lowercase hex. */
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "page_count") val pageCount: Int,
    /** "COPY" or "LINK" (StorageMode name). */
    @ColumnInfo(name = "storage_mode") val storageMode: String,
    /** Absolute file path for COPY, content URI for LINK. */
    val location: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
    @ColumnInfo(name = "cover_path") val coverPath: String?,
    @ColumnInfo(name = "password_protected") val passwordProtected: Boolean,
    /** The page the reader last showed (0-based); null until the book is first read. Added in schema v2. */
    @ColumnInfo(name = "last_page") val lastPage: Int? = null
)
