package org.bscm.models.tables

object TourPassTable : CatalogItemTable("tour_passes") {
    val name = varchar("name", 255)
    val artist = varchar("artist", 200).nullable()
}
