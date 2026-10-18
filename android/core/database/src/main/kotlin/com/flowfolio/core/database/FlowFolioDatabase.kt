package com.flowfolio.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [BookEntity::class, ShelfEntity::class, ShelfBookEntity::class, BookmarkEntity::class],
    version = 4,
    exportSchema = true,
    // v2: books.last_page (reading position, Phase 1 milestone 4).
    // v3: books.author, shelves and shelf_books (milestone 5, slice 3).
    // v4: bookmarks (ribbons with a colour; slice 4).
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4)
    ]
)
abstract class FlowFolioDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun shelfDao(): ShelfDao

    abstract fun bookmarkDao(): BookmarkDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FlowFolioDatabase =
        Room.databaseBuilder(context, FlowFolioDatabase::class.java, "flowfolio.db").build()

    @Provides
    fun bookDao(db: FlowFolioDatabase): BookDao = db.bookDao()

    @Provides
    fun shelfDao(db: FlowFolioDatabase): ShelfDao = db.shelfDao()

    @Provides
    fun bookmarkDao(db: FlowFolioDatabase): BookmarkDao = db.bookmarkDao()
}
