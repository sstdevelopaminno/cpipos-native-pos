package com.cpipos.pos.next.core.supabase

import com.cpipos.pos.next.core.supabase.model.BranchReadModel
import com.cpipos.pos.next.core.supabase.model.ProductReadModel
import com.cpipos.pos.next.core.supabase.model.SubscriptionPackageReadModel
import com.cpipos.pos.next.core.supabase.model.TenantFeatureReadModel
import com.cpipos.pos.next.core.supabase.model.TenantReadModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

/**
 * Phase 1 repository: SELECT-only access to CpiPOS-001.
 *
 * No insert/update/delete/RPC methods are intentionally exposed here.
 * Tenant and branch identifiers are explicit parameters to reduce the risk
 * of accidental cross-tenant reads in application code. Database RLS remains
 * the authoritative security boundary.
 */
class ReadonlyCatalogRepository(
    private val client: SupabaseClient
) {
    suspend fun loadTenant(tenantId: String): TenantReadModel? =
        client.from("tenants")
            .select {
                filter {
                    eq("id", tenantId)
                    eq("is_active", true)
                }
            }
            .decodeList<TenantReadModel>()
            .firstOrNull()

    suspend fun loadBranches(tenantId: String): List<BranchReadModel> =
        client.from("branches")
            .select {
                filter {
                    eq("tenant_id", tenantId)
                    eq("is_active", true)
                }
            }
            .decodeList()

    suspend fun loadProducts(
        tenantId: String,
        branchId: String
    ): List<ProductReadModel> =
        client.from("products")
            .select {
                filter {
                    eq("tenant_id", tenantId)
                    eq("branch_id", branchId)
                    eq("is_active", true)
                }
            }
            .decodeList()

    suspend fun loadSubscriptionPackage(packageId: String): SubscriptionPackageReadModel? =
        client.from("subscription_packages")
            .select {
                filter {
                    eq("id", packageId)
                    eq("is_active", true)
                }
            }
            .decodeList<SubscriptionPackageReadModel>()
            .firstOrNull()

    suspend fun loadTenantFeatures(
        tenantId: String,
        branchId: String? = null
    ): List<TenantFeatureReadModel> =
        client.from("tenant_feature_subscriptions")
            .select {
                filter {
                    eq("tenant_id", tenantId)
                    eq("is_enabled", true)
                    if (branchId != null) {
                        eq("branch_id", branchId)
                    }
                }
            }
            .decodeList()
}
