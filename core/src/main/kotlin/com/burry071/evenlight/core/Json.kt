package com.burry071.evenlight.core

sealed class Json {
    data class Obj(val fields: List<Pair<String, Json>>) : Json()
    data class Arr(val items: List<Json>) : Json()
    data class Str(val v: String) : Json()
    data class Num(val v: Double) : Json()
    data class Bool(val v: Boolean) : Json()

    fun field(name: String): Json? = (this as? Obj)?.fields?.firstOrNull { it.first == name }?.second
    fun str(name: String): String? = (field(name) as? Str)?.v
    fun num(name: String): Double? = (field(name) as? Num)?.v
    fun int(name: String): Int? = (field(name) as? Num)?.v?.let {
        // Integral-or-nothing, not truncating. The only callers read stored integers (`winner`,
        // `sun_altitude_deg`, `attempt`); a file holding 6.7 there is corrupt, and quietly reading it as 6
        // would turn a broken record into a plausible one. NaN falls out here too, since NaN != anything.
        if (it == it.toLong().toDouble()) it.toInt() else null
    }
    fun bool(name: String): Boolean? = (field(name) as? Bool)?.v
    fun arr(name: String): List<Json>? = (field(name) as? Arr)?.items

    companion object {
        fun encode(j: Json): String = buildString { write(j, this) }
        fun tryParse(s: String): Json? = try { Parser(s).document() } catch (e: Exception) { null }

        private fun write(j: Json, out: StringBuilder) {
            when (j) {
                is Str -> out.append('"').append(escape(j.v)).append('"')
                is Num -> out.append(if (j.v == j.v.toLong().toDouble()) j.v.toLong().toString() else j.v.toString())
                is Bool -> out.append(j.v.toString())
                is Arr -> {
                    out.append('[')
                    j.items.forEachIndexed { i, v -> if (i > 0) out.append(','); write(v, out) }
                    out.append(']')
                }
                is Obj -> {
                    out.append('{')
                    j.fields.forEachIndexed { i, (k, v) ->
                        if (i > 0) out.append(',')
                        out.append('"').append(escape(k)).append("\":")
                        write(v, out)
                    }
                    out.append('}')
                }
            }
        }

        private fun escape(s: String): String = buildString {
            for (c in s) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                '\r' -> append("\\r")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }
}

private class Parser(private val s: String) {
    private var i = 0

    fun document(): Json { val v = value(); ws(); require(i == s.length) { "trailing content" }; return v }

    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

    private fun value(): Json {
        ws()
        require(i < s.length) { "unexpected end" }
        return when (s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> Json.Str(string())
            't' -> literal("true") { Json.Bool(true) }
            'f' -> literal("false") { Json.Bool(false) }
            // No 'n' case on purpose. This codec cannot write null, so a file containing one was not written
            // by us; `null` falls through to number(), which consumes nothing and throws. See the test.
            else -> number()
        }
    }

    private fun literal(text: String, make: () -> Json): Json {
        require(s.startsWith(text, i)) { "bad literal" }
        i += text.length
        return make()
    }

    private fun number(): Json {
        val start = i
        if (i < s.length && s[i] == '-') i++
        // Without this guard the scan below happily starts on a `+`, because `+` has to be legal inside an
        // exponent (`1e+5`) - so `+1.5` would parse, while Python's json.load rejects it. A number must begin
        // with a digit or a minus, and this says so before the permissive scan runs.
        require(i < s.length && s[i].isDigit()) { "number must start with a digit" }
        while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
        return Json.Num(s.substring(start, i).toDouble())
    }

    private fun string(): String {
        require(s[i] == '"') { "expected a string" }
        i++
        val out = StringBuilder()
        while (true) {
            require(i < s.length) { "unterminated string" }
            when (val c = s[i++]) {
                '"' -> return out.toString()
                '\\' -> {
                    val e = s[i++]
                    out.append(
                        when (e) {
                            '"' -> '"'; '\\' -> '\\'; '/' -> '/'; 'b' -> '\b'; 'f' -> '\u000C'
                            'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                            'u' -> { val h = s.substring(i, i + 4); i += 4; h.toInt(16).toChar() }
                            else -> throw IllegalArgumentException("bad escape")
                        }
                    )
                }
                else -> out.append(c)
            }
        }
    }

    private fun arr(): Json {
        i++
        val items = ArrayList<Json>()
        ws()
        if (i < s.length && s[i] == ']') { i++; return Json.Arr(items) }
        while (true) {
            items += value()
            ws()
            require(i < s.length) { "unterminated array" }
            when (s[i++]) {
                ',' -> continue
                ']' -> return Json.Arr(items)
                else -> throw IllegalArgumentException("expected , or ]")
            }
        }
    }

    private fun obj(): Json {
        i++
        val fields = ArrayList<Pair<String, Json>>()
        ws()
        if (i < s.length && s[i] == '}') { i++; return Json.Obj(fields) }
        while (true) {
            ws()
            val k = string()
            ws()
            require(i < s.length && s[i] == ':') { "expected :" }
            i++
            fields += k to value()
            ws()
            require(i < s.length) { "unterminated object" }
            when (s[i++]) {
                ',' -> continue
                '}' -> return Json.Obj(fields)
                else -> throw IllegalArgumentException("expected , or }")
            }
        }
    }
}
