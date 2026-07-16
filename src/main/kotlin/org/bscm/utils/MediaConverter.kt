package org.bscm.utils

import io.ktor.util.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import kotlin.time.Duration.Companion.milliseconds

private val log = KtorSimpleLogger("MediaConverter")

object MediaConverter {

    suspend fun convertToOpus(inputBytes: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        val tmpIn = Files.createTempFile("preview-in", ".tmp")
        val tmpOut = Files.createTempFile("preview-out", ".opus")
        var process: Process? = null
        try {
            tmpIn.toFile().writeBytes(inputBytes)

            process = ProcessBuilder(
                "ffmpeg", "-y",
                "-i", tmpIn.toAbsolutePath().toString(),
                "-c:a", "libopus",
                "-b:a", "56k",
                "-vn",
                "-ac", "2",
                "-ar", "48000",
                "-application", "audio",
                "-cutoff", "20000",
                "-compression_level", "3",
                "-frame_duration", "20",
                tmpOut.toAbsolutePath().toString()
            )
                .redirectErrorStream(true)
                .start()

            // Drena o stdout/stderr em paralelo, senão o processo pode travar
            // esperando alguém ler o buffer (deadlock).
            val outputDeferred = async { process.inputStream.bufferedReader().readText() }

            val exitCode = withTimeoutOrNull(30_000.milliseconds) {
                process.waitFor()
            }

            if (exitCode == null) {
                // Passou dos 30s: mata o processo e desiste.
                process.destroyForcibly()
                log.error("ffmpeg opus conversion timed out after 30s, process killed")
                return@withContext null
            }

            val output = outputDeferred.await()

            if (exitCode != 0) {
                log.error("ffmpeg opus conversion failed (exit=$exitCode): $output")
                return@withContext null
            }

            tmpOut.toFile().readBytes()
        } catch (e: Exception) {
            log.error("ffmpeg opus conversion error", e)
            process?.destroyForcibly()
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

            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            if (exitCode != 0) {
                log.error("ffmpeg avif conversion failed (exit=$exitCode): $output")
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
