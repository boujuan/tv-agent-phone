package com.boujuan.tvagent.phone.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** An agent this phone is paired with. Its token is kept sealed (see [KeystoreSealer]); everything else is plain. */
@Serializable
data class SavedAgent(
    val id: String,                 // this phone's own id for it
    val name: String,               // the computer's name
    val urls: List<String>,         // its phone listener's addresses, in the order to try them
    val pin: String,                // its certificate's SHA-256
    val streamPort: Int,
    val deviceId: String,           // this phone's id there
    val deviceName: String,
    val version: String = "",
    val sealedToken: String,
)

@Serializable
data class AppPrefs(
    val theme: String = "system",   // "system", "light", "dark"
    val haptics: Boolean = true,
    val dynamicColor: Boolean = true,
)

/** Seals and opens secrets at rest. */
interface Sealer {
    fun seal(plain: String): String
    fun open(sealed: String): String?
}

/**
 * AES-256-GCM with a key that lives in the Android Keystore (hardware-backed where the phone has it) and can't be
 * exported: a copy of the app's files (a backup, a rooted read) doesn't carry usable tokens.
 */
class KeystoreSealer(private val alias: String = "tv-agent-tokens") : Sealer {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    override fun seal(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    override fun open(sealed: String): String? = runCatching {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()
}

class AgentStore(private val data: DataStore<Preferences>, private val sealer: Sealer) {
    private val agentsKey = stringPreferencesKey("agents")
    private val selectedKey = stringPreferencesKey("selected")
    private val prefsKey = stringPreferencesKey("prefs")
    private val agentList = ListSerializer(SavedAgent.serializer())

    val agents: Flow<List<SavedAgent>> = data.data.map { decode(it) }
    val selected: Flow<SavedAgent?> = data.data.map { p ->
        val all = decode(p)
        all.firstOrNull { it.id == p[selectedKey] } ?: all.firstOrNull()
    }
    val prefs: Flow<AppPrefs> = data.data.map { p ->
        p[prefsKey]?.let { runCatching { json.decodeFromString(AppPrefs.serializer(), it) }.getOrNull() } ?: AppPrefs()
    }

    private fun decode(p: Preferences): List<SavedAgent> =
        p[agentsKey]?.let { runCatching { json.decodeFromString(agentList, it) }.getOrNull() } ?: emptyList()

    fun token(agent: SavedAgent): String? = sealer.open(agent.sealedToken)

    /** A newly paired agent (replacing an earlier pairing with the same computer and certificate), selected. */
    suspend fun add(agent: SavedAgent, token: String): SavedAgent {
        val saved = agent.copy(sealedToken = withContext(Dispatchers.IO) { sealer.seal(token) })   // the Keystore can be slow
        data.edit { p ->
            val kept = decode(p).filterNot { it.pin == agent.pin && it.name == agent.name }
            p[agentsKey] = json.encodeToString(agentList, kept + saved)
            p[selectedKey] = saved.id
        }
        return saved
    }

    suspend fun update(agent: SavedAgent) = data.edit { p ->
        p[agentsKey] = json.encodeToString(agentList, decode(p).map { if (it.id == agent.id) agent else it })
    }

    suspend fun remove(id: String) = data.edit { p ->
        p[agentsKey] = json.encodeToString(agentList, decode(p).filterNot { it.id == id })
        if (p[selectedKey] == id) p.remove(selectedKey)
    }

    suspend fun select(id: String) = data.edit { it[selectedKey] = id }

    suspend fun setPrefs(prefs: AppPrefs) = data.edit { it[prefsKey] = json.encodeToString(AppPrefs.serializer(), prefs) }
}
