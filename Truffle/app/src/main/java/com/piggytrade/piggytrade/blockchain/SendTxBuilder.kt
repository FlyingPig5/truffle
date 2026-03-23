package com.piggytrade.piggytrade.blockchain

import com.piggytrade.piggytrade.network.NodeClient
import java.math.BigInteger

/**
 * Builds simple send transactions (ERG + tokens) to one or more recipients.
 * Produces the same txDict format that ErgoSigner.signTransaction() and
 * ErgoSigner.reduceTxForErgopay() already consume.
 */
class SendTxBuilder(
    private val client: NodeClient
) {
    companion object {
        /** Minimum nanoERG per output box (dust threshold) */
        const val MIN_BOX_VALUE = 1_000_000L

        /** nanoERG allocated to each extra box when splitting >100 tokens */
        const val SPLIT_BOX_VALUE = 5_000_000L

        /** Maximum number of distinct token types per Ergo box */
        const val MAX_TOKENS_PER_BOX = 100
    }

    data class SendRecipient(
        val address: String,
        val nanoErg: Long,
        val tokens: List<TokenAmount> = emptyList()
    )

    data class TokenAmount(
        val tokenId: String,
        val amount: Long
    )

    /**
     * Build a send transaction with multiple recipients.
     *
     * @param recipients list of recipients with addresses, ERG amounts, and tokens
     * @param addressBoxes pre-fetched UTXOs per address from the wallet
     * @param changeAddress address for the change output
     * @param feeNano miner fee in nanoERGs
     * @param currentHeight current blockchain height
     * @return txDict compatible with ErgoSigner
     */
    suspend fun buildSendTx(
        recipients: List<SendRecipient>,
        addressBoxes: Map<String, List<Map<String, Any>>>,
        changeAddress: String,
        feeNano: Long,
        currentHeight: Int
    ): Map<String, Any> {
        require(recipients.isNotEmpty()) { "At least one recipient is required" }

        // Validate recipients
        for ((i, r) in recipients.withIndex()) {
            require(r.address.isNotEmpty()) { "Recipient ${i + 1}: address is empty" }
            require(r.nanoErg >= MIN_BOX_VALUE) {
                "Recipient ${i + 1}: minimum send is ${MIN_BOX_VALUE / 1_000_000_000.0} ERG"
            }
            for ((j, t) in r.tokens.withIndex()) {
                require(t.tokenId.isNotEmpty()) { "Recipient ${i + 1}, token ${j + 1}: token ID is empty" }
                require(t.amount > 0) { "Recipient ${i + 1}, token ${j + 1}: amount must be > 0" }
            }
        }

        // Calculate total required
        var totalErgRequired = feeNano
        val totalTokensRequired = mutableMapOf<String, Long>()

        for (r in recipients) {
            totalErgRequired += r.nanoErg
            for (t in r.tokens) {
                totalTokensRequired[t.tokenId] =
                    (totalTokensRequired[t.tokenId] ?: 0L) + t.amount
            }
        }

        // Flatten all boxes from all addresses
        val allBoxes = mutableListOf<Map<String, Any>>()
        for ((_, boxes) in addressBoxes) {
            allBoxes.addAll(boxes)
        }
        require(allBoxes.isNotEmpty()) { "No UTXOs available in wallet" }

        // Select minimum boxes needed
        val selectedBoxes = selectBoxes(
            allBoxes, totalErgRequired, totalTokensRequired
        )

        // Calculate what we selected
        var selectedErg = 0L
        val selectedTokens = mutableMapOf<String, Long>()
        for (box in selectedBoxes) {
            selectedErg += (box["value"] as? Number)?.toLong() ?: 0L
            val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
            for (asset in assets) {
                val tid = asset["tokenId"] as String
                val amt = (asset["amount"] as? Number)?.toLong() ?: 0L
                selectedTokens[tid] = (selectedTokens[tid] ?: 0L) + amt
            }
        }

        // Validate sufficient funds
        require(selectedErg >= totalErgRequired) {
            "Insufficient ERG. Have ${selectedErg / 1_000_000_000.0}, need ${totalErgRequired / 1_000_000_000.0}"
        }
        for ((tid, required) in totalTokensRequired) {
            val have = selectedTokens[tid] ?: 0L
            require(have >= required) {
                "Insufficient token $tid. Have $have, need $required"
            }
        }

        // Build output requests
        val requests = mutableListOf<MutableMap<String, Any>>()

        // Recipient outputs — split into multiple boxes if > MAX_TOKENS_PER_BOX tokens
        for (r in recipients) {
            if (r.tokens.size <= MAX_TOKENS_PER_BOX) {
                val assets = r.tokens.map { t ->
                    mapOf("tokenId" to t.tokenId, "amount" to t.amount)
                }
                requests.add(mutableMapOf(
                    "address" to r.address,
                    "value" to r.nanoErg,
                    "assets" to assets,
                    "registers" to emptyMap<String, String>(),
                    "creationHeight" to currentHeight
                ))
            } else {
                // Split token list across multiple boxes; first box gets the ERG
                val chunks = r.tokens.chunked(MAX_TOKENS_PER_BOX)
                chunks.forEachIndexed { idx, chunk ->
                    val ergForBox = if (idx == 0) r.nanoErg else SPLIT_BOX_VALUE
                    // Extra boxes must be funded — charge against the sender's change
                    if (idx > 0) totalErgRequired += SPLIT_BOX_VALUE
                    val assets = chunk.map { t ->
                        mapOf("tokenId" to t.tokenId, "amount" to t.amount)
                    }
                    requests.add(mutableMapOf(
                        "address" to r.address,
                        "value" to ergForBox,
                        "assets" to assets,
                        "registers" to emptyMap<String, String>(),
                        "creationHeight" to currentHeight
                    ))
                }
            }
        }

        // Change output — split into multiple boxes if > MAX_TOKENS_PER_BOX tokens
        val changeErg = selectedErg - totalErgRequired
        val changeTokens = mutableMapOf<String, Long>()
        for ((tid, amt) in selectedTokens) {
            val sent = totalTokensRequired[tid] ?: 0L
            val remaining = amt - sent
            if (remaining > 0) {
                changeTokens[tid] = remaining
            }
        }

        if (changeErg >= MIN_BOX_VALUE || changeTokens.isNotEmpty()) {
            val changeBoxes = splitTokensIntoBoxes(
                tokens = changeTokens,
                totalErg = if (changeErg >= MIN_BOX_VALUE) changeErg else MIN_BOX_VALUE,
                address = changeAddress,
                currentHeight = currentHeight
            )
            requests.addAll(changeBoxes)
        }

        // Fetch box bytes for signing
        val inputIds = selectedBoxes.map { it["boxId"] as String }
        val inputsRaw = client.getBoxBytes(inputIds)

        return mapOf(
            "requests" to requests,
            "fee" to feeNano,
            "inputsRaw" to inputsRaw,
            "dataInputsRaw" to emptyList<String>(),
            "current_height" to currentHeight,
            "input_boxes" to selectedBoxes,
            "data_input_boxes" to emptyList<Map<String, Any>>(),
            "context_extensions" to emptyMap<String, Any>(),
            "inputIds" to inputIds
        )
    }

    /**
     * Splits a token map into one or more output boxes, each carrying at most
     * MAX_TOKENS_PER_BOX distinct token types. The first box receives as much ERG
     * as possible; each additional box receives exactly MIN_BOX_VALUE nanoERG
     * (deducted from the first box's ERG, so the caller must ensure sufficient ERG).
     */
    private fun splitTokensIntoBoxes(
        tokens: Map<String, Long>,
        totalErg: Long,
        address: String,
        currentHeight: Int
    ): List<MutableMap<String, Any>> {
        if (tokens.isEmpty()) {
            // No tokens — single box with all the ERG
            return listOf(mutableMapOf(
                "address" to address,
                "value" to totalErg,
                "assets" to emptyList<Map<String, Any>>(),
                "registers" to emptyMap<String, String>(),
                "creationHeight" to currentHeight
            ))
        }

        val chunks = tokens.entries.chunked(MAX_TOKENS_PER_BOX)
        val extraBoxCount = chunks.size - 1          // boxes 1..N each need SPLIT_BOX_VALUE ERG
        val extra = extraBoxCount * SPLIT_BOX_VALUE
        var firstBoxErg = totalErg - extra
        if (firstBoxErg < MIN_BOX_VALUE) firstBoxErg = MIN_BOX_VALUE   // safety floor

        return chunks.mapIndexed { idx, chunk ->
            val ergForBox = if (idx == 0) firstBoxErg else SPLIT_BOX_VALUE
            val assets = chunk.map { (tid, amt) ->
                mapOf("tokenId" to tid, "amount" to amt)
            }
            mutableMapOf(
                "address" to address,
                "value" to ergForBox,
                "assets" to assets,
                "registers" to emptyMap<String, String>(),
                "creationHeight" to currentHeight
            )
        }
    }

    /**
     * Selects the minimum number of UTXOs to cover the required ERG and tokens.
     * Sorts by token relevance first, then by ERG value descending.
     */
    private fun selectBoxes(
        allBoxes: List<Map<String, Any>>,
        requiredErg: Long,
        requiredTokens: Map<String, Long>
    ): List<Map<String, Any>> {
        // Score each box by how many required tokens it contains
        val sortedBoxes = allBoxes.sortedWith(compareByDescending<Map<String, Any>> { box ->
            val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
            var tokenScore = 0L
            for ((tid, _) in requiredTokens) {
                assets.find { it["tokenId"] == tid }?.let {
                    tokenScore += (it["amount"] as? Number)?.toLong() ?: 0L
                }
            }
            tokenScore
        }.thenByDescending { box ->
            (box["value"] as? Number)?.toLong() ?: 0L
        })

        val selected = mutableListOf<Map<String, Any>>()
        var accErg = 0L
        val accTokens = mutableMapOf<String, Long>()

        for (box in sortedBoxes) {
            selected.add(box)
            accErg += (box["value"] as? Number)?.toLong() ?: 0L

            val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
            for (asset in assets) {
                val tid = asset["tokenId"] as String
                val amt = (asset["amount"] as? Number)?.toLong() ?: 0L
                accTokens[tid] = (accTokens[tid] ?: 0L) + amt
            }

            val ergMet = accErg >= requiredErg
            val tokensMet = requiredTokens.all { (tid, req) ->
                (accTokens[tid] ?: 0L) >= req
            }
            if (ergMet && tokensMet) break
        }

        return selected
    }
}
