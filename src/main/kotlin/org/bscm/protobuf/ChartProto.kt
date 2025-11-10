package org.bscm.protobuf

/**
 * Definição do protocolo para Chart (beatmaps).
 */
object ChartProto {
    val proto = mapOf(
        1 to VarintField("id", 1),
        2 to StringField("interactions_id", 2),
        5 to PackedMessageField("notes", 5, mapOf(
            1 to VarintField("note_type", 1),
            3 to GroupField("single", 3, mapOf(
                1 to GroupField("note", 1, mapOf(
                    1 to FloatField("offset", 1),
                    2 to GroupField("sizes", 2, mapOf(
                        1 to FloatField("x", 1),
                        2 to FloatField("y", 2)
                    )),
                    3 to VarintField("lane", 3)
                )),
                2 to VarintField("swipe", 2)
            )),
            4 to GroupField("long", 4, mapOf(
                1 to GroupField("note", 1, mapOf(
                    1 to FloatField("offset", 1),
                    2 to GroupField("sizes", 2, mapOf(
                        1 to FloatField("x", 1),
                        2 to FloatField("y", 2)
                    )),
                    3 to VarintField("lane", 3)
                ), mapOf("repeating" to true)),
                2 to VarintField("swipe", 2)
            )),
            6 to VarintField("lane", 6),
            13 to VarintField("size", 13),
            14 to GroupField("note", 14, mapOf(
                1 to GroupField("switchHold", 1, mapOf(
                    1 to FloatField("offset", 1),
                    2 to GroupField("vec", 2, mapOf(
                        1 to FloatField("x", 1),
                        2 to FloatField("y", 2)
                    )),
                    3 to VarintField("lane", 3)
                ), mapOf("repeating" to true)),
                2 to VarintField("swipe", 2)
            ))
        )),
        6 to PackedMessageField("sections", 6, mapOf(
            1 to FloatField("offset", 1)
        )),
        7 to PackedMessageField("perfectSizes", 7, mapOf(
            1 to FloatField("offset", 1),
            2 to FloatField("multiplier", 2)
        )),
        8 to PackedMessageField("speeds", 8, mapOf(
            1 to FloatField("offset", 1),
            2 to FloatField("multiplier", 2)
        )),
        9 to PackedMessageField("effects", 9, mapOf(
            1 to FloatField("offset", 1),
            4 to VarintField("effects", 4, mapOf("repeating" to true, "key" to true))
        ))
    )
}
