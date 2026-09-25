package com.cpipos.pos.next.core.supabase.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class TenantReadModel(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("package_id") val packageId: String? = null,
    @SerialName("is_active") val isActive: Boolean
)

@Serializable
data class BranchReadModel(
    val id: String,
    @SerialName("tenant_id") val tenantId: String,
    val code: String,
    val name: String,
    val address: String? = null,
    @SerialName("is_active") val isActive: Boolean
)

@Serializable
data class ProductReadModel(
    val id: String,
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("branch_id") val branchId: String,
    val sku: String,
    val name: String,
    val category: String,
    @Serializable(with = PostgresNumericAsString::class) val price: String,
    @SerialName("is_active") val isActive: Boolean
)

@Serializable
data class SubscriptionPackageReadModel(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("max_branches") val maxBranches: Int? = null,
    @SerialName("max_devices") val maxDevices: Int? = null
)

@Serializable
data class TenantFeatureReadModel(
    val id: String,
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("branch_id") val branchId: String? = null,
    @SerialName("feature_code") val featureCode: String,
    @SerialName("is_enabled") val isEnabled: Boolean,
    val source: String
)

/** Preserve exact Postgres numeric text; do not round money through Double. */
object PostgresNumericAsString : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "PostgresNumericAsString", PrimitiveKind.STRING
    )
    override fun deserialize(decoder: Decoder): String =
        if (decoder is JsonDecoder) decoder.decodeJsonElement().jsonPrimitive.content
        else decoder.decodeString()
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}
