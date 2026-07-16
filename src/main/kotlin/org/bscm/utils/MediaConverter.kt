package org.bscm.utils

import io.ktor.util.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.concurrent.TimeUnit

private val log = KtorSimpleLogger("MediaConverter")

object MediaConverter {

    suspend fun convertToOpus(inputBytes: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        val tmpIn = Files.createTempFile("preview-in", ".tmp")
        val tmpOut = Files.createTempFile("preview-out", ".opus")
        try {
           (tmpIn).toFile().writeBytes(inputBytes)

            val process = ProcessBuilder(
                "ffmpeg", "-y",
                "-i", tmpIn.toAbsolutePath().toString(),
                "-c:a", "libopus",
                "-b:a", "64k",
                "-vn",
                "-ac", "2",
                "-ar", "48000",
                "-application", "audio",
                "-cutoff", "18000",
                tmpOut.toAbsolutePath().toString()
            )
                .redirectErrorStream(true)
                .start()

            val exited = process.waitFor(60, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                val output = process.inputStream.bufferedReader().readText()
                log.error("ffmpeg opus conversion timed out: $output")
                return@withContext null
            }
            if (process.exitValue() != 0) {
                val output = process.inputStream.bufferedReader().readText()
                log.error("ffmpeg opus conversion failed (exit=${process.exitValue()}): $output")
                return@withContext null
            }

            tmpOut.toFile().readBytes()
        } catch (e: Exception) {
            log.error("ffmpeg opus conversion error", e)
            null
        } finally {
            tmpIn.toFile().delete()
            tmpOut.toFile().delete()
        }
    }

    suspend fun convertToAvif(inputBytes: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        val tmpIn = Files.createTempFile("cover-in", ".png")
        val tmpOut = Files.createTempFile("cover-out", ".avif")
        try {
            tmpIn.toFile().writeBytes(inputBytes)

            val process = ProcessBuilder(
                "ffmpeg", "-y",
                "-i", tmpIn.toAbsolutePath().toString(),
                "-c:v", "libaom-av1",
                "-crf", "40",
                "-b:v", "0",
                "-strict", "experimental",
                "-pix_fmt", "yuv420p",
                "-still-picture", "1",
                "-row-mt", "1",
                tmpOut.toAbsolutePath().toString()
            )
                .redirectErrorStream(true)
                .start()

            val exited = process.waitFor(60, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                val output = process.inputStream.bufferedReader().readText()
                log.error("ffmpeg avif conversion timed out: $output")
                return@withContext null
            }
            if (process.exitValue() != 0) {
                val output = process.inputStream.bufferedReader().readText()
                log.error("ffmpeg avif conversion failed (exit=${process.exitValue()}): $output")
                return@withContext null
            }

            tmpOut.toFile().readBytes()
        } catch (e: Exception) {
            log.error("ffmpeg avif conversion error", e)
            null
        } finally {
            tmpIn.toFile().delete()
            tmpOut.toFile().delete()
        }
    }
}
