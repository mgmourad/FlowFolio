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
    @ColumnInfo(name = "created_at") val createdAt: Long
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
    @ColumnInfo(name = "added_at") val addedAt: Long
)
