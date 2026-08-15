package org.bscm.utils

import io.ktor.util.logging.*
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MediaConverterTest {

    private val logger = KtorSimpleLogger("MediaConverterTest")

    @Test
    fun `convertToOpus with local file`() {
        val file = resolveMediaFile()
        val inputBytes = file.readBytes()
        val result = runBlocking { MediaConverter.convertToOpus(inputBytes, previewDurationSeconds = 10) }

        assertNotNull(result, "convertToOpus returned null — is ffmpeg installed?")
        assertTrue(result.isNotEmpty(), "opus output is empty")
        logger.info("Opus output size: ${result.size} bytes")
    }

    @Test
    fun `convertToAvif with local file`() {
        val file = resolveMediaFile()
        val inputBytes = file.readBytes()
        val result = runBlocking { MediaConverter.convertToAvif(inputBytes) }

        assertNotNull(result, "convertToAvif returned null — is ffmpeg installed?")
        assertTrue(result.isNotEmpty(), "avif output is empty")
        logger.info("Avif output size: ${result.size} bytes")
    }

    @Test
    fun `opus bitrate comparison`() {
        val file = resolveMediaFile()
        val outDir = file.parentFile
        val bitrates = listOf("16k", "20k", "24k", "28k", "32k")
        val results = mutableListOf<Pair<String, Long>>()

        for (br in bitrates) {
            val outFile = File(outDir, "test_${br}.opus")
            val process = ProcessBuilder(
                "ffmpeg",
                "-y",
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-i", file.absolutePath,
                "-t", "10",
                "-vn",
                "-af", "loudnorm=I=-16:TP=-1.5:LRA=11",
                "-c:a", "libopus",
                "-b:a", br,
                "-vbr", "on",
                "-compression_level", "10",
                "-frame_duration", "60",
                "-ac", "1",
                "-ar", "48000",
                "-application", "audio",
                "-f", "opus",
                outFile.absolutePath
            ).start()

            process.outputStream.close()

            val stderrBuffer = ByteArrayOutputStream()
            val stderrReader = Thread({
                process.errorStream.use { it.copyTo(stderrBuffer) }
            }, "ffmpeg-bitrate-$br").apply { isDaemon = true; start() }

            val exited = process.waitFor(60, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                stderrReader.join(2_000)
                error("ffmpeg timed out at ${br}: ${stderrBuffer.toString(Charsets.UTF_8.name())}")
            }
            stderrReader.join()

            val exitCode = process.exitValue()
            assertTrue(exitCode == 0, "ffmpeg failed (exit=$exitCode) at ${br}: ${stderrBuffer.toString(Charsets.UTF_8.name())}")
            assertTrue(outFile.exists(), "Output file not created for bitrate $br")

            results.add(br to outFile.length())
            logger.info("  ${br}: ${outFile.length()} bytes -> ${outFile.name}")
        }

        logger.info("--- Bitrate comparison for ${file.name} ---")
        results.forEach { (br, size) -> logger.info("  $br  $size bytes") }

        results.zipWithNext { (_, a), (_, b) ->
            assertTrue(b >= a, "File size decreased from ${a} to ${b}")
        }
    }

    private fun resolveMediaFile(): File {
        val path = System.getProperty("test.media.file")
            ?: System.getenv("TEST_MEDIA_FILE")
            ?: error(
                "Set the input file via either:\n" +
                "  - System property: -Dtest.media.file=C:/path/to/file\n" +
                "  - Environment var:  TEST_MEDIA_FILE=C:/path/to/file\n" +
                "Example:\n" +
                "  In IntelliJ Run Config -> VM options: -Dtest.media.file=C:\\Users\\Eduardo\\Documents\\Projetos\\bscm\\preview.mp3\n" +
                "  Or set env var TEST_MEDIA_FILE in your shell / IntelliJ run config."
            )
        val file = File(path)
        assertTrue(file.exists(), "File not found: $path")
        assertTrue(file.isFile, "Not a file: $path")
        return file
    }
}
