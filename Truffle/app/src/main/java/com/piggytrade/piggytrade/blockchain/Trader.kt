package com.piggytrade.piggytrade.blockchain

import com.piggytrade.piggytrade.network.NodeClient
import java.math.BigDecimal
import java.math.RoundingMode

class Trader(
    private val client: NodeClient,
    private val builder: TxBuilder?,
    private val tokens: Map<String, Map<String, Any>>,
    private val signer: ErgoSigner? = null
) {
    private val poolBoxCache = mutableMapOf<String, Pair<Long, Map<String, Any>>>()
    private val CACHE_DURATION_MS = 10_000L

    private fun getPoolConfig(poolKey: String): Map<String, Any> {
        val trimmed = poolKey.trim()
        val direct = tokens[trimmed]
        if (direct != null) return direct

        // Case-insensitive search by key
        for ((key, info) in tokens) {
            if (key.equals(trimmed, ignoreCase = true)) {
                return info
            }
        }

        // Search by PID
        for (info in tokens.values) {
            if ((info["pid"] as? String) == trimmed) {
                return info
            }
        }

        // Case-insensitive search by 'name' field
        for (info in tokens.values) {
            if ((info["name"] as? String)?.equals(trimmed, ignoreCase = true) == true) {
                return info
            }
        }
        throw IllegalArgumentException("Pool '$poolKey' not found")
    }

    suspend fun getQuote(poolKey: String, amount: Double, orderType: String, poolType: String = "erg", checkMempool: Boolean = true): Pair<String, Double> {
        return try {
            val cfg = getPoolConfig(poolKey)
            val tokenId = cfg["id"] as? String ?: ""
            val tokenPid = cfg["pid"] as? String ?: ""
            
            val cacheKey = "$tokenPid-$checkMempool"
            val cached = poolBoxCache[cacheKey]
            val now = System.currentTimeMillis()
            
            val poolBox = if (cached != null && (now - cached.first) < CACHE_DURATION_MS) {
                cached.second
            } else {
                val box = client.getPoolBox(tokenPid, checkMempool) ?: return Pair("Error: Pool not found", 0.0)
                poolBoxCache[cacheKey] = Pair(now, box)
                box
            }
            
            val poolNanoerg = (poolBox["value"] as? Number)?.toLong() ?: 0L
            val amountDec = BigDecimal.valueOf(amount)
            val fee = (cfg["fee"] as? Number)?.toDouble() ?: 0.003
            val decimals = (cfg["dec"] as? Number)?.toInt() ?: 0

            return if (poolType == "erg") {
                val poolTokenBal = getBal(poolBox, tokenId)
                if (orderType.equals("buy", ignoreCase = true)) {
                    val nergToSend = amountDec.multiply(BigDecimal.valueOf(1000000000))
                    val feeMult = BigDecimal.ONE.subtract(BigDecimal.valueOf(fee))
                    val ergAmm = nergToSend.multiply(feeMult).toLong()
                    val (delta, _, _) = Amm.buyToken(ergAmm, poolNanoerg, poolTokenBal)
                    val readableOut = delta.divide(BigDecimal.TEN.pow(decimals), decimals, RoundingMode.HALF_UP)
                    
                    if (poolTokenBal == 0L) return Pair("Pool has no tokens", 0.0)
                    
                    val spotPrice = BigDecimal.valueOf(poolNanoerg).divide(BigDecimal.valueOf(poolTokenBal), 10, RoundingMode.HALF_UP)
                    
                    if (readableOut.compareTo(BigDecimal.ZERO) == 0) return Pair("Amount too small", 0.0)
                    
                    // Execution price (pure slippage, excluding swap fee)
                    val executionPrice = BigDecimal.valueOf(ergAmm).divide(delta, 10, RoundingMode.HALF_UP)
                    val priceImpact = if (spotPrice.compareTo(BigDecimal.ZERO) == 0) 0.0 
                    else executionPrice.subtract(spotPrice).divide(spotPrice, 10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).toDouble()
                    
                    Pair(formatReadable(readableOut, decimals), priceImpact)
                } else {
                    val tokenAmt = amountDec.multiply(BigDecimal.TEN.pow(decimals)).toLong()
                    val (ergOut, _, _, _) = Amm.sellToken(tokenAmt, poolNanoerg, poolTokenBal)
                    val feeMult = BigDecimal.ONE.subtract(BigDecimal.valueOf(fee))
                    val ergReceived = BigDecimal.valueOf(ergOut).multiply(feeMult)
                    
                    val readableOut = ergReceived.divide(BigDecimal.valueOf(1000000000), 9, RoundingMode.HALF_UP)
                    
                    if (poolNanoerg == 0L) return Pair("Pool has no ERG", 0.0)
                    
                    val spotPrice = BigDecimal.valueOf(poolTokenBal).divide(BigDecimal.valueOf(poolNanoerg), 10, RoundingMode.HALF_UP)
                    
                    if (ergReceived.compareTo(BigDecimal.ZERO) == 0) return Pair("Amount too small", 0.0)
                    
                    // Execution price (pure slippage, excluding swap fee)
                    val executionPrice = BigDecimal.valueOf(tokenAmt).divide(BigDecimal.valueOf(ergOut), 10, RoundingMode.HALF_UP)
                    val priceImpact = if (spotPrice.compareTo(BigDecimal.ZERO) == 0) 0.0
                    else executionPrice.subtract(spotPrice).divide(spotPrice, 10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).toDouble()
                    
                    Pair(formatReadable(readableOut, 9), priceImpact)
                }
            } else if (poolType == "token") {
                val tidX = cfg["id_in"] as? String ?: ""
                val tidY = cfg["id_out"] as? String ?: ""
                val decX = (cfg["dec_in"] as? Number)?.toInt() ?: 0
                val decY = (cfg["dec_out"] as? Number)?.toInt() ?: 0
                val poolBalX = getBal(poolBox, tidX)
                val poolBalY = getBal(poolBox, tidY)

                if (orderType.equals("sell", ignoreCase = true)) {
                    val amtInX = amountDec.multiply(BigDecimal.TEN.pow(decX))
                    val deltaY = Amm.tokenForToken(amtInX, BigDecimal.valueOf(poolBalX), BigDecimal.valueOf(poolBalY), BigDecimal.valueOf(fee))
                    val readableOut = deltaY.divide(BigDecimal.TEN.pow(decY), decY, RoundingMode.HALF_UP)
                    
                    if (poolBalY == 0L) return Pair("Pool has no $tidY", 0.0)
                    val spotPrice = BigDecimal.valueOf(poolBalX).divide(BigDecimal.valueOf(poolBalY), 10, RoundingMode.HALF_UP)
                    
                    if (deltaY.compareTo(BigDecimal.ZERO) == 0) return Pair("Amount too small", 0.0)
                    
                    // Execution price (pure slippage, excluding swap fee)
                    val effectiveInX = amtInX.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(fee)))
                    val executionPrice = effectiveInX.divide(deltaY, 10, RoundingMode.HALF_UP)
                    val priceImpact = if (spotPrice.compareTo(BigDecimal.ZERO) == 0) 0.0
                    else executionPrice.subtract(spotPrice).divide(spotPrice, 10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).toDouble()
                    
                    Pair(formatReadable(readableOut, decY), priceImpact)
                } else {
                    val amtInY = amountDec.multiply(BigDecimal.TEN.pow(decY))
                    val deltaX = Amm.tokenForToken(amtInY, BigDecimal.valueOf(poolBalY), BigDecimal.valueOf(poolBalX), BigDecimal.valueOf(fee))
                    val readableOut = deltaX.divide(BigDecimal.TEN.pow(decX), decX, RoundingMode.HALF_UP)
                    
                    if (poolBalX == 0L) return Pair("Pool has no $tidX", 0.0)
                    val spotPrice = BigDecimal.valueOf(poolBalY).divide(BigDecimal.valueOf(poolBalX), 10, RoundingMode.HALF_UP)
                    
                    if (deltaX.compareTo(BigDecimal.ZERO) == 0) return Pair("Amount too small", 0.0)
                    
                    // Execution price (pure slippage, excluding swap fee)
                    val effectiveInY = amtInY.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(fee)))
                    val executionPrice = effectiveInY.divide(deltaX, 10, RoundingMode.HALF_UP)
                    val priceImpact = if (spotPrice.compareTo(BigDecimal.ZERO) == 0) 0.0
                    else executionPrice.subtract(spotPrice).divide(spotPrice, 10, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).toDouble()
                    
                    Pair(formatReadable(readableOut, decX), priceImpact)
                }
            } else {
                Pair("Error: Unknown pool type", 0.0)
            }
        } catch (e: Exception) {
            Pair("Error: ${e.message}", 0.0)
        }
    }

    private fun getBal(box: Map<String, Any>, tid: String): Long {
        val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
        return (assets.find { it["tokenId"] == tid }?.get("amount") as? Number)?.toLong() ?: 0L
    }

    private fun formatReadable(value: BigDecimal, dec: Int): String {
        return value.setScale(dec, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    suspend fun buildSwapTransaction(
        poolKey: String,
        amount: Double,
        orderType: String,
        poolType: String = "erg",
        senderAddress: String,
        currentHeight: Int,
        fee: Double = 0.002,
        includeUnconfirmed: Boolean = true,
        changeAddress: String? = null,
        addressBoxes: Map<String, List<Map<String, Any>>>? = null
    ): Map<String, Any> {
        if (builder == null) throw IllegalStateException("TxBuilder not provided")
        
        val feeNano = (fee * 1_000_000_000).toLong()
        val cfg = getPoolConfig(poolKey)
        val tokenId = cfg["id"] as? String ?: ""
        val tokenPid = cfg["pid"] as? String ?: ""
        val lpId = cfg["lp"] as? String ?: ""
        
        val poolBox = client.getPoolBox(tokenPid, includeUnconfirmed) ?: throw IllegalArgumentException("Pool box for ${poolKey} not found")
        
        var lpSwapBox: Map<String, Any>? = null
        if (poolKey.equals("use", true) || poolKey.equals("dexygold", true)) {
            val cfgLp = if (poolKey.equals("use", true)) com.piggytrade.piggytrade.protocol.NetworkConfig.USE_CONFIG else com.piggytrade.piggytrade.protocol.NetworkConfig.DEXYGOLD_CONFIG
            val lpNftId = cfgLp["lp_nft"] as String
            lpSwapBox = client.getPoolBox(lpNftId, includeUnconfirmed) ?: throw IllegalArgumentException("${poolKey.uppercase()} LP Swap Box not found!")
        }
        
        val myAssets: Map<String, Long>
        val myNanoerg: Long
        val userBoxes: List<Map<String, Any>>

        if (addressBoxes != null && addressBoxes.size > 1) {
            // Multi-address mode: aggregate assets and select boxes smartly
            val aggregatedAssets = mutableMapOf<String, Long>()
            var totalNanoerg = 0L
            val allUserBoxes = mutableListOf<Map<String, Any>>()
            for ((_, boxes) in addressBoxes) {
                for (box in boxes) {
                    totalNanoerg += (box["value"] as? Number)?.toLong() ?: 0L
                    val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
                    for (asset in assets) {
                        val tid = asset["tokenId"] as String
                        val amt = (asset["amount"] as? Number)?.toLong() ?: 0L
                        aggregatedAssets[tid] = aggregatedAssets.getOrDefault(tid, 0L) + amt
                    }
                    allUserBoxes.add(box)
                }
            }
            myAssets = aggregatedAssets
            myNanoerg = totalNanoerg
            // Smart box selection: pick minimum boxes needed
            userBoxes = selectMinimumBoxes(allUserBoxes, (fee * 1_000_000_000).toLong(), poolType, orderType, cfg, amount)
        } else if (addressBoxes != null && addressBoxes.size == 1) {
            // Single address with pre-fetched boxes — use all boxes
            val singleAddrBoxes = addressBoxes.values.first()
            val aggregatedAssets = mutableMapOf<String, Long>()
            var totalNanoerg = 0L
            for (box in singleAddrBoxes) {
                totalNanoerg += (box["value"] as? Number)?.toLong() ?: 0L
                val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
                for (asset in assets) {
                    val tid = asset["tokenId"] as String
                    val amt = (asset["amount"] as? Number)?.toLong() ?: 0L
                    aggregatedAssets[tid] = aggregatedAssets.getOrDefault(tid, 0L) + amt
                }
            }
            myAssets = aggregatedAssets
            myNanoerg = totalNanoerg
            userBoxes = singleAddrBoxes
        } else {
            val (a, n, b) = client.getMyAssets(senderAddress, includeUnconfirmed)
            myAssets = a
            myNanoerg = n
            userBoxes = b
        }
        if (userBoxes.isEmpty()) throw IllegalArgumentException("No boxes found for address")

        val poolNanoerg = (poolBox["value"] as? Number)?.toLong() ?: 0L
        val amountDec = BigDecimal.valueOf(amount)
        var nergToPool = 0L
        var tokensToPoolList = listOf<Map<String, Any>>()
        var poolAddr = com.piggytrade.piggytrade.protocol.NetworkConfig.SPECTRUM_ADDRESS
        val extraRequests = mutableListOf<MutableMap<String, Any>>()
        
        if (poolType == "erg") {
            val poolTokenBal = getBal(poolBox, tokenId)
            if (poolKey.equals("use", true) || poolKey.equals("dexygold", true)) {
                // LP config
                val cfgLp = if (poolKey.equals("use", true)) com.piggytrade.piggytrade.protocol.NetworkConfig.USE_CONFIG else com.piggytrade.piggytrade.protocol.NetworkConfig.DEXYGOLD_CONFIG
                poolAddr = cfgLp["pool_address"] as String
                val lpNft = cfgLp["lp_nft"] as String
                val lpSwapAddr = cfgLp["lp_swap_address"] as String
                extraRequests.add(mutableMapOf(
                    "address" to lpSwapAddr,
                    "value" to 1000000000L,
                    "assets" to listOf(mapOf("tokenId" to lpNft, "amount" to 1L)),
                    "registers" to emptyMap<String, String>()
                ))
            }
            if (orderType.equals("buy", true)) {
                val nergToSend = amountDec.multiply(BigDecimal.valueOf(1000000000)).toLong()
                val reqErg = nergToSend + feeNano
                if (myNanoerg < reqErg) throw IllegalArgumentException("Insufficient ERG. Have ${myNanoerg / 1e9}, Need ${reqErg / 1e9}")
                
                val cfgFee = (cfg["fee"] as? Number)?.toDouble() ?: 0.003
                val feeMult = BigDecimal.ONE.subtract(BigDecimal.valueOf(cfgFee))
                val ergAmm = BigDecimal.valueOf(nergToSend).multiply(feeMult).toLong()
                val (delta, _, _) = Amm.buyToken(ergAmm, poolNanoerg, poolTokenBal)
                
                tokensToPoolList = listOf(mapOf("tokenId" to tokenId, "amount" to -delta.toLong()))
                nergToPool = nergToSend
            } else if (orderType.equals("sell", true)) {
                val dec = (cfg["dec"] as? Number)?.toInt() ?: 0
                val tokenAmt = amountDec.multiply(BigDecimal.TEN.pow(dec)).toLong()
                val haveTokens = myAssets[tokenId] ?: 0L
                if (haveTokens < tokenAmt) throw IllegalArgumentException("Insufficient Tokens")
                
                val cfgFee = (cfg["fee"] as? Number)?.toDouble() ?: 0.003
                val (ergOut, _, _, _) = Amm.sellToken(tokenAmt, poolNanoerg, poolTokenBal)
                val feeMult = BigDecimal.ONE.subtract(BigDecimal.valueOf(cfgFee))
                val ergReceived = BigDecimal.valueOf(ergOut).multiply(feeMult).toLong()
                
                tokensToPoolList = listOf(mapOf("tokenId" to tokenId, "amount" to tokenAmt))
                nergToPool = -ergReceived
            }
        } else if (poolType == "token") {
            poolAddr = com.piggytrade.piggytrade.protocol.NetworkConfig.SPECTRUM_TOKEN_ADDRESS
            val tidX = cfg["id_in"] as? String ?: ""
            val tidY = cfg["id_out"] as? String ?: ""
            val decX = (cfg["dec_in"] as? Number)?.toInt() ?: 0
            val decY = (cfg["dec_out"] as? Number)?.toInt() ?: 0
            val poolBalX = getBal(poolBox, tidX)
            val poolBalY = getBal(poolBox, tidY)
            val cfgFee = (cfg["fee"] as? Number)?.toDouble() ?: 0.003
            if (orderType.equals("sell", true)) {
                val amtInX = amountDec.multiply(BigDecimal.TEN.pow(decX)).toLong()
                if ((myAssets[tidX] ?: 0L) < amtInX) throw IllegalArgumentException("Insufficient Base Token")
                val deltaY = Amm.tokenForToken(BigDecimal.valueOf(amtInX), BigDecimal.valueOf(poolBalX), BigDecimal.valueOf(poolBalY), BigDecimal.valueOf(cfgFee))
                tokensToPoolList = listOf(mapOf("tokenId" to tidX, "amount" to amtInX), mapOf("tokenId" to tidY, "amount" to -deltaY.toLong()))
            } else if (orderType.equals("buy", true)) {
                val amtInY = amountDec.multiply(BigDecimal.TEN.pow(decY)).toLong()
                if ((myAssets[tidY] ?: 0L) < amtInY) throw IllegalArgumentException("Insufficient Quote Token")
                val deltaX = Amm.tokenForToken(BigDecimal.valueOf(amtInY), BigDecimal.valueOf(poolBalY), BigDecimal.valueOf(poolBalX), BigDecimal.valueOf(cfgFee))
                tokensToPoolList = listOf(mapOf("tokenId" to tidY, "amount" to amtInY), mapOf("tokenId" to tidX, "amount" to -deltaX.toLong()))
            }
        }
        
        val inputIds = mutableListOf(poolBox["boxId"] as String)
        if (lpSwapBox != null) inputIds.add(lpSwapBox["boxId"] as String)
        inputIds.addAll(userBoxes.map { it["boxId"] as String })
        val inputsRaw = client.getBoxBytes(inputIds)
        
        val appFeeAddress = "9hogsADPbHLbEsQY9aEYphXBXPXuQd3EAqbCn7NA65RpW9h2D2W"
 
        var selectedNanoerg = 0L
        val selectedAssets = mutableMapOf<String, Long>()
        for (box in userBoxes) {
            selectedNanoerg += (box["value"] as? Number)?.toLong() ?: 0L
            val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
            for (asset in assets) {
                val tid = asset["tokenId"] as String
                val amt = (asset["amount"] as? Number)?.toLong() ?: 0L
                selectedAssets[tid] = selectedAssets.getOrDefault(tid, 0L) + amt
            }
        }

        val myAssetsBd = selectedAssets.mapValues { java.math.BigInteger.valueOf(it.value) }
        val appFee = ErgoSigner("").calculateAppFee(nergToPool, if (poolType == "token") 1 else 0)
 
        val r4 = cfg["R4"] as? String ?: ""
        val registers = if (r4.isNotEmpty()) mapOf("R4" to r4) else emptyMap()
 
        val txDict = builder.buildSwapTx(
            inputsRaw = inputsRaw,
            poolBox = poolBox,
            userNanoergIn = java.math.BigInteger.valueOf(selectedNanoerg),
            userAssetsIn = myAssetsBd,
            nergToPool = java.math.BigInteger.valueOf(nergToPool),
            tokensToPool = tokensToPoolList,
            poolAddress = poolAddr,
            miningFee = java.math.BigInteger.valueOf(feeNano),
            appFee = appFee,
            appFeeAddress = appFeeAddress,
            currentHeight = currentHeight,
            registers = registers,
            extraRequests = extraRequests,
            changeAddress = changeAddress
        ).toMutableMap()
        
        txDict["inputIds"] = inputIds
        
        val allBoxes = mutableListOf<Map<String, Any>>()
        poolBox.let { allBoxes.add(it) } // poolBox is non-null here
        if (lpSwapBox != null) allBoxes.add(lpSwapBox!!)
        allBoxes.addAll(userBoxes)
        txDict["input_boxes"] = allBoxes
        txDict["data_input_boxes"] = emptyList<Map<String, Any>>()
        txDict["context_extensions"] = emptyMap<String, Any>()
        
        return txDict
    }

    /**
     * Smart box selection for multi-address mode:
     * Selects the minimum number of boxes needed to cover the required ERG and token amounts.
     * Sorts boxes by value (descending) to minimize input count.
     */
    private fun selectMinimumBoxes(
        allBoxes: List<Map<String, Any>>,
        feeNano: Long,
        poolType: String,
        orderType: String,
        cfg: Map<String, Any>,
        amount: Double
    ): List<Map<String, Any>> {
        val amountDec = BigDecimal.valueOf(amount)
        
        // Calculate required ERG and tokens based on order type
        var requiredErg = feeNano + 1_000_000L // fee + minimum box value
        var requiredTokenId = ""
        var requiredTokenAmount = 0L
        
        if (poolType == "erg") {
            if (orderType.equals("buy", true)) {
                requiredErg += amountDec.multiply(BigDecimal.valueOf(1_000_000_000)).toLong()
            } else {
                val dec = (cfg["dec"] as? Number)?.toInt() ?: 0
                requiredTokenId = cfg["id"] as? String ?: ""
                requiredTokenAmount = amountDec.multiply(BigDecimal.TEN.pow(dec)).toLong()
            }
        } else if (poolType == "token") {
            if (orderType.equals("sell", true)) {
                val decX = (cfg["dec_in"] as? Number)?.toInt() ?: 0
                requiredTokenId = cfg["id_in"] as? String ?: ""
                requiredTokenAmount = amountDec.multiply(BigDecimal.TEN.pow(decX)).toLong()
            } else {
                val decY = (cfg["dec_out"] as? Number)?.toInt() ?: 0
                requiredTokenId = cfg["id_out"] as? String ?: ""
                requiredTokenAmount = amountDec.multiply(BigDecimal.TEN.pow(decY)).toLong()
            }
        }
        
        // Sort boxes: prefer boxes with the required token first, then by ERG value descending
        val sortedBoxes = allBoxes.sortedWith(compareByDescending<Map<String, Any>> { box ->
            if (requiredTokenId.isNotEmpty()) {
                val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
                assets.find { it["tokenId"] == requiredTokenId }?.let { (it["amount"] as? Number)?.toLong() ?: 0L } ?: 0L
            } else 0L
        }.thenByDescending { box ->
            (box["value"] as? Number)?.toLong() ?: 0L
        })
        
        val selected = mutableListOf<Map<String, Any>>()
        var accErg = 0L
        var accToken = 0L
        
        for (box in sortedBoxes) {
            selected.add(box)
            accErg += (box["value"] as? Number)?.toLong() ?: 0L
            if (requiredTokenId.isNotEmpty()) {
                val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
                accToken += assets.find { it["tokenId"] == requiredTokenId }?.let { (it["amount"] as? Number)?.toLong() ?: 0L } ?: 0L
            }
            
            val ergMet = accErg >= requiredErg
            val tokenMet = requiredTokenId.isEmpty() || accToken >= requiredTokenAmount
            if (ergMet && tokenMet) break
        }
        
        return selected
    }
}
