package org.bscm.protobuf

import java.io.File

/**
 * Exemplo de uso do ChartParser.
 * 
 * Para testar:
 * 1. Coloque um arquivo .bytes no diretório do projeto
 * 2. Execute: ./gradlew run --args="path/to/chart.bytes"
 * 
 * Ou use como função auxiliar em testes/rotas.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Uso: ChartParserExample <caminho-para-arquivo.bytes>")
        println("Exemplo: ./gradlew run --args=\"./508.bytes\"")
        return
    }

    val filePath = args[0]
    val file = File(filePath)

    if (!file.exists()) {
        println("Erro: Arquivo não encontrado: $filePath")
        return
    }

    try {
        println("Lendo arquivo: $filePath (${file.length()} bytes)")
        val bytes = file.readBytes()
        
        println("Parseando chart...")
        val chart = ChartParser.parse(bytes)
        
        println("\n=== Chart Parseado com Sucesso ===")
        println("ID: ${chart.id}")
        println("Interactions ID: ${chart.interactionsId}")
        println("Notas: ${chart.notes.size}")
        println("Seções: ${chart.sections.size}")
        println("Perfect Sizes: ${chart.perfectSizes.size}")
        println("Speeds: ${chart.speeds.size}")
        println("Effects: ${chart.effects.size}")
        
        // Mostrar algumas notas como exemplo
        if (chart.notes.isNotEmpty()) {
            println("\n=== Primeiras 5 Notas ===")
            chart.notes.take(5).forEachIndexed { index, note ->
                println("Nota $index:")
                println("  Tipo: ${note.noteType}")
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
                        println("  Long note com ${note.long.notes.size} posições")
                        println("    Swipe: ${note.long.swipe}")
                    }
                    note.switchHold != null -> {
                        println("  Switch hold com ${note.switchHold.positions.size} posições")
                        println("    Swipe: ${note.switchHold.swipe}")
                    }
                }
            }
        }
        
        // Mostrar seções
        if (chart.sections.isNotEmpty()) {
            println("\n=== Primeiras 5 Seções ===")
            chart.sections.take(5).forEachIndexed { index, section ->
                println("Seção $index: offset=${section.offset}")
            }
        }
        
        println("\n✅ Parse completo!")
        
    } catch (e: IndexOutOfBoundsException) {
        println("\n❌ Erro: Arquivo corrompido ou truncado")
        println("Detalhes: ${e.message}")
        e.printStackTrace()
    } catch (e: Exception) {
        println("\n❌ Erro ao parsear chart")
        println("Detalhes: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * Função auxiliar para usar em testes ou rotas da API.
 */
fun parseChartSafely(bytes: ByteArray): Result<Chart> {
    return try {
        Result.success(ChartParser.parse(bytes))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
