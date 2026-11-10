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
        TranslationEntity::class,
        ReadingGoalEntity::class,
        DefinitionEntity::class,
        BookTextEntity::class,
        BookTagEntity::class
    ],
    version = 22,
    exportSchema = true,
    // v2: books.last_page (reading position, Phase 1 milestone 4).
    // v3: books.author, shelves and shelf_books (milestone 5, slice 3).
    // v4: bookmarks (ribbons with a colour; slice 4).
    // v5: editable book details and type, reading_sessions (slice 6).
    // v6: annotations (highlights and notes) and books.right_to_left.
    // v7: books.page_labels (printed page numbers).
    // v8: translations (the reader's translation history).
    // v9: books.format (PDF or EPUB, Phase 2).
    // v10: books.last_locator (EPUB reading position).
    // v11: shelves.color (a shelf's colour).
    // v12: books.folder (folder imports) and more looked-up details (subjects, print pages, series, rating).
    // v13: shelf_books.position (books reordered on a shelf by drag and drop).
    // v14: annotations.locator and bookmarks.locator (EPUB highlights and ribbons by locator, ADR-0040).
    // v15: books.flow_mode and books.flow_locator (Dynamic Flow, ADR-0045).
    // v16: books.favorite and books.finished_at (favourites and finished books, M3T2).
    // v17: reading_goals and definitions (Reading Goal and offline Dictionary, M3T3).
    // v21: book_text (a book's own text settings, ADR-0052).
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        // v22: book_tags, a book's own tags (owner, 2026-11-10).
        AutoMigration(from = 21, to = 22)
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

    abstract fun readingGoalDao(): ReadingGoalDao

    abstract fun definitionDao(): DefinitionDao

    abstract fun bookTextDao(): BookTextDao

    abstract fun bookTagDao(): BookTagDao
}

@Suppress("TooManyFunctions") // one provider per DAO: it grows with the tables
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

    @Provides
    fun readingGoalDao(db: FlowFolioDatabase): ReadingGoalDao = db.readingGoalDao()

    @Provides
    fun definitionDao(db: FlowFolioDatabase): DefinitionDao = db.definitionDao()

    @Provides
    fun bookTextDao(db: FlowFolioDatabase): BookTextDao = db.bookTextDao()

    @Provides
    fun bookTagDao(db: FlowFolioDatabase): BookTagDao = db.bookTagDao()
}
