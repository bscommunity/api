package org.bscm.protobuf

/**
 * Main parser to convert Chart bytes into a structured object.
 */
object ChartParser {
    
    /**
     * Parse raw bytes into a Chart object.
     *
     * @param data ByteArray containing the chart data in protobuf format
     * @return Chart object with all parsed information
     */
    fun parse(data: ByteArray): Chart {
        val reader = ProtobufReader(data)
        reader.process()
        
        val parsed = reader.parseProto<ChartProto>(ChartProto.proto)
        
        return Chart(
            id = parsed["id"] as? Int,
            interactionsId = parsed["interactions_id"] as? String,
            notes = parseNotes(parsed["notes"]),
            sections = parseSections(parsed["sections"]),
            perfectSizes = parsePerfectSizes(parsed["perfectSizes"]),
            speeds = parseSpeeds(parsed["speeds"]),
            effects = parseEffects(parsed["effects"])
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseNotes(notesData: Any?): List<Note> {
        if (notesData !is List<*>) return emptyList()
        
        return notesData.mapNotNull { noteMap ->
            if (noteMap !is Map<*, *>) return@mapNotNull null
            
            val noteType = noteMap["note_type"] as? Int
            val lane = noteMap["lane"] as? Int
            val size = noteMap["size"] as? Int
            
            when {
                noteMap.containsKey("single") -> {
                    val single = noteMap["single"] as? Map<String, Any?>
                    Note(
                        noteType = noteType,
                        single = parseSingleNote(single),
                        lane = lane,
                        size = size
                    )
                }
                noteMap.containsKey("long") -> {
                    val long = noteMap["long"] as? Map<String, Any?>
                    Note(
                        noteType = noteType,
                        long = parseLongNote(long),
                        lane = lane,
                        size = size
                    )
                }
                noteMap.containsKey("note") -> {
                    val noteData = noteMap["note"] as? Map<String, Any?>
                    Note(
                        noteType = noteType,
                        switchHold = parseSwitchHold(noteData),
                        lane = lane,
                        size = size
                    )
                }
                else -> null
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSingleNote(data: Map<String, Any?>?): SingleNote? {
        if (data == null) return null
        
        val noteData = data["note"] as? Map<String, Any?>
        val swipe = data["swipe"] as? Int
        
        return SingleNote(
            note = parseNoteData(noteData),
            swipe = swipe
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseLongNote(data: Map<String, Any?>?): LongNote? {
        if (data == null) return null
        
        val notes = (data["note"] as? List<*>)?.mapNotNull { 
            parseNoteData(it as? Map<String, Any?>)
        } ?: emptyList()
        val swipe = data["swipe"] as? Int
        
        return LongNote(
            notes = notes,
            swipe = swipe
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSwitchHold(data: Map<String, Any?>?): SwitchHold? {
        if (data == null) return null
        
        val switchHold = data["switchHold"] as? List<*>
        val swipe = data["swipe"] as? Int
        
        val positions = switchHold?.mapNotNull { 
            parseNoteData(it as? Map<String, Any?>)
        } ?: emptyList()
        
        return SwitchHold(
            positions = positions,
            swipe = swipe
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseNoteData(data: Map<String, Any?>?): NoteData? {
        if (data == null) return null
        
        val offset = data["offset"] as? Float
        val sizes = data["sizes"] as? Map<String, Any?>
        val lane = data["lane"] as? Int
        
        val vec = if (sizes != null) {
            Vec2(
                x = sizes["x"] as? Float ?: 0f,
                y = sizes["y"] as? Float ?: 0f
            )
        } else {
            data["vec"]?.let { vecData ->
                if (vecData is Map<*, *>) {
                    Vec2(
                        x = vecData["x"] as? Float ?: 0f,
                        y = vecData["y"] as? Float ?: 0f
                    )
                } else null
            }
        }
        
        return NoteData(
            offset = offset ?: 0f,
            sizes = vec,
            lane = lane
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSections(sectionsData: Any?): List<Section> {
        if (sectionsData !is List<*>) return emptyList()
        
        return sectionsData.mapNotNull { sectionMap ->
            if (sectionMap !is Map<*, *>) return@mapNotNull null
            Section(offset = sectionMap["offset"] as? Float ?: 0f)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePerfectSizes(data: Any?): List<PerfectSize> {
        if (data !is List<*>) return emptyList()
        
        return data.mapNotNull { item ->
            if (item !is Map<*, *>) return@mapNotNull null
            PerfectSize(
                offset = item["offset"] as? Float ?: 0f,
                multiplier = item["multiplier"] as? Float ?: 1f
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSpeeds(data: Any?): List<Speed> {
        if (data !is List<*>) return emptyList()
        
        return data.mapNotNull { item ->
            if (item !is Map<*, *>) return@mapNotNull null
            Speed(
                offset = item["offset"] as? Float ?: 0f,
                multiplier = item["multiplier"] as? Float ?: 1f
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEffects(data: Any?): List<Effect> {
        if (data !is List<*>) return emptyList()
        
        return data.mapNotNull { item ->
            if (item !is Map<*, *>) return@mapNotNull null
            Effect(
                offset = item["offset"] as? Float ?: 0f,
                effects = (item["effects"] as? List<*>)?.mapNotNull { it as? Int } ?: emptyList()
            )
        }
    }
}

// Data classes to represent the Chart
data class Chart(
    val id: Int?,
    val interactionsId: String?,
    val notes: List<Note>,
    val sections: List<Section>,
    val perfectSizes: List<PerfectSize>,
    val speeds: List<Speed>,
    val effects: List<Effect>
)

data class Note(
    val noteType: Int?,
    val single: SingleNote? = null,
    val long: LongNote? = null,
    val switchHold: SwitchHold? = null,
    val lane: Int?,
    val size: Int?
)

data class SingleNote(
    val note: NoteData?,
    val swipe: Int?
)

data class LongNote(
    val notes: List<NoteData>,
    val swipe: Int?
)

data class SwitchHold(
    val positions: List<NoteData>,
    val swipe: Int?
)

data class NoteData(
    val offset: Float,
    val sizes: Vec2?,
    val lane: Int?
)

data class Vec2(
    val x: Float,
    val y: Float
)

data class Section(
    val offset: Float
)

data class PerfectSize(
    val offset: Float,
    val multiplier: Float
)

data class Speed(
    val offset: Float,
    val multiplier: Float
)

data class Effect(
    val offset: Float,
    val effects: List<Int>
)
