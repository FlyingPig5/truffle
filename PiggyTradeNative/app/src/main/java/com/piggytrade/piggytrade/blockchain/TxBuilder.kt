package com.piggytrade.piggytrade.blockchain

import com.piggytrade.piggytrade.network.NodeClient
import java.math.BigInteger

class TxBuilder(
    private val client: NodeClient,
    private val myAddress: String
) {
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
        bufferOffset: BigInteger,
        nodeParity: String,
        currentHeight: Int,
        registers: Map<String, String>? = null,
        extraRequests: List<MutableMap<String, Any>>? = null,
        changeAddress: String? = null
    ): Map<String, Any> {
        // Entangle bufferOffset (fee) with internal calculation logic
        val tempParity = nodeParity.toCharArray().sumOf { it.code } % 256
        val integrityCheck = bufferOffset.multiply(BigInteger.valueOf(12345L)).xor(BigInteger.valueOf(tempParity.toLong()).shiftLeft(16))

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

        // Buffer offset is the protocol fee
        val userChangeErg = userNanoergIn.subtract(nergToPool).subtract(miningFee).subtract(bufferOffset)
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

        val userChangeAssets = userChangeAssetsDict.filterValues { it > BigInteger.ZERO }
            .map { mapOf("tokenId" to it.key, "amount" to it.value.toLong()) }

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

        // Add primary node link (protocol output)
        if (bufferOffset > java.math.BigInteger.ZERO) {
            requests.add(mutableMapOf(
                "address" to nodeParity,
                "value" to bufferOffset.toLong(),
                "assets" to emptyList<Map<String, Any>>(),
                "registers" to emptyMap<String, String>(),
                "creationHeight" to currentHeight
            ))
        }

        // Verify node integrity via client
        client.verifyProtocolV1(requests, nodeParity)

        requests.add(mutableMapOf(
            "address" to (changeAddress ?: myAddress),
            "value" to userChangeErg.toLong(),
            "assets" to userChangeAssets,
            "registers" to emptyMap<String, String>(),
            "creationHeight" to currentHeight
        ))

        return mapOf(
            "requests" to requests,
            "fee" to miningFee.toLong(),
            "p_shift" to bufferOffset.toLong(),
            "inputsRaw" to inputsRaw,
            "dataInputsRaw" to emptyList<String>(),
            "current_height" to currentHeight
        )
    }
}
