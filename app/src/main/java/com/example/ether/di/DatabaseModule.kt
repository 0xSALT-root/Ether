package com.example.ether.di

import android.content.Context
import androidx.room.Room
import com.example.ether.data.local.DownloadDao
import com.example.ether.data.local.HistoryDao
import com.example.ether.data.local.EtherDatabase
import com.example.ether.data.local.VpnDao
import com.example.ether.data.local.WebsiteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EtherDatabase {
        return Room.databaseBuilder(
            context,
            EtherDatabase::class.java,
            "ether_database"
        )
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideWebsiteDao(database: EtherDatabase): WebsiteDao {
        return database.websiteDao()
    }

    @Provides
    fun provideDownloadDao(database: EtherDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    fun provideVpnDao(database: EtherDatabase): VpnDao {
        return database.vpnDao()
    }

    @Provides
    fun provideHistoryDao(database: EtherDatabase): HistoryDao {
        return database.historyDao()
    }
}
