package com.cpipos.pos.next.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class NativeSalesWindowTest {
    private val date = LocalDate.of(2026, 10, 23)

    @Test fun dayWindowUsesBangkokSelectedDay() {
        val range = nativeSalesWindow("day", date, date.minusDays(5), date)
        assertEquals(date, range.start)
        assertEquals(date, range.end)
    }

    @Test fun monthHandlesAllDaysIncludingLeapFebruary() {
        val leap = LocalDate.of(2028, 2, 17)
        val range = nativeSalesWindow("month", leap, date, date)
        assertEquals(LocalDate.of(2028, 2, 1), range.start)
        assertEquals(LocalDate.of(2028, 2, 29), range.end)
    }

    @Test fun yearWindowIncludesEntireCalendarYear() {
        val range = nativeSalesWindow("year", date, date, date)
        assertEquals(LocalDate.of(2026, 1, 1), range.start)
        assertEquals(LocalDate.of(2026, 12, 31), range.end)
    }

    @Test fun customWindowKeepsExplicitInclusiveDates() {
        val from = LocalDate.of(2025, 5, 14)
        val to = LocalDate.of(2026, 10, 23)
        val range = nativeSalesWindow("custom", date, from, to)
        assertEquals(from, range.start)
        assertEquals(to, range.end)
    }
}
