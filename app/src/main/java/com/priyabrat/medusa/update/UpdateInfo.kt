package com.priyabrat.medusa.update

import org.json.JSONObject

/** Contents of latest.json published with every GitHub release. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String?,
    val notes: String?,
) {
    companion object {
        fun parse(json: String): UpdateInfo {
            val o = JSONObject(json)
            return UpdateInfo(
                versionCode = o.getLong("versionCode"),
                versionName = o.getString("versionName"),
                apkUrl = o.getString("apkUrl"),
                sha256 = o.optString("sha256").ifBlank { null },
                notes = o.optString("notes").ifBlank { null },
            )
        }
    }
}
