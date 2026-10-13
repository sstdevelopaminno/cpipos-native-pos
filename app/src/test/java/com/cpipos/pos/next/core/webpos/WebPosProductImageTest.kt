package com.cpipos.pos.next.core.webpos

import com.cpipos.pos.next.core.pos.Satang
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebPosProductImageTest {
    @Test fun buildsPublicProductMediaUrlFromObjectPath() {
        assertEquals(
            "https://supabase.example.test/storage/v1/object/public/product-media/tenant/branch/product/thumb.webp",
            publicProductMediaUrl("tenant/branch/product/thumb.webp", "https://supabase.example.test/")
        )
    }

    @Test fun trimsLeadingSlashFromObjectPath() {
        assertEquals(
            "https://supabase.example.test/storage/v1/object/public/product-media/tenant/product/display.jpg",
            publicProductMediaUrl("/tenant/product/display.jpg", "https://supabase.example.test")
        )
    }

    @Test fun usesCanonicalThumbnailForMatchingProductOnly() {
        val products = listOf(
            WebPosProduct("a", "Coffee", "CF", "Drinks", Satang.of(5000L)),
            WebPosProduct("b", "Tea", "TEA", "Drinks", Satang.of(4500L))
        )
        val assets = JSONObject(
            """{"a":{"thumbnail_url":"https://media.example.test/a-thumb.webp","image_url":"https://media.example.test/a.jpg"},"other-tenant-item":{"thumbnail_url":"https://media.example.test/secret.jpg"}}"""
        )
        val merged = mergeCanonicalProductMedia(products, assets)
        assertEquals("https://media.example.test/a-thumb.webp", merged[0].imageUrl)
        assertNull(merged[1].imageUrl)
        assertEquals(products.map { it.id }, merged.map { it.id })
        assertEquals(products.map { it.price }, merged.map { it.price })
    }

    @Test fun fallsBackToDisplayOrExistingHttpsUrl() {
        val products = listOf(
            WebPosProduct("a", "A", "", "", Satang.ZERO),
            WebPosProduct("b", "B", "", "", Satang.ZERO, "https://assets.example.test/b.webp"),
            WebPosProduct("c", "C", "", "", Satang.ZERO, "http://unsafe.example.test/c.png")
        )
        val assets = JSONObject("""{"a":{"image_url":"https://media.example.test/a.webp"},"b":{"thumbnail_url":"http://unsafe.example.test/b.png"}}""")
        val merged = mergeCanonicalProductMedia(products, assets)
        assertEquals("https://media.example.test/a.webp", merged[0].imageUrl)
        assertEquals("https://assets.example.test/b.webp", merged[1].imageUrl)
        assertNull(merged[2].imageUrl)
    }

    @Test fun rejectsPathsWithoutHttpsSupabaseUrl() {
        assertNull(publicProductMediaUrl("tenant/a.jpg", ""))
        assertNull(publicProductMediaUrl("tenant/a.jpg", "http://supabase.example.test"))
        assertNull(publicProductMediaUrl(" ", "https://supabase.example.test"))
    }
}
