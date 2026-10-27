package com.flowfolio.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A shelf the reader made. Added in schema v3. */
@Entity(tableName = "shelves")
data class ShelfEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** A [com.flowfolio.core.model.ShelfColor] name, or null for the theme's colour. Added in schema v11. */
    @ColumnInfo(name = "color") val color: String? = null
)

/** A book on a shelf. Removing the shelf or the book removes the row. Added in schema v3. */
@Entity(
    tableName = "shelf_books",
    primaryKeys = ["shelf_id", "book_id"],
    foreignKeys = [
        ForeignKey(ShelfEntity::class, ["id"], ["shelf_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(BookEntity::class, ["id"], ["book_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("book_id")]
)
data class ShelfBookEntity(
    @ColumnInfo(name = "shelf_id") val shelfId: Long,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    /**
     * Where the book stands on the shelf, smallest first; ties (every row before schema v13) fall back to newest first.
     * Added in schema v13, for drag-and-drop reordering.
     */
    @ColumnInfo(defaultValue = "0") val position: Int = 0
)
