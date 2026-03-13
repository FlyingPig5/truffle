package com.piggytrade.piggytrade.data

import android.content.Context
import android.util.Log
import com.piggytrade.piggytrade.BuildConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.piggytrade.piggytrade.blockchain.TradeMapper
import com.piggytrade.piggytrade.network.NodeClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.InputStreamReader

class TokenRepository(private val context: Context) {
    private val gson = Gson()
    private val cacheFile = File(context.filesDir, "tokens_cache.json")
    private val ergToTokenFile = File(context.filesDir, "erg_to_token.json")
    private val tokenToTokenFile = File(context.filesDir, "token_to_token.json")
    private val whitelistFile = File(context.filesDir, "custom_whitelist.json")

    private val systemWhitelistPids by lazy { loadSystemWhitelistPids() }
    private val systemWhitelistNameMap by lazy { loadSystemWhitelistNameMap() }
    private var customWhitelistPids: MutableSet<String> = loadCustomWhitelistPids().toMutableSet()

    private fun loadSystemWhitelistNameMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        (com.piggytrade.piggytrade.protocol.NetworkConfig.USE_CONFIG["lp_nft"] as? String)?.let { map[it] = "USE" }
        (com.piggytrade.piggytrade.protocol.NetworkConfig.DEXYGOLD_CONFIG["lp_nft"] as? String)?.let { map[it] = "DexyGold" }
        return map
    }

    private fun loadSystemWhitelistPids(): Set<String> {
        val pids = mutableSetOf<String>()
        try {
            val inputStream = context.assets.open("tokens.json")
            val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
            val data: Map<String, Map<String, Any>> = gson.fromJson(InputStreamReader(inputStream), type) ?: emptyMap()
            data.values.forEach { 
                (it["pid"] as? String)?.let { p -> pids.add(p) }
            }
        } catch (e: Exception) {
            Log.e("TokenRepo", "Error loading tokens.json: ${e.message}")
        }
        return pids
    }

    private fun loadCustomWhitelistPids(): Set<String> {
        return try {
            if (!whitelistFile.exists()) return emptySet()
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(whitelistFile.readText(), type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun saveCustomWhitelist(pids: Set<String>) {
        customWhitelistPids = pids.toMutableSet()
        whitelistFile.writeText(gson.toJson(customWhitelistPids))
    }

    fun isPidWhitelisted(pid: String): Boolean {
        // Hardcoded Official PIDs
        val isHardcoded = pid == com.piggytrade.piggytrade.protocol.NetworkConfig.USE_CONFIG["lp_nft"] ||
                         pid == com.piggytrade.piggytrade.protocol.NetworkConfig.DEXYGOLD_CONFIG["lp_nft"]
        
        return isHardcoded || systemWhitelistPids.contains(pid) || customWhitelistPids.contains(pid)
    }

    fun isSystemVerified(pid: String): Boolean {
        val usePid = com.piggytrade.piggytrade.protocol.NetworkConfig.USE_CONFIG["pid"] as? String
        val dexyPid = com.piggytrade.piggytrade.protocol.NetworkConfig.DEXYGOLD_CONFIG["pid"] as? String
        val isHardcoded = pid.isNotEmpty() && (pid == usePid || pid == dexyPid)
        return isHardcoded || (pid.isNotEmpty() && systemWhitelistPids.contains(pid))
    }
    
    fun isUserVerified(pid: String): Boolean = customWhitelistPids.contains(pid)

    /**
     * Returns verification status of a token:
     * 0: Official (System Whitelisted)
     * 1: User Added (Custom Whitelisted)
     * 2: Unverified
     */
    fun getVerificationStatus(tokenKey: String): Int {
        if (tokenKey == "ERG") return 0
        if (tokenKey.equals("USE", ignoreCase = true) || tokenKey.equals("DexyGold", ignoreCase = true)) return 0
        
        // 1. Direct Pool Key check - if it's in our map, we know its specific PID.
        val directData = tokens[tokenKey]
        if (directData != null) {
            val pid = directData["pid"] as? String ?: (directData["lp"] as? String ?: "")
            if (isSystemVerified(pid)) return 0
            if (isUserVerified(pid)) return 1
            return 2 // Known pool, but unverified
        }
        
        // 2. Ticker/Asset check (Fallback) - used for wallet tokens or simple names.
        // We consider an asset verified if AT LEAST ONE pool with this name is official.
        var userFound = false
        for ((_, data) in tokens) {
            val pid = data["pid"] as? String ?: (data["lp"] as? String ?: "")
            val nameIn = data["name_in"] as? String
            val nameOut = data["name_out"] as? String
            val nameOfficial = data["name"] as? String
            
            val isAssetMatch = (nameIn != null && nameIn.equals(tokenKey, ignoreCase = true)) ||
                              (nameOut != null && nameOut.equals(tokenKey, ignoreCase = true)) ||
                              (nameOfficial != null && nameOfficial.equals(tokenKey, ignoreCase = true)) ||
                              (data.containsKey("id") && tokenKey.length > 30 && data["id"] == tokenKey) // tokenId match
            
            if (isAssetMatch) {
                if (isSystemVerified(pid)) return 0
                if (isUserVerified(pid)) userFound = true
            }
        }
        
        return if (userFound) 1 else 2
    }

    data class BlockchainBox(
        val boxId: String,
        val assets: List<BlockchainAsset>,
        val additionalRegisters: Map<String, String>
    )

    data class BlockchainAsset(
        val tokenId: String,
        val amount: Long
    )

    data class BlockchainTokenInfo(
        val id: String,
        val name: String?,
        val decimals: Int
    )

    private fun decodeVlqZigZag(hex: String): Int {
        if (hex.length < 2) return 0
        // Skip the first byte (Sigma type header, e.g., 04 for Int)
        val bytes = try { 
            hex.substring(2).chunked(2).map { it.toInt(16) }
        } catch (e: Exception) { emptyList() }
        
        var res = 0
        var shift = 0
        for (b in bytes) {
            res = res or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return res ushr 1
    }

    suspend fun syncTokensWithBlockchain(
        nodeClient: NodeClient,
        onProgress: (current: Int, total: Int, newTokens: List<String>, batchInfo: String) -> Unit
    ): Map<String, Int> {
        onProgress(0, -1, emptyList(), "Fetching ERG-Token pool boxes...")
        val ergToTokenBoxes = fetchAllBoxesForAddress(nodeClient, ADDR_ERG_TO_TOKEN) { batch ->
            onProgress(0, -1, emptyList(), "ERG-Token pools: offset $batch")
        }
        onProgress(0, -1, emptyList(), "Fetching Token-Token pool boxes...")
        val tokenToTokenBoxes = fetchAllBoxesForAddress(nodeClient, ADDR_TOKEN_TO_TOKEN) { batch ->
            onProgress(0, -1, emptyList(), "Token-Token pools: offset $batch")
        }
        
        val allBoxes = ergToTokenBoxes + tokenToTokenBoxes
        val total = allBoxes.size
        if (total == 0) return mapOf("total" to 0, "new" to 0)

        val tokenIdSet = mutableSetOf<String>()
        val parsedEntries = mutableListOf<Triple<String, Map<String, Any>, Boolean>>() // Name, Data, isTokenToToken

        allBoxes.forEachIndexed { index, boxMap ->
            val assets = (boxMap["assets"] as? List<Map<String, Any>>) ?: emptyList()
            val registers = boxMap["additionalRegisters"] as? Map<String, Any>
            val r4 = (registers?.get("R4") as? String) ?: "04ca0f"
            val decodedFeeNum = decodeVlqZigZag(r4)
            val fee = (1000 - decodedFeeNum).toDouble() / 1000.0
            
            if (index == 0 && BuildConfig.DEBUG) Log.d("TokenRepo", "Sample box R4: $r4 -> decoded: $decodedFeeNum -> fee: $fee")

            if (assets.size >= 3) {
                val pid = assets[0]["tokenId"] as String
                val lp = assets[1]["tokenId"] as String
                
                if (assets.size == 3) {
                    // ERG to Token
                    val tid = assets[2]["tokenId"] as String
                    tokenIdSet.add(tid)
                    val data = mutableMapOf<String, Any>(
                        "id" to tid,
                        "pid" to pid,
                        "lp" to lp,
                        "fee" to fee,
                        "R4" to r4
                    )
                    parsedEntries.add(Triple("", data, false))
                } else if (assets.size >= 4) {
                    // Token to Token
                    val idIn = assets[2]["tokenId"] as String
                    val idOut = assets[3]["tokenId"] as String
                    tokenIdSet.add(idIn)
                    tokenIdSet.add(idOut)
                    val data = mutableMapOf<String, Any>(
                        "id_in" to idIn,
                        "id_out" to idOut,
                        "pid" to pid,
                        "lp" to lp,
                        "fee" to fee,
                        "R4" to r4
                    )
                    parsedEntries.add(Triple("", data, true))
                }
            }
        }

        // Batch fetch token details
        val tokenInfoMap = mutableMapOf<String, BlockchainTokenInfo>()
        val idList = tokenIdSet.toList()
        idList.chunked(20).forEach { chunk ->
            try {
                val infos = nodeClient.api.getTokensInfo(chunk)
                infos.forEach { info ->
                    val tid = info["id"] as String
                    val name = info["name"] as? String ?: tid.take(8)
                    val decimals = (info["decimals"] as? Number)?.toInt() ?: 0
                    tokenInfoMap[tid] = BlockchainTokenInfo(tid, name, decimals)
                }
            } catch (e: Exception) {}
        }



        val existingTokens = loadCombinedTokens()
        val knownPids = existingTokens.values.flatMap { 
            listOfNotNull(
                it["pid"] as? String,
                it["lp"] as? String
            )
        }.toSet()
        val currentKnownPids = knownPids.toMutableSet()

        // Track names that were already known from assets (tokens.json) so we don't re-report them as "new"
        val assetKnownNames: Set<String> = try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Map<String, Any>>>() {}.type
            val inputStream = context.assets.open("tokens.json")
            val assetMap: Map<String, Map<String, Any>> = gson.fromJson(InputStreamReader(inputStream), type)
            assetMap.keys.map { key ->
                if (key.contains("-")) {
                    val parts = key.split("-", limit = 2)
                    "${normalizeTokenName(parts[0])}-${normalizeTokenName(parts[1])}"
                } else {
                    normalizeTokenName(key)
                }
            }.toSet()
        } catch (e: Exception) { emptySet() }

        val newTokensAdded = mutableListOf<String>()
        // Initialize with existing synced tokens to avoid flushing them if sync doesn't see them this time
        val finalErgToToken = mutableMapOf<String, Map<String, Any>>()
        val finalTokenToToken = mutableMapOf<String, Map<String, Any>>()

        // Try to load existing local discovery data first so we don't lose them
        try {
            if (ergToTokenFile.exists()) {
                val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
                val existing: Map<String, Map<String, Any>> = gson.fromJson(ergToTokenFile.readText(), type) ?: emptyMap()
                finalErgToToken.putAll(existing)
            }
            if (tokenToTokenFile.exists()) {
                val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
                val existing: Map<String, Map<String, Any>> = gson.fromJson(tokenToTokenFile.readText(), type) ?: emptyMap()
                finalTokenToToken.putAll(existing)
            }
        } catch (e: Exception) {}
        
        val officialPids = systemWhitelistPids
        val officialNameMap = systemWhitelistNameMap

        // Temporary maps to track best liquidity seen so far during this sync
        val bestErgLiquidity = mutableMapOf<String, Long>()
        val bestTokenLiquidity = mutableMapOf<String, Long>()

        parsedEntries.forEachIndexed { index, triple ->
            val boxMap = allBoxes[index]
            val assets = (boxMap["assets"] as? List<Map<String, Any>>) ?: emptyList()
            val data = triple.second.toMutableMap()
            val isT2T = triple.third
            
            var entryName = ""
            if (!isT2T) {
                val tid = data["id"] as String
                val pid = data["pid"] as? String ?: ""
                val info = tokenInfoMap[tid]
                entryName = normalizeTokenName(info?.name ?: tid.take(8))
                data["dec"] = info?.decimals ?: 0
                data["name"] = entryName
                
                val currentErg = (boxMap["value"] as? Number)?.toLong() ?: 0L
                val isOfficial = officialPids.contains(pid)
                val mustUseSuffix = !isOfficial && assetKnownNames.contains(entryName)

                // If it must use a suffix (official asset duplicate), it doesn't compete for the clean name.
                if (mustUseSuffix) {
                    val suffixedName = "$entryName (${pid.take(4)})"
                    data["official"] = false
                    data["user_added"] = customWhitelistPids.contains(pid)
                    data["whitelisted"] = isPidWhitelisted(pid)
                    data["name"] = suffixedName
                    
                    if (!currentKnownPids.contains(pid)) {
                        newTokensAdded.add(suffixedName)
                        currentKnownPids.add(pid)
                    }
                    finalErgToToken[suffixedName] = data
                } else {
                    // Compete for the clean name slot (Champion logic)
                    val existing = finalErgToToken[entryName]
                    val wasOfficial = existing != null && (existing["official"] as? Boolean ?: false)
                    val bestSoFar = bestErgLiquidity[entryName] ?: 0L

                    val isBetter = when {
                        existing == null -> true
                        isOfficial && !wasOfficial -> true
                        !isOfficial && wasOfficial -> false
                        else -> currentErg > bestSoFar
                    }

                    if (isBetter) {
                        // If replacing an existing non-official champion, move the old one to a suffixed slot
                        if (existing != null && !wasOfficial) {
                            val oldPid = existing["pid"] as? String ?: ""
                            val oldName = existing["name"] as? String ?: ""
                            // Avoid double suffixing
                            val oldFinalName = if (oldName.contains(" (")) oldName else "$oldName (${oldPid.take(4)})"
                            finalErgToToken[oldFinalName] = existing
                        }

                        data["official"] = isOfficial
                        data["user_added"] = customWhitelistPids.contains(pid)
                        data["whitelisted"] = isPidWhitelisted(pid)
                        
                        val finalName = if (isOfficial) (officialNameMap[pid] ?: entryName) else entryName
                        data["name"] = finalName
                        
                        if (!currentKnownPids.contains(pid)) {
                            newTokensAdded.add(finalName)
                            currentKnownPids.add(pid)
                        }
                        
                        finalErgToToken[entryName] = data
                        bestErgLiquidity[entryName] = currentErg
                    } else {
                        // Not the champion. Store with suffix.
                        val suffixedName = "$entryName (${pid.take(4)})"
                        data["official"] = isOfficial
                        data["user_added"] = customWhitelistPids.contains(pid)
                        data["whitelisted"] = isPidWhitelisted(pid)
                        data["name"] = suffixedName
                        
                        if (!currentKnownPids.contains(pid)) {
                            newTokensAdded.add(suffixedName)
                            currentKnownPids.add(pid)
                        }
                        finalErgToToken[suffixedName] = data
                    }
                }
            } else {
                val idIn = data["id_in"] as String
                val idOut = data["id_out"] as String
                val pid = if (assets.isNotEmpty()) assets[0]["tokenId"] as String else ""
                val infoIn = tokenInfoMap[idIn]
                val infoOut = tokenInfoMap[idOut]
                val nameIn = normalizeTokenName(infoIn?.name ?: idIn.take(8))
                val nameOut = normalizeTokenName(infoOut?.name ?: idOut.take(8))
                entryName = "$nameIn-$nameOut"
                data["dec_in"] = infoIn?.decimals ?: 0
                data["dec_out"] = infoOut?.decimals ?: 0
                data["name"] = entryName
                data["name_in"] = nameIn
                data["name_out"] = nameOut
                
                val currentLiquidity = if (assets.size >= 3) (assets[2]["amount"] as? Number)?.toLong() ?: 0L else 0L
                val isVerified = officialPids.contains(pid) || isPidWhitelisted(pid)
                val isOfficial = officialPids.contains(pid)
                val mustUseSuffix = !isOfficial && assetKnownNames.contains(entryName)

                if (mustUseSuffix) {
                    val suffixedName = "$entryName (${pid.take(4)})"
                    data["official"] = false
                    data["user_added"] = customWhitelistPids.contains(pid)
                    data["whitelisted"] = isPidWhitelisted(pid)
                    data["name"] = suffixedName
                    
                    if (!currentKnownPids.contains(pid)) {
                        newTokensAdded.add(suffixedName)
                        currentKnownPids.add(pid)
                    }
                    finalTokenToToken[suffixedName] = data
                } else {
                    // Champion logic for T2T
                    val existing = finalTokenToToken[entryName]
                    val wasOfficial = existing != null && (existing["official"] as? Boolean ?: false)
                    val bestSoFar = bestTokenLiquidity[entryName] ?: 0L

                    val isBetter = when {
                        existing == null -> true
                        isOfficial && !wasOfficial -> true
                        !isOfficial && wasOfficial -> false
                        else -> currentLiquidity > bestSoFar
                    }

                    if (isBetter) {
                        if (existing != null && !wasOfficial) {
                            val oldPid = existing["pid"] as? String ?: ""
                            val oldName = existing["name"] as? String ?: ""
                            val oldFinalName = if (oldName.contains(" (")) oldName else "$oldName (${oldPid.take(4)})"
                            finalTokenToToken[oldFinalName] = existing
                        }

                        data["official"] = isOfficial
                        data["user_added"] = customWhitelistPids.contains(pid)
                        data["whitelisted"] = isPidWhitelisted(pid)
                        data["name"] = entryName
                        
                        if (!currentKnownPids.contains(pid)) {
                            newTokensAdded.add(entryName)
                            currentKnownPids.add(pid)
                        }
                        
                        finalTokenToToken[entryName] = data
                        bestTokenLiquidity[entryName] = currentLiquidity
                    } else {
                        val suffixedName = "$entryName (${pid.take(4)})"
                        data["official"] = isOfficial
                        data["user_added"] = customWhitelistPids.contains(pid)
                        data["whitelisted"] = isPidWhitelisted(pid)
                        data["name"] = suffixedName

                        if (!currentKnownPids.contains(pid)) {
                            newTokensAdded.add(suffixedName)
                            currentKnownPids.add(pid)
                        }
                        finalTokenToToken[suffixedName] = data
                    }
                }
            }
            onProgress(index + 1, total, newTokensAdded.toList(), "Analysing box ${index + 1} of $total")
        }

        // Save all discovered token info to cache for persistent naming
        tokenInfoMap.forEach { (tid, info) ->
            saveTokenInfo(tid, mapOf("name" to (info.name ?: tid.take(8)), "decimals" to info.decimals))
        }

        // Save files
        try {
            ergToTokenFile.writeText(gson.toJson(finalErgToToken))
            tokenToTokenFile.writeText(gson.toJson(finalTokenToToken))
            refreshTokens() // Ensure instance is immediately updated
        } catch (e: Exception) {}

        return mapOf("total" to total, "new" to newTokensAdded.size)
    }

    companion object {
        const val ADDR_ERG_TO_TOKEN = "5vSUZRZbdVbnk4sJWjg2uhL94VZWRg4iatK9VgMChufzUgdihgvhR8yWSUEJKszzV7Vmi6K8hCyKTNhUaiP8p5ko6YEU9yfHpjVuXdQ4i5p4cRCzch6ZiqWrNukYjv7Vs5jvBwqg5hcEJ8u1eerr537YLWUoxxi1M4vQxuaCihzPKMt8NDXP4WcbN6mfNxxLZeGBvsHVvVmina5THaECosCWozKJFBnscjhpr3AJsdaL8evXAvPfEjGhVMoTKXAb2ZGGRmR8g1eZshaHmgTg2imSiaoXU5eiF3HvBnDuawaCtt674ikZ3oZdekqswcVPGMwqqUKVsGY4QuFeQoGwRkMqEYTdV2UDMMsfrjrBYQYKUBFMwsQGMNBL1VoY78aotXzdeqJCBVKbQdD3ZZWvukhSe4xrz8tcF3PoxpysDLt89boMqZJtGEHTV9UBTBEac6sDyQP693qT3nKaErN8TCXrJBUmHPqKozAg9bwxTqMYkpmb9iVKLSoJxG7MjAj72SRbcqQfNCVTztSwN3cRxSrVtz4p87jNFbVtFzhPg7UqDwNFTaasySCqM"
        const val ADDR_TOKEN_TO_TOKEN = "3gb1RZucekcRdda82TSNS4FZSREhGLoi1FxGDmMZdVeLtYYixPRviEdYireoM9RqC6Jf4kx85Y1jmUg5XzGgqdjpkhHm7kJZdgUR3VBwuLZuyHVqdSNv3eanqpknYsXtUwvUA16HFwNa3HgVRAnGC8zj8U7kksrfjycAM1yb19BB4TYR2BKWN7mpvoeoTuAKcAFH26cM46CEYsDRDn832wVNTLAmzz4Q6FqE29H9euwYzKiebgxQbWUxtupvfSbKaHpQcZAo5Dhyc6PFPyGVFZVRGZZ4Kftgi1NMRnGwKG7NTtXsFMsJP6A7yvLy8UZaMPe69BUAkpbSJdcWem3WpPUE7UpXv4itDkS5KVVaFtVyfx8PQxzi2eotP2uXtfairHuKinbpSFTSFKW3GxmXaw7vQs1JuVd8NhNShX6hxSqCP6sxojrqBxA48T2KcxNrmE3uFk7Pt4vPPdMAS4PW6UU82UD9rfhe3SMytK6DkjCocuRwuNqFoy4k25TXbGauTNgKuPKY3CxgkTpw9WfWsmtei178tLefhUEGJueueXSZo7negPYtmcYpoMhCuv4G1JZc283Q7f3mNXS"

        fun normalizeTokenName(name: String): String {
            val trimmed = name.trim()
            return when {
                trimmed.equals("ERG", ignoreCase = true) -> "ERG"
                trimmed.equals("USE", ignoreCase = true) -> "USE"
                trimmed.equals("DexyGold", ignoreCase = true) -> "DexyGold"
                else -> trimmed
            }
        }
    }

    private var _tokens: Map<String, Map<String, Any>>? = null
    val tokens: Map<String, Map<String, Any>>
        get() = synchronized(this) {
            if (_tokens == null) _tokens = loadCombinedTokens()
            _tokens!!
        }

    private fun loadCombinedTokens(): Map<String, Map<String, Any>> {
        val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
        
        // 1. Load synced tokens (Local Files)
        val syncedErgToToken: Map<String, Map<String, Any>> = try {
            if (ergToTokenFile.exists()) gson.fromJson(ergToTokenFile.readText(), type) else emptyMap()
        } catch (e: Exception) { emptyMap() }

        val syncedTokenToToken: Map<String, Map<String, Any>> = try {
            if (tokenToTokenFile.exists()) gson.fromJson(tokenToTokenFile.readText(), type) else emptyMap()
        } catch (e: Exception) { emptyMap() }

        val combined = mutableMapOf<String, Map<String, Any>>()
        
        fun addSynced(syncedMap: Map<String, Map<String, Any>>) {
            syncedMap.forEach { (key, data) ->
                val pid = data["pid"] as? String ?: (data["lp"] as? String ?: "")
                if (pid.isNotEmpty()) {
                    val mut = data.toMutableMap()
                    if (isSystemVerified(pid)) {
                        mut["official"] = true
                        mut["whitelisted"] = true
                    } else {
                        mut["official"] = false
                        mut["whitelisted"] = isPidWhitelisted(pid)
                    }
                    mut["user_added"] = customWhitelistPids.contains(pid)
                    combined[key] = mut
                }
            }
        }

        addSynced(syncedErgToToken)
        addSynced(syncedTokenToToken)

        // 2. Inject official assets from tokens.json as baseline
        try {
            val inputStream = context.assets.open("tokens.json")
            val typeObj = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
            val assetMap: Map<String, Map<String, Any>> = gson.fromJson(InputStreamReader(inputStream), typeObj) ?: emptyMap()
            assetMap.forEach { (key, data) ->
                val pid = data["pid"] as? String ?: ""
                val normalizedKey = normalizeTokenName(key)
                
                val existing = combined[normalizedKey]
                
                // If the existing synced entry has id_in (T2T pair), don't overwrite it
                // with the stripped tokens.json version that only has pid.
                if (existing != null && existing.containsKey("id_in")) {
                    // Just update the official/whitelisted flags on the existing entry
                    val mut = existing.toMutableMap()
                    mut["official"] = true
                    mut["whitelisted"] = true
                    combined[normalizedKey] = mut
                    return@forEach
                }
                
                // Only add if not already present or if the existing one isn't official
                if (existing == null || !(existing["official"] as? Boolean ?: false)) {
                    val mut = data.toMutableMap()
                    mut["official"] = true
                    mut["whitelisted"] = true
                    mut["name"] = normalizedKey
                    combined[normalizedKey] = mut
                }
            }
        } catch (e: Exception) {
            Log.e("TokenRepo", "Error injecting tokens.json: ${e.message}")
        }

        // 3. Inject specialized hardcoded tokens
        val useCfg = com.piggytrade.piggytrade.protocol.NetworkConfig.USE_CONFIG
        combined["USE"] = useCfg.toMutableMap().apply {
            put("official", true)
            put("whitelisted", true)
        }
        
        val dexyCfg = com.piggytrade.piggytrade.protocol.NetworkConfig.DEXYGOLD_CONFIG
        combined["DexyGold"] = dexyCfg.toMutableMap().apply {
            put("official", true)
            put("whitelisted", true)
        }

        return combined
    }

    private var _cachedTokenInfo: MutableMap<String, Map<String, Any>>? = null

    val cachedTokenInfo: Map<String, Map<String, Any>>
        get() {
            if (_cachedTokenInfo == null) {
                _cachedTokenInfo = loadCache().toMutableMap()
            }
            return _cachedTokenInfo!!
        }

    private fun loadCache(): Map<String, Map<String, Any>> {
        if (!cacheFile.exists()) return emptyMap()
        return try {
            val json = cacheFile.readText()
            val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveTokenInfo(tokenId: String, info: Map<String, Any>) {
        if (_cachedTokenInfo == null) _cachedTokenInfo = loadCache().toMutableMap()
        _cachedTokenInfo!![tokenId] = info
        try {
            cacheFile.writeText(gson.toJson(_cachedTokenInfo))
        } catch (e: Exception) {}
    }

    fun getTokenName(tokenId: String): String {
        val rawName = resolveRawTokenName(tokenId)
        return normalizeTokenName(rawName)
    }

    private fun resolveRawTokenName(tokenId: String): String {
        if (tokenId.equals("ERG", ignoreCase = true)) return "ERG"
        
        // 0. Hardcoded specialized tokens (Check by Token ID)
        if (tokenId == com.piggytrade.piggytrade.protocol.NetworkConfig.USE_CONFIG["id"]) return "USE"
        if (tokenId == com.piggytrade.piggytrade.protocol.NetworkConfig.DEXYGOLD_CONFIG["id"]) return "DexyGold"

        // 1. Check direct ERG-to-Token matches in current pools
        for ((name, data) in tokens) {
            if (data["id"] == tokenId) return name
        }
        
        // 2. Check Token-to-Token pools for component matches
        for ((name, data) in tokens) {
            if (name.contains("-")) {
                val idIn = data["id_in"]
                val idOut = data["id_out"]
                if (idIn == tokenId) return name.split("-")[0]
                if (idOut == tokenId) return name.split("-")[1]
            }
        }
        
        // 3. Fallback to cached metadata
        val info = cachedTokenInfo[tokenId]
        return (info?.get("name") as? String) ?: (if (tokenId.length > 8) tokenId.take(8) + ".." else tokenId)
    }

    private fun normalizeTokenName(name: String): String = Companion.normalizeTokenName(name)

    fun getTokenDecimals(tokenId: String): Int {
        if (tokenId == "ERG") return 9
        // Check current tokens (must be synced/discovered to have valid metadata)
        for ((_, data) in tokens) {
            if (data["id"] == tokenId) {
                return (data["dec"] as? Number)?.toInt() ?: 0
            }
        }
        // Check cache
        val info = cachedTokenInfo[tokenId]
        return (info?.get("decimals") as? Number)?.toInt() 
            ?: (info?.get("dec") as? Number)?.toInt() 
            ?: 0
    }

    private var _tradeMapper: TradeMapper? = null
    val tradeMapper: TradeMapper
        get() = synchronized(this) {
            if (_tradeMapper == null) _tradeMapper = TradeMapper(tokens)
            _tradeMapper!!
        }

    fun refreshTokens() {
        _tokens = loadCombinedTokens()
        _tradeMapper = TradeMapper(tokens)
    }

    private suspend fun fetchAllBoxesForAddress(
        nodeClient: NodeClient,
        address: String,
        onBatch: ((offset: Int) -> Unit)? = null
    ): List<Map<String, Any>> {
        val allBoxes = mutableListOf<Map<String, Any>>()
        var offset = 0
        val limit = 50
        val mediaType = "application/json".toMediaTypeOrNull()
        val jsonAddress = "\"$address\""
        
        if (BuildConfig.DEBUG) Log.d("TokenRepo", "Fetching boxes for address (JSON): ${address.take(15)}...")
        
        while (true) {
            try {
                onBatch?.invoke(offset)
                if (BuildConfig.DEBUG) Log.d("TokenRepo", "Requesting boxes with offset=$offset, limit=$limit")
                val boxes = nodeClient.api.getUnspentBoxesByAddressPost(
                    offset = offset,
                    limit = limit,
                    includeUnconfirmed = false,
                    excludeMempoolSpent = false,
                    address = jsonAddress.toRequestBody(mediaType)
                )
                
                if (BuildConfig.DEBUG) Log.d("TokenRepo", "Received ${boxes.size} boxes")
                if (boxes.isEmpty()) break
                allBoxes.addAll(boxes)
                if (boxes.size < limit) break
                offset += limit
            } catch (e: Exception) {
                Log.e("TokenRepo", "Error fetching boxes at offset $offset: ${e.message}", e)
                // Fallback: try raw text if JSON fails
                if (offset == 0) {
                    return fetchAllBoxesForAddressRaw(nodeClient, address)
                }
                break
            }
        }
        if (BuildConfig.DEBUG) Log.d("TokenRepo", "Finished fetching. Total boxes: ${allBoxes.size}")
        return allBoxes
    }

    private suspend fun fetchAllBoxesForAddressRaw(nodeClient: NodeClient, address: String): List<Map<String, Any>> {
        val allBoxes = mutableListOf<Map<String, Any>>()
        var offset = 0
        val limit = 50
        val mediaType = "text/plain".toMediaTypeOrNull()
        
        if (BuildConfig.DEBUG) Log.d("TokenRepo", "Fetching boxes for address (RAW): ${address.take(15)}...")
        
        while (true) {
            try {
                val boxes = nodeClient.api.getUnspentBoxesByAddressPost(
                    offset = offset,
                    limit = limit,
                    includeUnconfirmed = false,
                    excludeMempoolSpent = false,
                    address = address.toRequestBody(mediaType)
                )
                if (boxes.isEmpty()) break
                allBoxes.addAll(boxes)
                if (boxes.size < limit) break
                offset += limit
            } catch (e: Exception) {
                Log.e("TokenRepo", "Error in RAW fetching: ${e.message}")
                break
            }
        }
        return allBoxes
    }

    fun getAllAssets(): List<String> = tradeMapper.allAssets()

    fun getToAssetsFor(fromAsset: String): List<String> = tradeMapper.toAssetsFor(fromAsset)

    fun hasTokenFiles(): Boolean = ergToTokenFile.exists() || tokenToTokenFile.exists()

    fun getErgToTokenJson(): String = if (ergToTokenFile.exists()) ergToTokenFile.readText() else "{}"
    fun getTokenToTokenJson(): String = if (tokenToTokenFile.exists()) tokenToTokenFile.readText() else "{}"
    
    fun isTokenToToken(name: String): Boolean {
        // Direct check: entry has id_in in its data
        if (tokens[name]?.containsKey("id_in") == true) return true
        // Heuristic: if name has a hyphen and both halves exist as separate entries, it's a T2T pair
        if (name.contains("-")) {
            val parts = name.split("-", limit = 2)
            if (parts.size == 2) {
                val left = parts[0].trim()
                val right = parts[1].trim()
                val leftExists = tokens.keys.any { it.equals(left, ignoreCase = true) && !it.contains("-") }
                val rightExists = tokens.keys.any { it.equals(right, ignoreCase = true) && !it.contains("-") }
                if (leftExists && rightExists) return true
            }
        }
        return false
    }

    fun deleteTokenData(name: String) {
        val all = tokens.toMutableMap()
        all.remove(name)
        
        val ergs = all.filter { !it.value.containsKey("id_in") }
        val t2ts = all.filter { it.value.containsKey("id_in") }
        
        try {
            ergToTokenFile.writeText(gson.toJson(ergs))
            tokenToTokenFile.writeText(gson.toJson(t2ts))
            refreshTokens()
        } catch (e: Exception) {}
    }

    fun updateTokenData(key: String, data: Map<String, Any>) {
        val all = tokens.toMutableMap()
        all[key] = data
        
        // Update custom whitelist if pid is in data
        (data["pid"] as? String)?.let { pid ->
            val isUser = data["user_added"] as? Boolean ?: false
            if (isUser) customWhitelistPids.add(pid) else customWhitelistPids.remove(pid)
            saveCustomWhitelist(customWhitelistPids)
        }
        
        saveToFiles(all)
    }

    fun renameTokenData(oldKey: String, newKey: String, data: Map<String, Any>) {
        val all = tokens.toMutableMap()
        all.remove(oldKey)
        all[newKey] = data
        
        // Update custom whitelist if pid is in data
        (data["pid"] as? String)?.let { pid ->
            val isUser = data["user_added"] as? Boolean ?: false
            if (isUser) customWhitelistPids.add(pid) else customWhitelistPids.remove(pid)
            saveCustomWhitelist(customWhitelistPids)
        }
        
        saveToFiles(all)
    }

    private fun saveToFiles(all: Map<String, Map<String, Any>>) {
        val ergs = all.filter { !it.value.containsKey("id_in") }
        val t2ts = all.filter { it.value.containsKey("id_in") }
        
        try {
            ergToTokenFile.writeText(gson.toJson(ergs))
            tokenToTokenFile.writeText(gson.toJson(t2ts))
            refreshTokens()
        } catch (e: Exception) {}
    }

    fun resetTokenData() {
        try {
            if (ergToTokenFile.exists()) ergToTokenFile.delete()
            if (tokenToTokenFile.exists()) tokenToTokenFile.delete()
            if (whitelistFile.exists()) whitelistFile.delete()
            if (cacheFile.exists()) cacheFile.delete()
            
            customWhitelistPids.clear()
            refreshTokens()
        } catch (e: Exception) {
            Log.e("TokenRepo", "Error resetting token data: ${e.message}")
        }
    }
}
