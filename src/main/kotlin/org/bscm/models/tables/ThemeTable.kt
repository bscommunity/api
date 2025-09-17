package org.bscm.models.tables

object ThemeTable : CatalogItemTable("themes") {
    val name = varchar("name", 255)
    val replaces = varchar("replaces", 255)
    val previewUrl = varchar("preview_url", 255)
}
