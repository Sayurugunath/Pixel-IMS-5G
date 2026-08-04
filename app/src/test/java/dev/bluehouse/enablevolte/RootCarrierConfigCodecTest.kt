package dev.bluehouse.enablevolte

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RootCarrierConfigCodecTest {
    @Test
    fun roundTripsEveryCarrierConfigType() {
        assertEquals(true, roundTrip(true))
        assertEquals(42, roundTrip(42))
        assertEquals(9_000_000_000L, roundTrip(9_000_000_000L))
        assertEquals("IMS value: ශ්‍රී ලංකා", roundTrip("IMS value: ශ්‍රී ලංකා"))
        assertArrayEquals(intArrayOf(1, 2, 3), roundTrip(intArrayOf(1, 2, 3)) as IntArray)
        assertArrayEquals(booleanArrayOf(true, false, true), roundTrip(booleanArrayOf(true, false, true)) as BooleanArray)
        assertArrayEquals(longArrayOf(1L, 5L), roundTrip(longArrayOf(1L, 5L)) as LongArray)
        assertArrayEquals(arrayOf("NSA", "SA", "value,with,comma"), roundTrip(arrayOf("NSA", "SA", "value,with,comma")) as Array<*>)
    }

    @Test
    fun rejectsUnknownOrCorruptValues() {
        assertEquals(null, RootCarrierConfigCodec.decode("unknown:value"))
        assertEquals(null, RootCarrierConfigCodec.decode("int:not-a-number"))
    }

    private fun roundTrip(value: Any): Any? =
        RootCarrierConfigCodec.decode(requireNotNull(RootCarrierConfigCodec.encode(value)))
}
