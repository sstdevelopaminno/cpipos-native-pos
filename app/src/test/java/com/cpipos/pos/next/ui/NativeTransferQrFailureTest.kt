package com.cpipos.pos.next.ui

import com.cpipos.pos.next.core.webpos.WebPosApiException
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTransferQrFailureTest {
    @Test
    fun expiredSessionExplainsReauthentication() {
        val text = transferQrFailureMessage(WebPosApiException("session_expired", 401))
        assertTrue(text.contains("เข้าสู่ระบบ"))
        assertFalse(text.contains("POS API:"))
    }

    @Test
    fun invalidPromptPayAccountPointsToPaymentSettings() {
        val text = transferQrFailureMessage(WebPosApiException("promptpay_qr_account_invalid", 422))
        assertTrue(text.contains("พร้อมเพย์"))
        assertTrue(text.contains("ตั้งค่า"))
    }

    @Test
    fun httpErrorsIncludeCodesWithoutLeakingRawHeaders() {
        val text = transferQrFailureMessage(WebPosApiException("unexpected_proxy_code", 502))
        assertTrue(text.contains("unexpected_proxy_code"))
        assertTrue(text.contains("HTTP 502"))
    }

    @Test
    fun networkOutageOffersRetryInstructions() {
        val text = transferQrFailureMessage(IOException("https://server?cookie=secret"))
        assertTrue(text.contains("อินเทอร์เน็ต"))
        assertFalse(text.contains("secret"))
    }
}
