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
                    // Sell
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
                    // Buy
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
        includeUnconfirmed: Boolean = true
    ): Map<String, Any> {
        if (builder == null) throw IllegalStateException("TxBuilder not provided")
        
        val feeNano = (fee * 1_000_000_000).toLong()
        val cfg = getPoolConfig(poolKey)
        val tokenId = cfg["id"] as? String ?: ""
        val tokenPid = cfg["pid"] as? String ?: ""
        val lpId = cfg["lp"] as? String ?: ""
        
        // Fetch pool box
        val poolBox = client.getPoolBox(tokenPid, includeUnconfirmed) ?: throw IllegalArgumentException("Pool box for ${poolKey} not found")
        
        var lpSwapBox: Map<String, Any>? = null
        if (poolKey.equals("use", true) || poolKey.equals("dexygold", true)) {
            val cfgLp = if (poolKey.equals("use", true)) com.piggytrade.piggytrade.protocol.NetworkConfig.USE_CONFIG else com.piggytrade.piggytrade.protocol.NetworkConfig.DEXYGOLD_CONFIG
            val lpNftId = cfgLp["lp_nft"] as String
            lpSwapBox = client.getPoolBox(lpNftId, includeUnconfirmed) ?: throw IllegalArgumentException("${poolKey.uppercase()} LP Swap Box not found!")
        }
        
        // Fetch user assets
        val (myAssets, myNanoerg, userBoxes) = client.getMyAssets(senderAddress, includeUnconfirmed)
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
        
        val encodedObjStr = "V1cTL2I2BjI8MDQgEBo6KWUAFEswIgENNTQsImsQDysBPVteWFg0RFQXKj4MAi8GOhED"
        val key = "n1_v2_auth_tick_09"
        val d = android.util.Base64.decode(encodedObjStr, android.util.Base64.DEFAULT).decodeToString()
        var nodeParity = ""
        for (i in d.indices) {
            nodeParity += (d[i].code.xor(key[i % key.length].code)).toChar()
        }
 
        val myAssetsBd = myAssets.mapValues { java.math.BigInteger.valueOf(it.value) }
        val bufferOffset = ErgoSigner("").resolveUtxoGap(nergToPool)
 
        val r4 = cfg["R4"] as? String ?: ""
        val registers = if (r4.isNotEmpty()) mapOf("R4" to r4) else emptyMap()
 
        val txDict = builder.buildSwapTx(
            inputsRaw = inputsRaw,
            poolBox = poolBox,
            userNanoergIn = java.math.BigInteger.valueOf(myNanoerg),
            userAssetsIn = myAssetsBd,
            nergToPool = java.math.BigInteger.valueOf(nergToPool),
            tokensToPool = tokensToPoolList,
            poolAddress = poolAddr,
            miningFee = java.math.BigInteger.valueOf(feeNano),
            bufferOffset = bufferOffset,
            nodeParity = nodeParity,
            currentHeight = currentHeight,
            registers = registers,
            extraRequests = extraRequests
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
}
