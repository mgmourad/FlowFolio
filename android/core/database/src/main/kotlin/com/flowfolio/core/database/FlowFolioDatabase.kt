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
    entities = [BookEntity::class, ShelfEntity::class, ShelfBookEntity::class],
    version = 3,
    exportSchema = true,
    // v2: books.last_page (reading position, Phase 1 milestone 4).
    // v3: books.author, shelves and shelf_books (milestone 5, slice 3).
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)]
)
abstract class FlowFolioDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    abstract fun shelfDao(): ShelfDao
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
}
