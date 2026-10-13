package com.cpipos.pos.next.core.supabase

import com.cpipos.pos.next.auth.NativeBootstrap
import com.cpipos.pos.next.auth.NativeCatalogItem
import com.cpipos.pos.next.core.pos.Satang
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth

data class NativeCatalogSnapshot(
    val tenantName: String,
    val branchName: String,
    val items: List<NativeCatalogItem>,
    val enabledFeatures: Set<String>
)

/**
 * Read-only path for after a trusted Native auth exchange imports a real Supabase
 * user session. No anonymous catalog fallback and no service-role key.
 */
class AuthenticatedCatalogBootstrap(private val client: SupabaseClient) {
    suspend fun fetch(session: NativeBootstrap): NativeCatalogSnapshot {
        check(System.currentTimeMillis() < session.expiresAtMs) { "Session expired" }
        val authUserId = client.auth.currentSessionOrNull()?.user?.id
        check(authUserId != null && authUserId == session.employeeId) {
            "No Supabase user session matching the verified employee"
        }

        val repository = ReadonlyCatalogRepository(client)
        val tenant = repository.loadTenant(session.tenantId)
            ?: error("Active tenant not visible to authenticated user")
        val branch = repository.loadBranches(session.tenantId)
            .firstOrNull { it.id == session.branchId && it.tenantId == session.tenantId }
            ?: error("Selected branch not visible to authenticated user")
        val products = repository.loadProducts(session.tenantId, session.branchId)
            .filter { it.tenantId == session.tenantId && it.branchId == session.branchId }
            .map { product ->
                // Postgres numeric must not pass through Double/Float to cash logic.
                Satang.fromBaht(product.price)
                NativeCatalogItem(
                    id = product.id,
                    name = product.name,
                    sku = product.sku,
                    priceBaht = product.price,
                    branchId = product.branchId
                )
            }
        val features = repository.loadTenantFeatures(session.tenantId)
            .filter { it.branchId == null || it.branchId == session.branchId }
            .map { it.featureCode }.toSet()
        return NativeCatalogSnapshot(
            tenantName = tenant.displayName ?: tenant.name,
            branchName = branch.name,
            items = products,
            enabledFeatures = features
        )
    }
}
