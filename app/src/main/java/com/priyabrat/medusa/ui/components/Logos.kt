package com.priyabrat.medusa.ui.components

import com.priyabrat.medusa.R

/*
 * Service and platform logos, converted from the Mac app's public/icons (Dashboard Icons and selfh.st/icons, CC BY 4.0;
 * Rotten Tomatoes icons from Wikimedia Commons). A monogram that matches a key here shows the logo instead of letters.
 * Keys are the Mac app's service codes (services table) and platform slugs (platforms table).
 * SonyLIV, ZEE5 and Lionsgate Play have no logo there either, so they stay lettered badges, as on the Mac.
 */
object Logos {
    private val byKey: Map<String, Int> = mapOf(
        "NF" to R.drawable.logo_netflix, "netflix" to R.drawable.logo_netflix,
        "PV" to R.drawable.logo_prime, "prime" to R.drawable.logo_prime,
        "AP" to R.drawable.logo_apple, "apple" to R.drawable.logo_apple,
        "JH" to R.drawable.logo_jiohotstar,
        "MU" to R.drawable.logo_mubi,
        "PX" to R.drawable.logo_plex,
        "hbo" to R.drawable.logo_hbo,
        "crunchy" to R.drawable.logo_crunchy,
        "disney" to R.drawable.logo_disney,
        "hulu" to R.drawable.logo_hulu,
        "paramount" to R.drawable.logo_paramount,
        "peacock" to R.drawable.logo_peacock,
    )

    /** The logo for a service code or platform slug, or null to show the letters. */
    fun forKey(key: String): Int? = byKey[key]

    /** Lettered badge text for codes without a logo (e.g. SL for SonyLIV). */
    fun letters(key: String): String = if (key.length <= 2) key else key.take(2).uppercase()
}
