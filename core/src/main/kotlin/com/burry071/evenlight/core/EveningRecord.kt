package com.burry071.evenlight.core

/**
 * One evening. Written before the card is rendered, so a render failure cannot lose the
 * observation (spec section 11). Python reads the same file with json.load.
 */
data class EveningRecord(
    val date: String,
    val shutter: String,
    val bandsHex: List<String>,
    val winnerIndex: Int,
    val name: String,
    val sunAltitudeDeg: Int,
    val meanLuminance: Double,
    val wording: String,
    val wordingSource: String,      // "model" | "template" | "none"
    val wordingRejected: Boolean,
    val attempt: Int,
    val clockSuspect: Boolean
) {
    fun encode(): String = Json.encode(
        Json.Obj(
            listOf(
                "date" to Json.Str(date),
                "shutter" to Json.Str(shutter),
                "bands" to Json.Arr(bandsHex.map { Json.Str(it) }),
                "winner" to Json.Num(winnerIndex.toDouble()),
                "name" to Json.Str(name),
                "sun_altitude_deg" to Json.Num(sunAltitudeDeg.toDouble()),
                "mean_luminance" to Json.Num(meanLuminance),
                "wording" to Json.Str(wording),
                "wording_source" to Json.Str(wordingSource),
                "wording_rejected" to Json.Bool(wordingRejected),
                "attempt" to Json.Num(attempt.toDouble()),
                "clock_suspect" to Json.Bool(clockSuspect)
            )
        )
    )

    companion object {
        fun decode(text: String): EveningRecord? {
            val o = Json.tryParse(text) as? Json.Obj ?: return null
            val bands = o.arr("bands") ?: return null
            if (bands.size != 8 || bands.any { it !is Json.Str }) return null
            return EveningRecord(
                date = o.str("date") ?: return null,
                shutter = o.str("shutter") ?: return null,
                bandsHex = bands.map { (it as Json.Str).v },
                winnerIndex = o.int("winner") ?: return null,
                name = o.str("name") ?: return null,
                sunAltitudeDeg = o.int("sun_altitude_deg") ?: return null,
                meanLuminance = o.num("mean_luminance") ?: return null,
                wording = o.str("wording") ?: return null,
                wordingSource = o.str("wording_source") ?: return null,
                wordingRejected = o.bool("wording_rejected") ?: return null,
                attempt = o.int("attempt") ?: 1,
                clockSuspect = o.bool("clock_suspect") ?: false
            )
        }
    }
}
