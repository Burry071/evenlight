package com.burry071.evenlight.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        // This codec has no Json.Null case, so `null` has nowhere honest to land - and a record file holding
        // `"wording": null` must not read back as a record with an empty wording. `null` is therefore absent
        // from this grammar: it falls through to number(), consumes no characters, and throws. Python's
        // json.load reads such a file happily; the app cannot tell the two apart, so it must refuse.
        assertNull(Json.tryParse("null"))
        assertNull(Json.tryParse("{\"wording\":null}"))
    }

    @Test fun rejectsAPlusPrefixedNumber() {
        // `+1.5` is not JSON, and Python's json.load rejects it. The parser's job is to agree with the reader
        // that shares these files, so a leading plus is a malformed number, not a positive one.
        assertNull(Json.tryParse("+1.5"))
        assertEquals(Json.Num(1.5), Json.tryParse("1.5"))
    }

    @Test fun rejectsLeadingZerosAndTrailingPointsTheWayPythonsReaderDoes() {
        // Finding 2.8, same argument as rejectsAPlusPrefixedNumber: the file's other reader is json.load, and
        // it refuses both shapes - `json.loads("01")` and `json.loads("1.")` raise - while the old scan
        // accepted them ("012" even parsed as 12). `0` itself, `0.5`, `0e1` and `10` stay legal in both
        // languages, so the tightening must not cost those.
        assertNull(Json.tryParse("01"))
        assertNull(Json.tryParse("-07"))
        assertNull(Json.tryParse("1."))
        assertNull(Json.tryParse("{\"attempt\":02}"))
        assertNull(Json.tryParse("{\"mean_luminance\":0.117.}"))
        assertEquals(Json.Num(0.0), Json.tryParse("0"))
        assertEquals(Json.Num(0.5), Json.tryParse("0.5"))
        assertEquals(Json.Num(0.0), Json.tryParse("0e1"))
        assertEquals(Json.Num(10.0), Json.tryParse("10"))
    }

    @Test fun controlCharactersSurviveARoundTrip() {
        // Python's strict reader refuses a raw control character inside a string, so escaping them is what
        // keeps these files loadable. Named escapes own newline/tab/return, which leaves the four-hex-digit
        // branch otherwise unexercised. `%04x` is not locale-parameterised the way `%04d` is, so its output
        // stays a pure function of the input.
        val s = Json.Str("a\u0001b\u001Fc")
        assertEquals(s, Json.tryParse(Json.encode(s)))
        assertEquals("\"a\\u0001b\\u001fc\"", Json.encode(s))
    }

    @Test fun readsAFileWithSurroundingWhitespace() {
        // A record file another tool terminated with a newline, or padded, is still ours to read. Task 8
        // depends on this: it reads whatever came off the disk, not what the encoder wrote.
        assertNotNull(Json.tryParse(" {\"a\":1}\r\n"))
        assertEquals(Json.Obj(listOf("a" to Json.Num(1.0))), Json.tryParse("\t{\"a\":1} "))
    }

    @Test fun namedEscapesHaveAFixedWireForm() {
        // The bytes on disk are the contract with Python, so the escape spellings are pinned rather than
        // round-tripped - a round trip would still pass if a named escape quietly became a \uXXXX one.
        val odd = "q\" s\\ n\n t\t r\r"
        assertEquals("\"q\\\" s\\\\ n\\n t\\t r\\r\"", Json.encode(Json.Str(odd)))
    }

    @Test fun aNearMissLiteralIsRefused() {
        // `literal()` matches a prefix, so without the check `[tru,]` would read as `[true,]`. A truncated
        // word is corruption, not a value.
        assertNull(Json.tryParse("[tru,]"))
        assertNull(Json.tryParse("[fals,]"))
    }

    @Test fun emptyContainersParseAsEmpty() {
        // The encoder writes `[]` for an empty list; if the parser stopped accepting it, a field that went
        // empty would become an unreadable file instead of an empty list.
        assertEquals(Json.Obj(emptyList()), Json.tryParse("{}"))
        assertEquals(Json.Arr(emptyList()), Json.tryParse("[]"))
    }
}
