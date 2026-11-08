package com.cpipos.pos.next.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCatalogProductPhotoContractTest {
    private fun ui(file: String) = File("src/main/java/com/cpipos/pos/next/ui/$file").readText()
    private fun backend() = File("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt").readText()

    @Test fun productCreateAndEditUseSameCanonicalProductMediaApi() {
        val catalog = ui("NativeCatalogManagementScreen.kt")
        val picker = ui("NativeCatalogPhotoPicker.kt")
        val client = backend()
        assertTrue(catalog.contains("NativeCatalogPhotoPicker("))
        assertTrue(catalog.contains("client.uploadCatalogProductPhoto("))
        assertTrue(catalog.contains("val createdId = client.createCatalogProduct("))
        assertTrue(catalog.contains("client.catalogPhotoUrls("))
        assertTrue(client.contains("/api/pos/product-media/"))
        assertTrue(client.contains("require(session.role in setOf(\"owner\", \"manager\"))"))
        assertTrue(picker.contains("ActivityResultContracts.TakePicture()"))
        assertTrue(picker.contains("ActivityResultContracts.GetContent()"))
    }

    @Test fun imagesAreOptimizedAndCameraUsesScopedFileProvider() {
        val photo = File("src/main/java/com/cpipos/pos/next/core/webpos/NativeCatalogPhotoEncoder.kt").readText()
        val provider = File("src/main/res/xml/native_update_paths.xml").readText()
        assertTrue(photo.contains("DISPLAY_LIMIT = 1_572_864"))
        assertTrue(photo.contains("THUMB_LIMIT = 524_288"))
        assertTrue(photo.contains("ExifInterface("))
        assertTrue(photo.contains("Bitmap.CompressFormat.JPEG"))
        assertTrue(provider.contains("native-product-capture"))
    }
}
