package com.cpipos.pos.next.core.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptItemNotesTest {
    @Test
    fun encodedThaiDetailRendersWithoutInternalMarker() {
        val raw = "มาม่า instant\n[[CPIPOS_ITEM_DETAIL]]:%E0%B9%84%E0%B8%A1%E0%B9%88%E0%B8%9C%E0%B8%B1%E0%B8%81"
        val printable = printableReceiptItemNotes(raw)
        assertEquals("มาม่า instant\nรายละเอียด: ไม่ผัก", printable)
        assertFalse(printable!!.contains("[[CPIPOS_ITEM_DETAIL]]"))
    }

    @Test
    fun legacyNotesRemainReadableAndBlankNotesStayAbsent() {
        assertEquals("ไม่ใส่ถั่วงอก", printableReceiptItemNotes("ไม่ใส่ถั่วงอก"))
        assertNull(printableReceiptItemNotes(null))
        assertNull(printableReceiptItemNotes(""))
        assertEquals("มาม่า instant", printableReceiptItemNotes("มาม่า instant\n[[CPIPOS_ITEM_DETAIL]]:%ZZ"))
    }
}
