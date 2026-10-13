package com.cpipos.pos.next.core.webpos

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

    @Test fun rejectsPathsWithoutHttpsSupabaseUrl() {
        assertNull(publicProductMediaUrl("tenant/a.jpg", ""))
        assertNull(publicProductMediaUrl("tenant/a.jpg", "http://supabase.example.test"))
        assertNull(publicProductMediaUrl(" ", "https://supabase.example.test"))
    }
}
