package com.piggytrade.piggytrade.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.piggytrade.piggytrade.BuildConfig
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferenceManager(context: Context) {
    private val gson = Gson()

    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val encPrefs = EncryptedSharedPreferences.create(
            "piggy_prefs_encrypted",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // One-time migration from old unencrypted prefs
        migrateFromUnencrypted(context, encPrefs)

        encPrefs
    } catch (e: Exception) {
        // Fallback to standard prefs if EncryptedSharedPreferences fails
        // (e.g. on devices without hardware keystore support)
        Log.e("PreferenceManager", "EncryptedSharedPreferences failed, using standard prefs: ${e.message}")
        context.getSharedPreferences("piggy_prefs", Context.MODE_PRIVATE)
    }

    private fun migrateFromUnencrypted(context: Context, encryptedPrefs: SharedPreferences) {
        val oldPrefs = context.getSharedPreferences("piggy_prefs", Context.MODE_PRIVATE)
        val oldAll = oldPrefs.all
        if (oldAll.isEmpty()) return

        // Only migrate if encrypted prefs are empty (first launch after update)
        if (encryptedPrefs.all.isNotEmpty()) return

        if (BuildConfig.DEBUG) Log.i("PreferenceManager", "Migrating ${oldAll.size} entries from unencrypted to encrypted prefs")
        val editor = encryptedPrefs.edit()
        for ((key, value) in oldAll) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
            }
        }
        editor.apply()

        // Clear old unencrypted prefs after successful migration
        oldPrefs.edit().clear().apply()
        if (BuildConfig.DEBUG) Log.i("PreferenceManager", "Migration complete, old prefs cleared")
    }

    companion object {
        private const val KEY_WALLETS = "wallets"
        private const val KEY_NODES = "nodes"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_SELECTED_NODE = "selected_node"
        private const val KEY_SELECTED_WALLET = "selected_wallet"
        const val KEY_NUM_FAVORITES = "num_favorites"
    }

    fun saveWallets(wallets: Map<String, Any>) {
        prefs.edit().putString(KEY_WALLETS, gson.toJson(wallets)).apply()
    }

    fun loadWallets(): Map<String, Any> {
        val json = prefs.getString(KEY_WALLETS, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveNodes(nodes: Map<String, Any>) {
        prefs.edit().putString(KEY_NODES, gson.toJson(nodes)).apply()
    }

    fun loadNodes(): Map<String, Any> {
        val json = prefs.getString(KEY_NODES, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveFavorites(favorites: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, gson.toJson(favorites)).apply()
    }

    fun loadFavorites(default: List<String>): List<String> {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return default
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveSettings(settings: Map<String, Any>) {
        prefs.edit().putString(KEY_SETTINGS, gson.toJson(settings)).apply()
    }

    fun loadSettings(): Map<String, Any> {
        val json = prefs.getString(KEY_SETTINGS, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        return gson.fromJson(json, type)
    }

    var selectedNode: String
        get() = prefs.getString(KEY_SELECTED_NODE, "Select Node") ?: "Select Node"
        set(value) = prefs.edit().putString(KEY_SELECTED_NODE, value).apply()

    var selectedWallet: String
        get() = prefs.getString(KEY_SELECTED_WALLET, "Select Wallet") ?: "Select Wallet"
        set(value) = prefs.edit().putString(KEY_SELECTED_WALLET, value).apply()

    fun saveTrades(trades: List<Map<String, Any>>) {
        prefs.edit().putString("trades", gson.toJson(trades)).apply()
    }

    fun loadTrades(): List<Map<String, Any>> {
        val json = prefs.getString("trades", null) ?: return emptyList()
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        return gson.fromJson(json, type)
    }

    /**
     * Persist per-wallet address selection config (which addresses are active + change address).
     * Stored separately from encrypted wallet data so it can be changed without decryption.
     */
    fun saveWalletAddressConfig(walletName: String, selectedAddresses: Set<String>, changeAddress: String) {
        val config = mapOf(
            "selected" to selectedAddresses.toList(),
            "change" to changeAddress
        )
        prefs.edit().putString("addr_config_$walletName", gson.toJson(config)).apply()
    }

    /**
     * Load per-wallet address selection config.
     * Returns (selectedAddresses, changeAddress). Empty set + empty string if not configured.
     */
    fun loadWalletAddressConfig(walletName: String): Pair<Set<String>, String> {
        val json = prefs.getString("addr_config_$walletName", null) ?: return Pair(emptySet(), "")
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val config: Map<String, Any> = gson.fromJson(json, type)
            @Suppress("UNCHECKED_CAST")
            val selected = (config["selected"] as? List<String>)?.toSet() ?: emptySet()
            val change = config["change"] as? String ?: ""
            Pair(selected, change)
        } catch (e: Exception) {
            Pair(emptySet(), "")
        }
    }

    // ─── SAVED EXPLORER ADDRESSES ────────────────────────────────────────

    fun saveExplorerAddresses(addresses: Map<String, String>) {
        prefs.edit().putString("explorer_addresses", gson.toJson(addresses)).apply()
    }

    fun loadExplorerAddresses(): Map<String, String> {
        val json = prefs.getString("explorer_addresses", null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
