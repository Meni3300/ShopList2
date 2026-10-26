package com.shoplist.app.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TextNormalizer {
    private val niqqud = Regex("[\\u0591-\\u05C7]")
    private val spaces = Regex("\\s+")

    /** Name as shown to the user: no niqqud, single spaces, trimmed. */
    fun display(s: String): String = niqqud.replace(s, "").replace(spaces, " ").trim()

    /** Key used for duplicate detection. */
    fun normalize(s: String): String = display(s).lowercase(Locale.ROOT)
}

object Format {
    fun branches(n: Int): String = when (n) {
        0 -> "אין סניפים"
        1 -> "סניף אחד"
        else -> "$n סניפים"
    }

    fun times(n: Int): String = when (n) {
        0 -> "עדיין לא נקנה"
        1 -> "נקנה פעם אחת"
        else -> "נקנה $n פעמים"
    }

    fun date(ts: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ts))
}

object Categories {
    val all = listOf(
        "פירות וירקות", "מוצרי חלב", "בשר", "דגים", "מאפים", "קפואים",
        "שימורים", "שתייה", "חטיפים", "ניקיון", "פארם", "אחר"
    )

    private val keywords: List<Pair<String, List<String>>> = listOf(
        "פארם" to listOf("שמפו", "משחת שיניים", "דאודורנט", "מברשת", "קרם", "טמפון", "תחבושת", "מקלות אוזניים"),
        "ניקיון" to listOf("סבון", "אקונומיקה", "מרכך", "נייר טואלט", "אבקת כביסה", "שקיות", "ספוג", "מגבון", "נוזל כלים"),
        "קפואים" to listOf("קפוא", "גלידה", "פיצה"),
        "שתייה" to listOf("מים", "קפה", "תה", "מיץ", "קולה", "בירה", "יין", "סודה"),
        "חטיפים" to listOf("שוקולד", "חטיף", "במבה", "ביסלי", "צ'יפס", "פופקורן", "עוגיות"),
        "מוצרי חלב" to listOf("חלב", "גבינ", "יוגורט", "חמאה", "שמנת", "קוטג", "ביצ", "לבן", "מעדן"),
        "מאפים" to listOf("לחם", "פיתה", "לחמני", "חלה", "עוגה", "בגט"),
        "שימורים" to listOf("טונה", "שימור", "תירס", "חומוס", "זיתים", "רסק", "פסטה", "אורז", "קטשופ", "טחינה"),
        "דגים" to listOf("דג", "סלמון", "פילה"),
        "בשר" to listOf("עוף", "בשר", "שניצל", "קציצ", "נקניק", "סטייק", "המבורגר", "כבד"),
        "פירות וירקות" to listOf(
            "עגבני", "מלפפון", "בצל", "תפוח", "בננה", "גזר", "חסה", "לימון", "פלפל",
            "ירק", "פרי", "תפוז", "אבוקדו", "שום"
        )
    )

    fun guess(name: String): String {
        val n = TextNormalizer.normalize(name)
        return keywords.firstOrNull { (_, words) -> words.any { n.contains(it) } }?.first ?: "אחר"
    }

    private val productEmoji: List<Pair<String, String>> = listOf(
        "חלב" to "🥛", "לחם" to "🍞", "ביצ" to "🥚", "קפה" to "☕", "טונה" to "🐟",
        "שמפו" to "🧴", "גבינ" to "🧀", "עוף" to "🍗", "בשר" to "🥩", "עגבני" to "🍅",
        "בננה" to "🍌", "תפוח" to "🍎", "מים" to "💧", "שוקולד" to "🍫", "יוגורט" to "🥣",
        "אורז" to "🍚", "פסטה" to "🍝", "גלידה" to "🍨", "נייר טואלט" to "🧻", "סבון" to "🧼"
    )

    private val categoryEmoji = mapOf(
        "פירות וירקות" to "🥬", "מוצרי חלב" to "🥛", "בשר" to "🥩", "דגים" to "🐟",
        "מאפים" to "🥖", "קפואים" to "🧊", "שימורים" to "🥫", "שתייה" to "🥤",
        "חטיפים" to "🍿", "ניקיון" to "🧽", "פארם" to "💊", "אחר" to "🛒"
    )

    fun emoji(name: String, category: String): String {
        val n = TextNormalizer.normalize(name)
        return productEmoji.firstOrNull { n.contains(it.first) }?.second ?: (categoryEmoji[category] ?: "🛒")
    }
}
