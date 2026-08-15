package org.bscm.models

interface Versionable {
    val versionsCount: Int
    val latestVersion: Version?
    val bundleHash: String?
}
