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
        ReadingSessionEntity::class,
        AnnotationEntity::class,
        TranslationEntity::class
    ],
    version = 8,
    exportSchema = true,
    // v2: books.last_page (reading position, Phase 1 milestone 4).
    // v3: books.author, shelves and shelf_books (milestone 5, slice 3).
    // v4: bookmarks (ribbons with a colour; slice 4).
    // v5: editable book details and type, reading_sessions (slice 6).
    // v6: annotations (highlights and notes) and books.right_to_left.
    // v7: books.page_labels (printed page numbers).
    // v8: translations (the reader's translation history).
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8)
    ]
)
abstract class FlowFolioDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun shelfDao(): ShelfDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun readingSessionDao(): ReadingSessionDao

    abstract fun annotationDao(): AnnotationDao

    abstract fun bookReadingDao(): BookReadingDao

    abstract fun translationDao(): TranslationDao
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

    @Provides
    fun annotationDao(db: FlowFolioDatabase): AnnotationDao = db.annotationDao()

    @Provides
    fun bookReadingDao(db: FlowFolioDatabase): BookReadingDao = db.bookReadingDao()

    @Provides
    fun translationDao(db: FlowFolioDatabase): TranslationDao = db.translationDao()
}
