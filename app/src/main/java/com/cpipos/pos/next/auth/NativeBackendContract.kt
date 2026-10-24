package com.cpipos.pos.next.auth

/**
 * Server contract for future CpiPOS-001 integration; NOT a mock authentication bypass.
 *
 * A trusted Edge Function/RPC must:
 *  1. rate-limit per store/device/IP without identifying a valid store to attackers;
 *  2. verify Store Code + employee PIN only server-side, never return PIN hashes;
 *  3. resolve allowed tenant, active branch, employee role and registered device;
 *  4. issue/validate Supabase Auth user JWT bound to auth.uid() and existing RLS;
 *  5. enforce branch_login_policies and package entitlement;
 *  6. return no elevated/secret key or database credentials.
 *
 * No production implementation is wired in MainActivity until these conditions
 * are verified with isolated test users and RLS negative tests.
 */
data class NativeBootstrap(
    val tenantId: String,
    val tenantName: String,
    val branchId: String,
    val branchName: String,
    val employeeId: String,
    val deviceId: String,
    val sessionId: String,
    val expiresAtMs: Long,
    val featureCodes: Set<String>
) {
    init {
        require(listOf(tenantId, branchId, employeeId, deviceId, sessionId).all(String::isNotBlank))
        require(expiresAtMs > 0)
    }
}

interface NativeBootstrapGateway {
    suspend fun login(storeCode: String, employeePin: String, branchId: String, deviceId: String): NativeBootstrap
    suspend fun readProducts(session: NativeBootstrap): List<NativeCatalogItem>
}

data class NativeCatalogItem(
    val id: String,
    val name: String,
    val sku: String,
    /** Exact decimal Baht from Postgres numeric; parse with Satang.fromBaht. */
    val priceBaht: String,
    val branchId: String
)
