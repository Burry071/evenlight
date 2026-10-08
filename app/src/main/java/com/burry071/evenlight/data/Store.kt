package com.burry071.evenlight.data

import com.burry071.evenlight.core.EveningRecord
import com.burry071.evenlight.core.Json
import java.io.File
import java.time.LocalDateTime

class Store(val root: File) {
    private val base = File(root, "evenlight").apply { mkdirs() }
    private val raw = File(base, "raw").apply { mkdirs() }
    private val cards = File(base, "cards").apply { mkdirs() }
    private val records = File(base, "records").apply { mkdirs() }
    private val settingsFile = File(base, "settings.json")

    /**
     * Built from parts, not with DateTimeFormatter. This string becomes a filename, a record field, and the key
     * Task 10's Python matches on, so it must be ASCII under every device locale. Measured on this host's JDK 21,
     * `ofPattern("yyyy-MM-dd--HHmm")` under ar-EG-u-nu-arab and th-TH-u-ca-buddhist still emitted ASCII digits,
     * so this is insurance against a locale/CLDR combination nobody here reproduced, not a proven defect - and no
     * test on this host can give it teeth, because the formatter version passes whatever locale test is written.
     * Same reasoning, same shape of fix as Pixels.HEX_DIGITS.
     */
    fun nextShutterName(now: LocalDateTime): String =
        "${now.toLocalDate()}--${twoDigit(now.hour)}${twoDigit(now.minute)}"

    private fun twoDigit(v: Int): String = if (v < 10) "0$v" else v.toString()

    fun attemptsFor(date: String): Int =
        records.listFiles()?.count { it.name == "$date.json" || it.name.startsWith("$date-attempt-") } ?: 0

    /**
     * The attempt is part of the name because the shutter name is only precise to the minute: two shots inside one
     * minute would otherwise write the same file and lose the first evening's only evidence of what was measured.
     */
    fun writeRaw(jpeg: ByteArray, shutter: String, attempt: Int): File =
        File(raw, "$shutter-attempt-$attempt.jpg").apply { writeBytes(jpeg) }

    fun recordFile(date: String, attempt: Int): File =
        if (attempt <= 1) File(records, "$date.json") else File(records, "$date-attempt-$attempt.json")

    fun writeRecord(r: EveningRecord, attempt: Int): File = recordFile(r.date, attempt).apply { writeText(r.encode()) }

    /**
     * Every file is read inside `catch (t: Throwable)`, not `Exception`: [Json.tryParse] recurses once per bracket
     * and only catches Exception, so one hostile or fumbled 200k-`[` file raises StackOverflowError. Caught per
     * file, that file is one corrupt record; caught nowhere, it empties the stack and Home says "no evenings yet"
     * over a month of good JSON. `readText()` throws IOException for the same reason - a file can be mid-delete.
     */
    private fun decodeOrNull(f: File): EveningRecord? = try {
        EveningRecord.decode(f.readText())
    } catch (t: Throwable) {
        null
    }

    fun readRecords(): List<EveningRecord> =
        records.listFiles()?.mapNotNull { f -> decodeOrNull(f)?.let { f to it } }
            ?.sortedWith(compareBy({ orderOf(it.first.name).first }, { orderOf(it.first.name).second }, { it.first.name }))
            ?.map { it.second } ?: emptyList()

    /**
     * The (date, attempt) a filename asserts. Ordering comes from the NAME because the name is what recordFile
     * wrote: sorting by the decoded pair instead would let a body whose `attempt` field is missing - decode defaults
     * it to 1 - or whose `date` was edited outside the app decide the stack's order. Nothing here filters: a file
     * that decodes is a record and shows up, and an evening that vanishes from this list is the exact failure this
     * store exists to prevent.
     *
     * The filename is the third key in the comparator above, and that is not decoration. A name the app would
     * never write - `2026-10-08-attempt-abc.json`, say - parses to attempt 1 here and would then tie with the real
     * bare-date file, leaving `listFiles()` order to decide which one Task 10's "latest attempt wins" picks. With
     * the name in the key the order is total and the same on every filesystem.
     */
    private fun orderOf(name: String): Pair<String, Int> {
        val base = name.removeSuffix(".json")
        val i = base.indexOf("-attempt-")
        return if (i < 0) base to 1 else base.substring(0, i) to (base.substring(i + 9).toIntOrNull() ?: 1)
    }

    fun corruptRecords(): Int = records.listFiles()?.count {
        it.name.endsWith(".json") && decodeOrNull(it) == null
    } ?: 0

    fun cardFile(date: String): File = File(cards, "$date.png")
    fun writeCard(date: String, png: ByteArray): File = cardFile(date).apply { writeBytes(png) }
    fun eveningsWithCards(): Set<String> = cards.listFiles()?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()

    fun wordingTotal(): Int = readRecords().size
    fun wordingRejected(): Int = readRecords().count { it.wordingRejected }

    fun loadSettings(): Settings {
        val text = try { settingsFile.readText() } catch (t: Throwable) { return Settings.UNSET }
        val o = try { Json.tryParse(text) as? Json.Obj } catch (t: Throwable) { null } ?: return Settings.UNSET
        return Settings(o.str("place") ?: "", o.num("lat") ?: 0.0, o.num("lon") ?: 0.0)
    }

    fun saveSettings(s: Settings) {
        settingsFile.writeText(
            Json.encode(Json.Obj(listOf("place" to Json.Str(s.place), "lat" to Json.Num(s.lat), "lon" to Json.Num(s.lon))))
        )
    }
}
