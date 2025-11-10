# Beatstar Protobuf Parser - Kotlin

Este pacote é uma portagem do parser de protobuf JavaScript para Kotlin, permitindo decodificar arquivos `.bytes` do Beatstar em um formato estruturado.

## Uso

### Parse de Chart

```kotlin
import org.bscm.protobuf.ChartParser
import java.io.File

fun main() {
    // Ler o arquivo .bytes
    val chartBytes = File("chart.bytes").readBytes()
    
    // Parse do chart
    val chart = ChartParser.parse(chartBytes)
    
    // Acessar os dados
    println("Chart ID: ${chart.id}")
    println("Interactions ID: ${chart.interactionsId}")
    println("Total de notas: ${chart.notes.size}")
    println("Seções: ${chart.sections.size}")
}
```

### Estrutura do Chart

O objeto `Chart` retornado contém:

- `id`: ID do chart
- `interactionsId`: ID de interações
- `notes`: Lista de notas do beatmap
  - Cada nota pode ser: `single`, `long` ou `switchHold`
  - Contém informações como `offset`, `lane`, `size`, `swipe`
- `sections`: Seções do chart com offsets
- `perfectSizes`: Multiplicadores de tamanho de perfect hits
- `speeds`: Mudanças de velocidade durante o chart
- `effects`: Efeitos aplicados em diferentes offsets

### Exemplo com Controller REST

```kotlin
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/charts")
class ChartController {
    
    @PostMapping("/parse")
    fun parseChart(@RequestParam("file") file: MultipartFile): Chart {
        val bytes = file.bytes
        return ChartParser.parse(bytes)
    }
}
```

## Arquitetura

O pacote é composto por:

1. **ProtobufReader**: Leitor de bytes em formato protobuf customizado
2. **ProtoField**: Tipos de campos (Varint, String, Float, Group, PackedMessage)
3. **ChartProto**: Definição do protocolo específico para Charts
4. **ChartParser**: Parser principal que converte bytes em objetos tipados
5. **Data Classes**: Representações tipadas do Chart e suas estruturas

## Diferenças do JavaScript

- Uso de data classes ao invés de objetos dinâmicos
- Type safety completo
- Melhor performance com ByteBuffer do Java NIO
- Integração nativa com Spring Boot e Kotlin

## Notas

O formato protobuf do Beatstar não segue o formato tradicional protobuf. Algumas peculiaridades:

- Campo 1 geralmente especifica o tipo de classe da mensagem
- Campo 2 contém uma mensagem com campos que variam baseado no campo 1
- Isso foi tratado com mapeamentos dinâmicos no parser

### Correções Aplicadas (Nov 2025)

- **processBlocks**: Agora inclui a chave (key) e o length varint no segmento retornado, idêntico ao comportamento do JS. Isso é necessário porque os `ProtoField.read()` esperam ler a key e length do buffer.
- **Validações defensivas**: `readByte`, `readVarint`, e métodos de leitura de tamanho fixo agora lançam exceções claras quando não há bytes suficientes.
- **slice**: Lança `IndexOutOfBoundsException` com mensagens detalhadas quando o length solicitado excede os bytes disponíveis (idêntico ao JS).

### Tratamento de Erros

Se você receber `IndexOutOfBoundsException` ou `ArrayIndexOutOfBoundsException`:
- Verifique se o arquivo `.bytes` está completo e não truncado
- Para arquivos comprimidos (GZIP), verifique se a descompressão foi bem-sucedida
- Se o erro persistir com arquivos válidos, reporte com o stacktrace e arquivo de teste
