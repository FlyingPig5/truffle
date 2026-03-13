package com.piggytrade.piggytrade.blockchain

import android.util.Log
import com.piggytrade.piggytrade.BuildConfig

data class TradeRoute(
    val tokenKey: String, // Original key from tokens map
    val orderType: String,
    val poolType: String,
    val pid: String = ""
)

class TradeMapper(private val tokens: Map<String, Map<String, Any>>) {

    companion object {
        const val ERG = "ERG"
        private const val TAG = "TradeMapper"
    }

    // Normalized asset names reachable directly from ERG
    private val ergTokens = mutableSetOf<String>()
    
    // Maps original token key -> Pair(n1, n2)
    private val poolAssetPairs = mutableMapOf<String, Pair<String, String>>()

    init {
        for ((key, data) in tokens) {
            val isT2T = data.containsKey("id_in") || data.containsKey("id_out")
            
            if (isT2T) {
                val nameIn = (data["name_in"] as? String)
                val nameOut = (data["name_out"] as? String)
                
                val parts = key.split("-", limit = 2)
                val rawN1 = nameIn ?: parts[0]
                val rawN2 = nameOut ?: if (parts.size > 1) parts[1] else ""
                
                val n1 = com.piggytrade.piggytrade.data.TokenRepository.normalizeTokenName(rawN1)
                val n2 = com.piggytrade.piggytrade.data.TokenRepository.normalizeTokenName(rawN2)
                
                poolAssetPairs[key] = Pair(n1, n2)
            } else {
                // ERG pool. The key is the identifier/name.
                ergTokens.add(com.piggytrade.piggytrade.data.TokenRepository.normalizeTokenName(key))
            }
        }
    }

    fun allAssets(): List<String> {
        val seen = mutableSetOf<String>()
        seen.add(ERG)
        seen.addAll(ergTokens)
        for (sides in poolAssetPairs.values) {
            seen.add(sides.first)
            seen.add(sides.second)
        }
        val others = seen.filter { it != ERG }.sorted()
        return listOf(ERG) + others
    }

    fun normalizeAsset(assetName: String?): String? {
        if (assetName.isNullOrBlank()) return assetName
        return com.piggytrade.piggytrade.data.TokenRepository.normalizeTokenName(assetName)
    }

    fun toAssetsFor(fromAsset: String): List<String> {
        val reachable = mutableSetOf<String>()
        val fa = normalizeAsset(fromAsset) ?: return emptyList()

        if (fa == ERG) {
            reachable.addAll(ergTokens)
        } else if (fa in ergTokens) {
            reachable.add(ERG)
        }

        for (sides in poolAssetPairs.values) {
            if (fa == sides.second) {
                reachable.add(sides.first)
            } else if (fa == sides.first) {
                reachable.add(sides.second)
            }
        }

        reachable.remove(fa)
        return reachable.sorted()
    }

    /**
     * Resolve exactly which POOL to use. 
     * If there are multiple pools for the same pair, this currently picks the FIRST one.
     * In a robust implementation, this would pick the most liquid one.
     */
    fun resolve(fromAsset: String, toAsset: String): TradeRoute? {
        val fa = normalizeAsset(fromAsset) ?: return null
        val ta = normalizeAsset(toAsset) ?: return null

        if (BuildConfig.DEBUG) Log.d(TAG, "resolve('$fromAsset','$toAsset') -> fa='$fa' ta='$ta' ergTokens=${ergTokens.size}")

        // 1. Check ERG-Token pools
        if (fa == ERG) {
            // Find a pool for ta where fa is ERG
            for ((key, data) in tokens) {
                if (data.containsKey("id_in")) continue // skip T2T
                val nToken = com.piggytrade.piggytrade.data.TokenRepository.normalizeTokenName(key)
                if (nToken == ta) {
                    val pid = data["pid"] as? String ?: ""
                    return TradeRoute(tokenKey = key, orderType = "BUY", poolType = "erg", pid = pid)
                }
            }
        }
        if (ta == ERG) {
            // Find a pool for fa where ta is ERG
            for ((key, data) in tokens) {
                if (data.containsKey("id_in")) continue // skip T2T
                val nToken = com.piggytrade.piggytrade.data.TokenRepository.normalizeTokenName(key)
                if (nToken == fa) {
                    val pid = data["pid"] as? String ?: ""
                    return TradeRoute(tokenKey = key, orderType = "SELL", poolType = "erg", pid = pid)
                }
            }
        }

        // 2. Check Token-Token pools
        for ((key, sides) in poolAssetPairs) {
            val pid = tokens[key]?.get("pid") as? String ?: ""
            if (fa == sides.second && ta == sides.first) {
                return TradeRoute(tokenKey = key, orderType = "BUY", poolType = "token", pid = pid)
            }
            if (fa == sides.first && ta == sides.second) {
                return TradeRoute(tokenKey = key, orderType = "SELL", poolType = "token", pid = pid)
            }
        }

        Log.w(TAG, "No route for '$fa' -> '$ta'.")
        return null
    }

    fun describeRoute(route: TradeRoute, amount: String, expected: String): String {
        val data = tokens[route.tokenKey]
        if (route.poolType == "erg") {
            return if (route.orderType == "BUY") {
                "$expected ${route.tokenKey} for $amount ERG"
            } else {
                "$expected ERG for $amount ${route.tokenKey}"
            }
        } else {
            val n1 = com.piggytrade.piggytrade.data.TokenRepository.normalizeTokenName(data?.get("name_in") as? String ?: route.tokenKey.split("-")[0])
            val n2 = com.piggytrade.piggytrade.data.TokenRepository.normalizeTokenName(data?.get("name_out") as? String ?: route.tokenKey.split("-").getOrNull(1) ?: "")
            
            return if (route.orderType == "BUY") {
                "$expected $n1 for $amount $n2"
            } else {
                "$expected $n2 for $amount $n1"
            }
        }
    }
}
