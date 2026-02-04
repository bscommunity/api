package org.bscm.protobuf

import java.io.File

/**
 * Example of using ChartParser.
 *
 * To test:
 * 1. Place a .bytes file in the project directory
 * 2. Run: ./gradlew run --args="path/to/chart.bytes"
 *
 * Or use as a helper function in tests/routes.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: ChartParserExample <path-to-file.bytes>")
        println("Example: ./gradlew run --args=\"./508.bytes\"")
        return
    }

    val filePath = args[0]
    val file = File(filePath)

    if (!file.exists()) {
        println("Error: File not found: $filePath")
        return
    }

    try {
        println("Reading file: $filePath (${file.length()} bytes)")
        val bytes = file.readBytes()
        
        println("Parsing chart...")
        val chart = ChartParser.parse(bytes)
        
        println("\n=== Parsed Chart Successfully ===")
        println("ID: ${chart.id}")
        println("Interactions ID: ${chart.interactionsId}")
        println("Notes: ${chart.notes.size}")
        println("Sections: ${chart.sections.size}")
        println("Perfect Sizes: ${chart.perfectSizes.size}")
        println("Speeds: ${chart.speeds.size}")
        println("Effects: ${chart.effects.size}")
        
        // Show some notes as example
        if (chart.notes.isNotEmpty()) {
            println("\n=== First 5 Notes ===")
            chart.notes.take(5).forEachIndexed { index, note ->
                println("Note $index:")
                println("  Type: ${note.noteType}")
                println("  Lane: ${note.lane}")
                println("  Size: ${note.size}")
                when {
                    note.single != null -> {
                        println("  Single note:")
                        println("    Offset: ${note.single.note?.offset}")
                        println("    Lane: ${note.single.note?.lane}")
                        println("    Swipe: ${note.single.swipe}")
                    }
                    note.long != null -> {
                        println("  Long note with ${note.long.notes.size} positions")
                        println("    Swipe: ${note.long.swipe}")
                    }
                    note.switchHold != null -> {
                        println("  Switch hold with ${note.switchHold.positions.size} positions")
                        println("    Swipe: ${note.switchHold.swipe}")
                    }
                }
            }
        }
        
        // Show sections
        if (chart.sections.isNotEmpty()) {
            println("\n=== First 5 Sections ===")
            chart.sections.take(5).forEachIndexed { index, section ->
                println("Section $index: offset=${section.offset}")
            }
        }
        
        println("\n✅ Parse complete!")

    } catch (e: IndexOutOfBoundsException) {
        println("\n❌ Error: Corrupted or truncated file")
        println("Details: ${e.message}")
        e.printStackTrace()
    } catch (e: Exception) {
        println("\n❌ Error parsing chart")
        println("Details: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * Helper function to use in tests or API routes.
 */
fun parseChartSafely(bytes: ByteArray): Result<Chart> {
    return try {
        Result.success(ChartParser.parse(bytes))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
