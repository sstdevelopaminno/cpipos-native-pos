package com.cpipos.pos.next.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The producer repo may build an unsigned QA binary; it must never sign or publish Stable. */
class NativeQaUnsignedBuildContractTest {
    @Test fun qaBuildKeepsSigningAuthorityInCentralCpiposRepository() {
        val workflow = File("../.github/workflows/unsigned-native-mobile-213-once.yml").readText()
        assertTrue(workflow.contains("35867cb81edc47ae865a806acd3c820bd5dfbb65"))
        assertTrue(workflow.contains(":app:testDebugUnitTest :app:assembleRelease"))
        assertTrue(workflow.contains("CpIPOS-Native-Mobile-2.1.3-unsigned.apk"))
        assertTrue(workflow.contains("actions/upload-artifact@v4"))
        assertFalse(workflow.contains("gh release create"))
        assertFalse(workflow.contains("ANDROID_SIGNING_KEYSTORE_BASE64"))
        assertFalse(workflow.contains("publish_stable=true"))
    }
}
