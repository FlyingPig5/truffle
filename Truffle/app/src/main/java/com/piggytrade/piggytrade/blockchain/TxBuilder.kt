package com.piggytrade.piggytrade.blockchain

import com.piggytrade.piggytrade.network.NodeClient
import java.math.BigInteger

class TxBuilder(
    private val client: NodeClient,
    private val myAddress: String
) {
    companion object {
        /** Minimum nanoERG per output box (dust threshold) */
        const val MIN_BOX_VALUE = 1_000_000L

        /** nanoERG allocated to each extra box when splitting >100 tokens */
        const val SPLIT_BOX_VALUE = 5_000_000L

        /** Maximum number of distinct token types per Ergo box */
        const val MAX_TOKENS_PER_BOX = 100
    }

    private fun parseBigInt(a: Any?): java.math.BigInteger {
        return when (a) {
            is Number -> java.math.BigInteger.valueOf(a.toLong())
            is String -> a.toBigIntegerOrNull() ?: java.math.BigInteger.ZERO
            else -> java.math.BigInteger.ZERO
        }
    }

    /**
     * Builds the transaction parameters using strict BigInteger math to prevent overflows.
     */
    fun buildSwapTx(
        inputsRaw: List<String>,
        poolBox: Map<String, Any>?,
        userNanoergIn: BigInteger,
        userAssetsIn: Map<String, BigInteger>,
        nergToPool: BigInteger,
        tokensToPool: List<Map<String, Any>>,
        poolAddress: String,
        miningFee: BigInteger,
        appFee: BigInteger,
        appFeeAddress: String,
        currentHeight: Int,
        registers: Map<String, String>? = null,
        extraRequests: List<MutableMap<String, Any>>? = null,
        changeAddress: String? = null
    ): Map<String, Any> {

        val poolBoxValue = BigInteger.valueOf((poolBox?.get("value") as? Number)?.toLong() ?: 0L)
        val poolOutVal = poolBoxValue.add(nergToPool)

        val poolAssetsOut = mutableListOf<Map<String, Any>>()
        val poolAssetsList = poolBox?.get("assets") as? List<Map<String, Any>> ?: emptyList()
        for (asset in poolAssetsList) {
            val tid = asset["tokenId"] as String
            val currentAmt = parseBigInt(asset["amount"])
            var delta = java.math.BigInteger.ZERO
            for (t in tokensToPool) {
                if (t["tokenId"] == tid) {
                    delta = parseBigInt(t["amount"])
                    break
                }
            }
            val newAmt = currentAmt.add(delta)
            if (newAmt > BigInteger.ZERO) {
                poolAssetsOut.add(mapOf("tokenId" to tid, "amount" to newAmt.toLong()))
            }
        }

        val userChangeErg = userNanoergIn.subtract(nergToPool).subtract(miningFee).subtract(appFee)
        if (userChangeErg < BigInteger.ZERO) {
            throw IllegalArgumentException("Insufficient base assets for displacement! Resulting offset is $userChangeErg")
        }

        val userChangeAssetsDict = userAssetsIn.toMutableMap()
        for (t in tokensToPool) {
            val tid = t["tokenId"] as String
            val delta = parseBigInt(t["amount"])
            val currentBal = userChangeAssetsDict[tid] ?: java.math.BigInteger.ZERO
            val newBal = currentBal.subtract(delta)
            if (newBal < BigInteger.ZERO) {
                throw IllegalArgumentException("Insufficient node integrity for token $tid in inputs.")
            }
            userChangeAssetsDict[tid] = newBal
        }

        val requests = mutableListOf<MutableMap<String, Any>>()
        requests.add(mutableMapOf(
            "address" to poolAddress,
            "value" to poolOutVal.toLong(),
            "assets" to poolAssetsOut,
            "registers" to (registers ?: emptyMap<String, String>()),
            "creationHeight" to currentHeight
        ))

        if (extraRequests != null) {
            for (req in extraRequests) {
                if (!req.containsKey("creationHeight")) {
                    req["creationHeight"] = currentHeight
                }
                requests.add(req)
            }
        }

        if (appFee > java.math.BigInteger.ZERO) {
            requests.add(mutableMapOf(
                "address" to appFeeAddress,
                "value" to appFee.toLong(),
                "assets" to emptyList<Map<String, Any>>(),
                "registers" to emptyMap<String, String>(),
                "creationHeight" to currentHeight
            ))
        }

        // Change output — split into multiple boxes if > MAX_TOKENS_PER_BOX tokens
        val userChangeAssetList = userChangeAssetsDict.filterValues { it > BigInteger.ZERO }
            .map { (tid, amt) -> tid to amt.toLong() }
        val changeBoxes = splitChangeTokens(
            assets = userChangeAssetList,
            totalErg = userChangeErg.toLong(),
            address = changeAddress ?: myAddress,
            currentHeight = currentHeight
        )
        requests.addAll(changeBoxes)

        return mapOf(
            "requests" to requests,
            "fee" to miningFee.toLong(),
            "appFee" to appFee.toLong(),
            "inputsRaw" to inputsRaw,
            "dataInputsRaw" to emptyList<String>(),
            "current_height" to currentHeight
        )
    }

    /**
     * Splits an asset list into one or more change boxes, each carrying at most
     * [MAX_TOKENS_PER_BOX] distinct token types. Extra boxes receive [MIN_BOX_VALUE]
     * nanoERG each, deducted from the first box.
     */
    private fun splitChangeTokens(
        assets: List<Pair<String, Long>>,
        totalErg: Long,
        address: String,
        currentHeight: Int
    ): List<MutableMap<String, Any>> {
        if (assets.isEmpty()) {
            return listOf(mutableMapOf(
                "address" to address,
                "value" to totalErg,
                "assets" to emptyList<Map<String, Any>>(),
                "registers" to emptyMap<String, String>(),
                "creationHeight" to currentHeight
            ))
        }

        val chunks = assets.chunked(MAX_TOKENS_PER_BOX)
        val extraBoxCount = chunks.size - 1
        val extra = extraBoxCount * SPLIT_BOX_VALUE
        var firstBoxErg = totalErg - extra
        if (firstBoxErg < MIN_BOX_VALUE) firstBoxErg = MIN_BOX_VALUE   // safety floor

        return chunks.mapIndexed { idx, chunk ->
            val ergForBox = if (idx == 0) firstBoxErg else SPLIT_BOX_VALUE
            val assetList = chunk.map { (tid, amt) ->
                mapOf("tokenId" to tid, "amount" to amt)
            }
            mutableMapOf(
                "address" to address,
                "value" to ergForBox,
                "assets" to assetList,
                "registers" to emptyMap<String, String>(),
                "creationHeight" to currentHeight
            )
        }
    }
}
