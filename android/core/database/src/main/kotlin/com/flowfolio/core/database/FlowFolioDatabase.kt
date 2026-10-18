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
    entities = [
        BookEntity::class,
        ShelfEntity::class,
        ShelfBookEntity::class,
        BookmarkEntity::class,
        ReadingSessionEntity::class
    ],
    version = 5,
    exportSchema = true,
    // v2: books.last_page (reading position, Phase 1 milestone 4).
    // v3: books.author, shelves and shelf_books (milestone 5, slice 3).
    // v4: bookmarks (ribbons with a colour; slice 4).
    // v5: editable book details and type, reading_sessions (slice 6).
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5)
    ]
)
abstract class FlowFolioDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun shelfDao(): ShelfDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun readingSessionDao(): ReadingSessionDao
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

    @Provides
    fun readingSessionDao(db: FlowFolioDatabase): ReadingSessionDao = db.readingSessionDao()
}
