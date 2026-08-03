package com.example.ether.data.repository

import android.content.Context
import android.net.Uri
import com.example.ether.data.local.EtherDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: EtherDatabase
) {
    private val databaseName = "ether_database"

    suspend fun createBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Checkpoint the database to ensure all data is in the main .db file
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }

            val dbFile = context.getDatabasePath(databaseName)
            val dataStoreDir = File(context.filesDir, "datastore")
            val dataStoreFile = File(dataStoreDir, "settings.preferences_pb")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zipOut ->
                    // Add Database
                    if (dbFile.exists()) {
                        addToZip(zipOut, dbFile, "database.db")
                    }
                    
                    // Add DataStore
                    if (dataStoreFile.exists()) {
                        addToZip(zipOut, dataStoreFile, "settings.preferences_pb")
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun addToZip(zipOut: ZipOutputStream, file: File, zipEntryName: String) {
        FileInputStream(file).use { fis ->
            val zipEntry = ZipEntry(zipEntryName)
            zipOut.putNextEntry(zipEntry)
            fis.copyTo(zipOut)
            zipOut.closeEntry()
        }
    }

    suspend fun restoreBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val entryName = entry.name
                        when {
                            entryName == "database.db" -> {
                                val dbFile = context.getDatabasePath(databaseName)
                                val tempDbFile = File(dbFile.parentFile, "${databaseName}.tmp")
                                
                                // Close database before overwriting
                                database.close()
                                
                                FileOutputStream(tempDbFile).use { fos ->
                                    zipIn.copyTo(fos)
                                }
                                
                                if (dbFile.exists()) dbFile.delete()
                                if (!tempDbFile.renameTo(dbFile)) {
                                    throw java.io.IOException("Failed to rename temporary database file")
                                }
                                
                                // Also delete WAL/SHM files if they exist to prevent corruption
                                File(dbFile.path + "-wal").delete()
                                File(dbFile.path + "-shm").delete()
                            }
                            entryName == "settings.preferences_pb" -> {
                                val dataStoreDir = File(context.filesDir, "datastore")
                                if (!dataStoreDir.exists()) dataStoreDir.mkdirs()
                                val dataStoreFile = File(dataStoreDir, "settings.preferences_pb")
                                val tempDataStoreFile = File(dataStoreDir, "settings.preferences_pb.tmp")
                                
                                // Zip Slip Validation
                                if (!dataStoreFile.canonicalPath.startsWith(dataStoreDir.canonicalPath)) {
                                    throw SecurityException("Potential Zip Slip attack detected: $entryName")
                                }
                                
                                FileOutputStream(tempDataStoreFile).use { fos ->
                                    zipIn.copyTo(fos)
                                }
                                
                                if (dataStoreFile.exists()) dataStoreFile.delete()
                                if (!tempDataStoreFile.renameTo(dataStoreFile)) {
                                    throw java.io.IOException("Failed to rename temporary DataStore file")
                                }
                            }
                            // Validate entry name to prevent path traversal for any other potential entries
                            else -> {
                                if (entryName.contains("..")) {
                                    throw SecurityException("Potential Zip Slip attack detected in entry: $entryName")
                                }
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
