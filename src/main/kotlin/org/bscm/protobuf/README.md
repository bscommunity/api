# Protobuf Parser

This package is a port of the JavaScript protobuf parser to Kotlin, allowing decoding of Beatstar `.bytes` files into a structured format.

## Usage

### Chart Parsing

```kotlin
fun main() {
    // Read the .bytes file
    val chartBytes = File("chart.bytes").readBytes()
    
    // Parse the chart
    val chart = ChartParser.parse(chartBytes)
    
    // Access the data
    println("Chart ID: ${chart.id}")
    println("Interactions ID: ${chart.interactionsId}")
    println("Total notes: ${chart.notes.size}")
    println("Sections: ${chart.sections.size}")
}
```

### Chart Structure

The returned `Chart` object contains:

- `id`: Chart ID
- `interactionsId`: Interactions ID
- `notes`: List of beatmap notes
  - Each note can be: `single`, `long` or `switchHold`
  - Contains information like `offset`, `lane`, `size`, `swipe`
- `sections`: Chart sections with offsets
- `perfectSizes`: Multipliers for perfect hit sizes
- `speeds`: Speed changes during the chart
- `effects`: Effects applied at different offsets

### REST Controller Example

```kotlin
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

## Architecture

The package consists of:

1. **ProtobufReader**: Custom protobuf format byte reader
2. **ProtoField**: Field types (Varint, String, Float, Group, PackedMessage)
3. **ChartProto**: Specific protocol definition for Charts
4. **ChartParser**: Main parser that converts bytes to typed objects
5. **Data Classes**: Typed representations of the Chart and its structures

## Differences from JavaScript

- Use of data classes instead of dynamic objects
- Complete type safety
- Better performance with Java NIO ByteBuffer
- Native integration with Spring Boot and Kotlin

## Notes

The Beatstar protobuf format does not follow the traditional protobuf format. Some peculiarities:

- Field 1 generally specifies the message class type
- Field 2 contains a message with fields that vary based on field 1
- This was handled with dynamic mappings in the parser

### Error Handling

If you receive `IndexOutOfBoundsException` or `ArrayIndexOutOfBoundsException`:
- Check if the `.bytes` file is complete and not truncated
- For compressed files (GZIP), verify that decompression was successful
- If the error persists with valid files, report with stacktrace and test file
