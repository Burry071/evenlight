package com.burry071.evenlight.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class SampleSizeTest {
    @Test fun aTwelveMegapixelFrameIsDecodedAtRoughlyOneThousandPixelsWide() {
        // 4032/4 = 1008, which is under the edge, while 2 would still be 2016 and cost ~12 MB of ints.
        assertEquals(4, sampleSize(4032, 3024))
    }

    @Test fun aFrameAlreadyUnderTheEdgeIsNotThrownAway() {
        // The fixtures are 893 tall and Task 10 compares band-for-band; downscaling one would sample pixels that
        // the golden file never had.
        assertEquals(1, sampleSize(893, 893))
        assertEquals(1, sampleSize(1024, 1024))
        assertEquals(2, sampleSize(1025, 1025))
    }
}
