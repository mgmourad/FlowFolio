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
    @ColumnInfo(name = "last_page") val lastPage: Int? = null,
    /** The PDF's Author metadata. Added in schema v3; null for books imported before. */
    val author: String? = null,
    // Editable details and the book's type, added in schema v5 (milestone 5, slice 6). All optional.
    val subtitle: String? = null,
    val edition: String? = null,
    val publisher: String? = null,
    val isbn: String? = null,
    val description: String? = null,
    val language: String? = null,
    val released: String? = null,
    @ColumnInfo(name = "author_role") val authorRole: String? = null,
    @ColumnInfo(name = "author_bio") val authorBio: String? = null,
    /** A BookType name; null or unknown means not set. */
    @ColumnInfo(name = "book_type") val bookType: String? = null,
    /** Pages run right to left. Added in schema v6. */
    @ColumnInfo(name = "right_to_left", defaultValue = "0") val rightToLeft: Boolean = false,
    /** Printed page numbers as PageLabels runs; null until the reader has looked. Added in schema v7. */
    @ColumnInfo(name = "page_labels") val pageLabels: String? = null,
    /** A BookFormat name (PDF or EPUB). Added in schema v9; earlier books are PDFs. */
    @ColumnInfo(defaultValue = "PDF") val format: String = "PDF",
    /** Where an EPUB was left: a reflow locator (chapter:offset), which survives re-pagination. Added in schema v10. */
    @ColumnInfo(name = "last_locator") val lastLocator: String? = null
)
