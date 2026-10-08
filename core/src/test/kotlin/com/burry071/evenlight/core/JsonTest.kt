package com.burry071.evenlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonTest {
    @Test fun roundTripsEveryScalarShape() {
        val obj = Json.Obj(
            listOf(
                "a" to Json.Str("b"), "n" to Json.Num(1.5), "t" to Json.Bool(true),
                "list" to Json.Arr(listOf(Json.Str("#AABBCC"), Json.Str("#010203"))),
                "empty" to Json.Arr(emptyList())
            )
        )
        assertEquals(obj, Json.tryParse(Json.encode(obj)))
    }

    @Test fun escapesWhatWouldOtherwiseBreakAString() {
        val s = Json.Str("quote \" back\\slash newline\n tab\t")
        assertEquals(s, Json.tryParse(Json.encode(s)))
    }

    @Test fun rejectsTrailingGarbageRatherThanSilentlyIgnoringIt() { assertNull(Json.tryParse("{\"a\":1} oops")) }

    @Test fun rejectsMalformedInput() {
        assertNull(Json.tryParse("{"))
        assertNull(Json.tryParse("[1,]"))
    }

    @Test fun nullIsNotSilentlyAnEmptyString() {
        // This codec has no Json.Null case, so `null` has nowhere honest to land. Mapping it to Str("") - what
        // an earlier draft of this plan did - made a record file containing `"wording": null` decode into a
        // record with an empty wording instead of failing, which is invented data at the far edge of the
        // pipeline. `null` is therefore not in this grammar: it falls through to number(), consumes no
        // characters, and throws. Python's json.load reads such a file happily; the app must not.
        assertNull(Json.tryParse("null"))
        assertNull(Json.tryParse("{\"wording\":null}"))
    }

    @Test fun rejectsAPlusPrefixedNumber() {
        // `+1.5` is not JSON, and Python's json.load rejects it. The parser's job is to agree with the reader
        // that shares these files, so a leading plus is a malformed number, not a positive one.
        assertNull(Json.tryParse("+1.5"))
        assertEquals(Json.Num(1.5), Json.tryParse("1.5"))
    }

    @Test fun controlCharactersSurviveARoundTrip() {
        // The escape branch that no other test reaches: every named escape (newline, tab, carriage return) has
        // its own case, so a string carrying a bare control character used the four-hex-digit branch with no
        // coverage at all. Checked on this JDK before keeping the format string there: under an ar-EG locale
        // `%04d` printed non-ASCII digits while `%04x` printed ASCII `00ff`, so hex output is not
        // locale-parameterised the way decimal output is - this escape stays a pure function of its input.
        // The branch is load-bearing, so it is now pinned.
        val s = Json.Str("a\u0001b\u001Fc")
        assertEquals(s, Json.tryParse(Json.encode(s)))
        assertEquals("\"a\\u0001b\\u001fc\"", Json.encode(s))
    }
}
