package com.saleh.alazzi.grocery.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.saleh.alazzi.grocery.data.local.entity.UserEntity
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object UserPermissions {
    const val CAN_SELL = "CAN_SELL"
    const val CAN_BUY = "CAN_BUY"
    const val CAN_VIEW_CUSTOMERS = "CAN_VIEW_CUSTOMERS"
    const val CAN_VIEW_SUPPLIERS = "CAN_VIEW_SUPPLIERS"
    const val CAN_RECEIVE_PAYMENT = "CAN_RECEIVE_PAYMENT"
    const val CAN_VIEW_INVENTORY = "CAN_VIEW_INVENTORY"
    const val CAN_EDIT_INVENTORY = "CAN_EDIT_INVENTORY"
    const val CAN_VIEW_FINANCE = "CAN_VIEW_FINANCE"
    const val CAN_VIEW_REPORTS = "CAN_VIEW_REPORTS"
    const val CAN_PRINT_SHARE = "CAN_PRINT_SHARE"
    const val CAN_EDIT_OPERATIONS = "CAN_EDIT_OPERATIONS"

    val selectablePermissions: List<Pair<String, String>> = listOf(
        CAN_SELL to "إنشاء فواتير المبيعات ونقطة البيع",
        CAN_BUY to "فواتير المشتريات واستلام البضائع",
        CAN_VIEW_CUSTOMERS to "عرض العملاء وكشوف حساباتهم",
        CAN_VIEW_SUPPLIERS to "عرض الموردين وكشوف حساباتهم",
        CAN_RECEIVE_PAYMENT to "تسجيل السندات وسداد الديون",
        CAN_VIEW_INVENTORY to "عرض الأصناف والمخزون",
        CAN_EDIT_INVENTORY to "إضافة وتعديل أصناف المخزون",
        CAN_VIEW_FINANCE to "الصندوق والمصروفات والحوالات",
        CAN_VIEW_REPORTS to "التقارير المالية وحركة الأصناف",
        CAN_PRINT_SHARE to "مركز الطباعة والتصدير والمشاركة",
        CAN_EDIT_OPERATIONS to "تعديل العمليات وعكس القيود"
    )

    val cashierDefaultPermissions: Set<String> = setOf(
        CAN_SELL,
        CAN_VIEW_CUSTOMERS,
        CAN_RECEIVE_PAYMENT,
        CAN_VIEW_INVENTORY,
        CAN_PRINT_SHARE
    )

    val limitedDefaultPermissions: Set<String> = setOf(
        CAN_VIEW_INVENTORY,
        CAN_VIEW_CUSTOMERS,
        CAN_PRINT_SHARE
    )

    fun roleArabicLabel(role: String): String = when (role) {
        "MANAGER" -> "مدير عام"
        "CASHIER" -> "كاشير"
        "LIMITED" -> "مستخدم محدود"
        else -> role
    }
}

fun UserEntity.grantedPermissions(): Set<String> = when (role) {
    "MANAGER" -> UserPermissions.selectablePermissions.map { it.first }.toSet()
    "CASHIER" -> if (!permissions.isNullOrBlank()) {
        permissions.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    } else {
        UserPermissions.cashierDefaultPermissions
    }
    "LIMITED" -> if (!permissions.isNullOrBlank()) {
        permissions.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    } else {
        UserPermissions.limitedDefaultPermissions
    }
    else -> emptySet()
}

fun UserEntity.hasPermission(permission: String): Boolean =
    role == "MANAGER" || permission in grantedPermissions()

data class PersistedSessionPayload(
    val sessionId: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val role: String,
    val permissionsCsv: String? = null,
    val issuedAt: Long,
    val expiresAt: Long,
    val lastRoute: String = "dashboard"
)

private data class EncryptedSessionEnvelope(
    val ivBase64: String,
    val ciphertextBase64: String
)

class SessionManager(private val context: Context) {
    companion object {
        const val DEFAULT_TIMEOUT_HOURS = 72
        private const val KEY_ALIAS = "alazzi_session_aes_key_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val SESSION_FILE = "alazzi_secure_session.enc"
        private const val FALLBACK_KEY_FILE = ".alazzi_session_k.bin"
        private const val IV_BYTES = 12
    }

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val random = SecureRandom()

    fun saveSession(
        user: UserEntity,
        lastRoute: String = "dashboard",
        timeoutHours: Int = DEFAULT_TIMEOUT_HOURS,
        now: Long = System.currentTimeMillis()
    ): PersistedSessionPayload {
        val safeHours = timeoutHours.coerceIn(1, 720)
        val payload = PersistedSessionPayload(
            sessionId = UUID.randomUUID().toString(),
            userId = user.id,
            username = user.username,
            displayName = user.displayName,
            role = user.role,
            permissionsCsv = user.permissions,
            issuedAt = now,
            expiresAt = now + safeHours * 3_600_000L,
            lastRoute = lastRoute.ifBlank { "dashboard" }
        )
        writeEncryptedPayload(payload)
        return payload
    }

    fun updateLastRoute(
        route: String,
        timeoutHours: Int = DEFAULT_TIMEOUT_HOURS,
        now: Long = System.currentTimeMillis()
    ) {
        val current = loadValidSession(now) ?: return
        val safeHours = timeoutHours.coerceIn(1, 720)
        writeEncryptedPayload(
            current.copy(
                lastRoute = route.ifBlank { "dashboard" },
                expiresAt = maxOf(current.expiresAt, now + safeHours * 3_600_000L)
            )
        )
    }

    fun loadValidSession(now: Long = System.currentTimeMillis()): PersistedSessionPayload? {
        val file = sessionFile()
        if (!file.exists()) return null
        val payload = runCatching {
            val raw = file.readText(Charsets.UTF_8)
            val envelope = gson.fromJson(raw, EncryptedSessionEnvelope::class.java) ?: return null
            val iv = Base64.getDecoder().decode(envelope.ivBase64)
            val cipherBytes = Base64.getDecoder().decode(envelope.ciphertextBase64)
            if (iv.size != IV_BYTES) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(128, iv))
            val plain = String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
            gson.fromJson(plain, PersistedSessionPayload::class.java)
        }.getOrNull() ?: run {
            clearSession()
            return null
        }
        if (payload.userId.isBlank() || payload.expiresAt <= now) {
            clearSession()
            return null
        }
        return payload
    }

    fun clearSession() {
        runCatching { sessionFile().delete() }
    }

    private fun writeEncryptedPayload(payload: PersistedSessionPayload) {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(gson.toJson(payload).toByteArray(Charsets.UTF_8))
        val envelope = EncryptedSessionEnvelope(
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            ciphertextBase64 = Base64.getEncoder().encodeToString(encrypted)
        )
        sessionFile().writeText(gson.toJson(envelope), Charsets.UTF_8)
    }

    private fun sessionFile(): File = File(context.filesDir, SESSION_FILE)

    private fun getOrCreateAesKey(): SecretKey {
        val keystoreKey = runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) {
                existing
            } else {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                generator.init(spec)
                generator.generateKey()
            }
        }.getOrNull()
        if (keystoreKey != null) return keystoreKey

        // App-private fallback key for headless JVM/Robolectric test environments without AndroidKeyStore.
        val fallbackFile = File(context.filesDir, FALLBACK_KEY_FILE)
        val keyBytes = if (fallbackFile.exists() && fallbackFile.length() == 32L) {
            fallbackFile.readBytes()
        } else {
            val generated = ByteArray(32).also(random::nextBytes)
            val hashed = MessageDigest.getInstance("SHA-256")
                .digest(generated + context.packageName.toByteArray(Charsets.UTF_8))
            fallbackFile.writeBytes(hashed)
            hashed
        }
        return SecretKeySpec(keyBytes, "AES")
    }
}
