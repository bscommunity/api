package org.bscm.utils

import io.ktor.util.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit

private val log = KtorSimpleLogger("MediaConverter")

object MediaConverter {

    suspend fun convertToOpus(inputBytes: ByteArray, previewDurationSeconds: Int = 15): ByteArray? = withContext(Dispatchers.IO) {
        val tmpIn = Files.createTempFile("preview-in", ".tmp")
        try {
            tmpIn.toFile().writeBytes(inputBytes)

            val process = ProcessBuilder(
                "ffmpeg",
                "-y",
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-i", tmpIn.toAbsolutePath().toString(),
                "-t", previewDurationSeconds.toString(),
                "-vn",
                "-c:a", "libopus",
                "-b:a", "48k",
                "-vbr", "on",
                "-compression_level", "10",
                "-frame_duration", "60",
                "-ac", "2",
                "-ar", "48000",
                "-application", "audio",
                "-cutoff", "18000",
                "-f", "opus",
                "pipe:1" // stream the encoded audio straight out, no output temp file
            ).start()

            process.outputStream.close() // we never write to ffmpeg's stdin; free the fd immediately

            // Drain stdout (audio bytes) and stderr (log text) on separate threads WHILE the
            // process runs. This is what actually fixes the deadlock above — reading only
            // matters if it happens concurrently with the process still writing.
            val stdoutBuffer = ByteArrayOutputStream()
            val stderrBuffer = ByteArrayOutputStream()

            val stdoutReader = Thread({
                process.inputStream.use { it.copyTo(stdoutBuffer) }
            }, "ffmpeg-opus-stdout").apply { isDaemon = true; start() }

            val stderrReader = Thread({
                process.errorStream.use { it.copyTo(stderrBuffer) }
            }, "ffmpeg-opus-stderr").apply { isDaemon = true; start() }

            val exited = process.waitFor(60, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                stdoutReader.join(2_000)
                stderrReader.join(2_000)
                log.error("ffmpeg opus conversion timed out: ${stderrBuffer.toString(Charsets.UTF_8.name())}")
                return@withContext null
            }

            stdoutReader.join()
            stderrReader.join()

            if (process.exitValue() != 0) {
                log.error("ffmpeg opus conversion failed (exit=${process.exitValue()}): ${stderrBuffer.toString(Charsets.UTF_8.name())}")
                return@withContext null
            }

            stdoutBuffer.toByteArray()
        } catch (e: Exception) {
            log.error("ffmpeg opus conversion error", e)
            null
        } finally {
            tmpIn.toFile().delete()
        }
    }

    suspend fun convertToAvif(inputBytes: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        val tmpIn = Files.createTempFile("cover-in", ".png")
        val tmpOut = Files.createTempFile("cover-out", ".avif")
        try {
            tmpIn.toFile().writeBytes(inputBytes)

            val process = ProcessBuilder(
                "ffmpeg",
                "-y",
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-i", tmpIn.toAbsolutePath().toString(),
                "-c:v", "libaom-av1",
                "-crf", "40",
                "-b:v", "0",
                "-strict", "experimental",
                "-pix_fmt", "yuv420p",
                "-still-picture", "1",
                "-row-mt", "1",
                tmpOut.toAbsolutePath().toString()
            ).start()

            process.outputStream.close()

            val stdoutBuffer = ByteArrayOutputStream()
            val stderrBuffer = ByteArrayOutputStream()

            val stdoutReader = Thread({
                process.inputStream.use { it.copyTo(stdoutBuffer) }
            }, "ffmpeg-avif-stdout").apply { isDaemon = true; start() }

            val stderrReader = Thread({
                process.errorStream.use { it.copyTo(stderrBuffer) }
            }, "ffmpeg-avif-stderr").apply { isDaemon = true; start() }

            val exited = process.waitFor(60, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                stdoutReader.join(2_000)
                stderrReader.join(2_000)
                log.error("ffmpeg avif conversion timed out: ${stderrBuffer.toString(Charsets.UTF_8.name())}")
                return@withContext null
            }

            stdoutReader.join()
            stderrReader.join()

            if (process.exitValue() != 0) {
                log.error("ffmpeg avif conversion failed (exit=${process.exitValue()}): ${stderrBuffer.toString(Charsets.UTF_8.name())}")
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
