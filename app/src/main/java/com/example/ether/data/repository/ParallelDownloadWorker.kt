package com.example.ether.data.repository

import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ParallelDownloadWorker(
    private val url: String,
    private val destination: File,
    private val numChunks: Int = 3
) {
    private var executor: ExecutorService? = null

    fun startDownload(onProgress: (Int) -> Unit, onComplete: () -> Unit, onError: (Exception) -> Unit) {
        val currentExecutor = Executors.newFixedThreadPool(numChunks)
        executor = currentExecutor
        
        Thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 10000
                val fileSize = connection.contentLengthLong
                connection.disconnect()

                if (fileSize <= 0) {
                    onError(Exception("Invalid file size: $fileSize"))
                    return@Thread
                }

                RandomAccessFile(destination, "rw").use { raf ->
                    raf.setLength(fileSize)
                }

                val chunkSize = fileSize / numChunks
                val completedChunks = AtomicInteger(0)
                val totalBytesDownloaded = AtomicLong(0)
                val hasFailed = java.util.concurrent.atomic.AtomicBoolean(false)

                for (i in 0 until numChunks) {
                    val start = i * chunkSize
                    val end = if (i == numChunks - 1) fileSize - 1 else (i + 1) * chunkSize - 1

                    currentExecutor.execute {
                        if (hasFailed.get()) return@execute
                        try {
                            val chunkConn = URL(url).openConnection() as HttpURLConnection
                            chunkConn.setRequestProperty("Range", "bytes=$start-$end")
                            chunkConn.connectTimeout = 10000
                            
                            chunkConn.inputStream.use { input ->
                                RandomAccessFile(destination, "rw").use { output ->
                                    output.seek(start)
                                    val buffer = ByteArray(16384) // Larger buffer for better performance
                                    var bytesRead: Int
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        if (hasFailed.get()) break
                                        output.write(buffer, 0, bytesRead)
                                        val total = totalBytesDownloaded.addAndGet(bytesRead.toLong())
                                        onProgress(((total * 100) / fileSize).toInt())
                                    }
                                }
                            }
                            
                            if (!hasFailed.get() && completedChunks.incrementAndGet() == numChunks) {
                                currentExecutor.shutdown()
                                onComplete()
                            }
                        } catch (e: Exception) {
                            if (hasFailed.compareAndSet(false, true)) {
                                currentExecutor.shutdownNow()
                                onError(e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                onError(e)
            }
        }.start()
    }

    fun cancel() {
        executor?.shutdownNow()
        executor = null
    }
}
