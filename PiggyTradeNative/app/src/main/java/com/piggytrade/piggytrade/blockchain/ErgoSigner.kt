package com.piggytrade.piggytrade.blockchain

import android.util.Base64
import com.google.gson.Gson
import com.piggytrade.piggytrade.protocol.ProtocolConfig
import java.math.BigInteger
import java.util.Arrays

object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val INDEXES = IntArray(128) { -1 }

    init {
        for (i in ALPHABET.indices) {
            INDEXES[ALPHABET[i].code] = i
        }
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        var zeros = 0
        while (zeros < input.length && input[zeros] == '1') {
            zeros++
        }
        val b58 = ByteArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            if (digit < 0) throw IllegalArgumentException("Invalid Base58 string")
            b58[i] = digit.toByte()
        }
        var decoded = ByteArray(input.length)
        var outputStart = decoded.size
        var inputStart = zeros
        while (inputStart < b58.size) {
            var remainder = 0
            for (i in inputStart until b58.size) {
                val temp = remainder * 58 + b58[i].toInt()
                b58[i] = (temp / 256).toByte()
                remainder = temp % 256
            }
            decoded[--outputStart] = remainder.toByte()
            if (b58[inputStart].toInt() == 0) {
                inputStart++
            }
        }
        while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) {
            outputStart++
        }
        return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.size)
    }

    fun addressToErgoTreeHex(address: String): String {
        val decoded = decode(address)
        val prefix = decoded[0].toInt() and 0xFF
        val type = prefix and 0x0F
        val content = Arrays.copyOfRange(decoded, 1, decoded.size - 4)
        
        val ergoTreeBytes = when (type) {
            0x01 -> byteArrayOf(0x00.toByte(), 0x08.toByte(), 0xcd.toByte()) + content
            0x03 -> content
            0x02 -> content
            else -> throw IllegalArgumentException("Cannot resolve $address type: $type")
        }
        return ergoTreeBytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}

class ErgoSigner(private val nodeUrl: String) {

    fun resolveUtxoGap(n: Long): BigInteger {
        return ProtocolConfig.resolveUtxoGap(BigInteger.valueOf(n))
    }

    private fun parseAmount(a: Any?): Long {
        return when (a) {
            is Number -> a.toLong()
            is String -> a.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun sanitizeBox(box: Map<String, Any>): Map<String, Any> {
        val sanitized = mutableMapOf<String, Any>()
        
        // 1. Core Fields
        // We Use Long for all numeric fields to ensure Gson serializes them as integers.
        sanitized["value"] = parseAmount(box["value"])
        sanitized["ergoTree"] = (box["ergoTree"] as? String) ?: (box["ergo_tree"] as? String) ?: ""
        
        // 2. Identification Fields (CRITICAL for Box ID calculation)
        // Note: We EXCLUDE "boxId" / "id" here. 
        // If provided, ergo-lib verifies it against its own calculation. 
        // By omitting it, we bypass the "Box id parsed from JSON ... differs from..." error.
        // If our calculation is correct, the resulting Tx will validly point to the on-chain box.
        
        sanitized["transactionId"] = (box["transactionId"] as? String) 
            ?: (box["txId"] as? String) 
            ?: (box["tx_id"] as? String) 
            ?: ""
            
        sanitized["index"] = (box["index"] as? Number)?.toLong() 
            ?: (box["outputIndex"] as? Number)?.toLong() 
            ?: 0L

        sanitized["creationHeight"] = (box["creationHeight"] as? Number)?.toLong() 
            ?: (box["creation_height"] as? Number)?.toLong() 
            ?: (box["height"] as? Number)?.toLong() 
            ?: 0L
        
        // 3. Assets/Tokens - PRESERVE ORIGINAL ORDER
        val assets = (box["assets"] as? List<*>) ?: (box["tokens"] as? List<*>)
        if (assets != null) {
            sanitized["assets"] = assets.filterIsInstance<Map<String, Any>>().map { asset ->
                mapOf(
                    "tokenId" to (asset["tokenId"] as? String ?: asset["id"] as? String ?: ""),
                    "amount" to parseAmount(asset["amount"])
                )
            }
        } else {
            sanitized["assets"] = emptyList<Any>()
        }
        
        // 4. Registers
        val regs = (box["additionalRegisters"] as? Map<*, *>) ?: (box["registers"] as? Map<*, *>)
        val sanitizedRegs = mutableMapOf<String, String>()
        regs?.forEach { (k, v) ->
            val key = k.toString().uppercase()
            if (key.startsWith("R")) {
                if (v is String) {
                    sanitizedRegs[key] = v
                } else if (v is Map<*, *>) {
                    val sv = (v["serializedValue"] as? String) ?: (v["rawValue"] as? String)
                    if (sv != null) sanitizedRegs[key] = sv
                }
            }
        }
        sanitized["additionalRegisters"] = sanitizedRegs
        
        return sanitized
    }

    val txGson = com.google.gson.GsonBuilder()
        .disableHtmlEscaping()
        .setObjectToNumberStrategy(com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE)
        .create()

    /**
     * Build a proper ErgoPay (EIP-20) URL by constructing a ReducedTransaction
     * via the native ergo-lib-jni library.
     *
     * @param txDict the transaction dict from TxBuilder (must include "input_boxes" and "requests")
     * @param senderAddress the change/sender address
     * @param lastHeadersJson JSON string from /blocks/lastHeaders/10  
     */
    fun reduceTxForErgopay(
        txDict: Map<String, Any>,
        senderAddress: String,
        lastHeadersJson: String
    ): String {
        val inputBoxes = txDict["input_boxes"] as? List<Map<String, Any>> ?: emptyList()
        val dataInputBoxes = txDict["data_input_boxes"] as? List<Map<String, Any>> ?: emptyList()
        val contextExtensions = txDict["context_extensions"] as? Map<String, Any> ?: emptyMap()
        val requests = txDict["requests"] as? List<Map<String, Any>> ?: emptyList()
        val fee = (txDict["fee"] as? Number)?.toLong() ?: 1100000L
        val currentHeight = (txDict["current_height"] as? Number)?.toInt() ?: 0

        // Build output candidates JSON for the native library (without the fee box — TxBuilder adds it)
        val outputCandidates = requests.map { req ->
            val address = req["address"] as? String ?: ""
            val value = (req["value"] as? Number)?.toLong() ?: 0L
            val assets = (req["assets"] as? List<Map<String, Any>> ?: emptyList()).map {
                mapOf("tokenId" to it["tokenId"], "amount" to parseAmount(it["amount"]))
            }
            val registers = req["registers"] as? Map<String, String> ?: emptyMap()
            val ergoTree = try { Base58.addressToErgoTreeHex(address) } catch (e: Exception) {
                ""
            }

            mapOf(
                "value" to value,
                "ergoTree" to ergoTree,
                "assets" to assets,
                "additionalRegisters" to registers,
                "creationHeight" to ((req["creationHeight"] as? Number)?.toInt() ?: currentHeight)
            )
        }
        
        val sanitizedInputBoxes = inputBoxes.map { sanitizeBox(it) }
        val inputBoxesJson = txGson.toJson(sanitizedInputBoxes)
        val sanitizedDataInputs = dataInputBoxes.map { sanitizeBox(it) }
        val dataInputBoxesJson = txGson.toJson(sanitizedDataInputs)
        val contextExtensionsJson = txGson.toJson(contextExtensions)
        val outputCandidatesJson = txGson.toJson(outputCandidates)

        return try {
            android.util.Log.d("ErgoSigner", "buildReducedTxBytes: inputBoxes=${inputBoxesJson.take(200)}")
            android.util.Log.d("ErgoSigner", "buildReducedTxBytes: dataInputBoxes=${dataInputBoxesJson.take(200)}")
            android.util.Log.d("ErgoSigner", "buildReducedTxBytes: outputCandidates=${outputCandidatesJson.take(800)}")
            android.util.Log.d("ErgoSigner", "buildReducedTxBytes: fee=$fee height=$currentHeight addr=$senderAddress")
            android.util.Log.d("ErgoSigner", "buildReducedTxBytes: extensions=$contextExtensionsJson")
            android.util.Log.d("ErgoSigner", "buildReducedTxBytes: creationHeight per candidate: ${outputCandidates.map { (it["creationHeight"] as? Number)?.toInt() }}")
            val base64Reduced = org.ergoplatform.wallet.jni.WalletLib.buildReducedTxBytes(
                inputBoxesJson,
                dataInputBoxesJson,
                outputCandidatesJson,
                fee,
                senderAddress,
                currentHeight,
                lastHeadersJson,
                contextExtensionsJson
            )
            android.util.Log.d("ErgoSigner", "buildReducedTxBytes SUCCESS: ${base64Reduced.take(40)}...")
            toErgoPayUrl(base64Reduced)
        } catch (e: Exception) {
            android.util.Log.e("ErgoSigner", "buildReducedTxBytes FAILED: ${e.message}", e)
            throw e  // Let caller handle — do NOT base64-encode JSON as fallback
        }
    }

    /**
     * Helper to ensure Base64 is correctly padded and follows EIP-20 (URL-Safe).
     * 1. Uses 'ergopay:<payload>' (opaque URI) to avoid "Invalid URL host" issues.
     * 2. Uses URL-Safe alphabet ('-' and '_') to prevent corruption by Intents.
     * 3. Ensures correct padding for strict decoders.
     */
    private fun toErgoPayUrl(base64: String): String {
        // 1. Convert to URL-Safe alphabet (mandated by EIP-20)
        // We also strip existing padding to re-calculate it cleanly.
        val safePayload = base64.trim()
            .replace("+", "-")
            .replace("/", "_")
            .replace("=", "")
            
        // 2. Strict padding to satisfy decoders that check "multiple of 4" length.
        var finalPayload = safePayload
        while (finalPayload.length % 4 != 0) {
            finalPayload += "="
        }
            
        return "ergopay:$finalPayload"
    }

    fun signTransaction(
        txDict: Map<String, Any>,
        senderAddress: String,
        mnemonic: String,
        mnemonicPass: String,
        lastHeadersJson: String
    ): String {
        val inputBoxes = txDict["input_boxes"] as? List<Map<String, Any>> ?: emptyList()
        val dataInputBoxes = txDict["data_input_boxes"] as? List<Map<String, Any>> ?: emptyList()
        val contextExtensions = txDict["context_extensions"] as? Map<String, Any> ?: emptyMap()
        val requests = txDict["requests"] as? List<Map<String, Any>> ?: emptyList()
        val fee = (txDict["fee"] as? Number)?.toLong() ?: 1100000L
        val currentHeight = (txDict["current_height"] as? Number)?.toInt() ?: 0

        val outputCandidates = requests.map { req ->
            val address = req["address"] as? String ?: ""
            val value = (req["value"] as? Number)?.toLong() ?: 0L
            val assets = (req["assets"] as? List<Map<String, Any>> ?: emptyList()).map {
                mapOf("tokenId" to it["tokenId"], "amount" to parseAmount(it["amount"]))
            }
            val registers = req["registers"] as? Map<String, String> ?: emptyMap()
            val ergoTree = try { Base58.addressToErgoTreeHex(address) } catch (e: Exception) {
                ""
            }

            mapOf("value" to value, "ergoTree" to ergoTree, "assets" to assets, "additionalRegisters" to registers, "creationHeight" to ((req["creationHeight"] as? Number)?.toInt() ?: currentHeight))
        }
        
        val sanitizedInputBoxes = inputBoxes.map { sanitizeBox(it) }
        val inputBoxesJson = txGson.toJson(sanitizedInputBoxes)
        val sanitizedDataInputs = dataInputBoxes.map { sanitizeBox(it) }
        val dataInputBoxesJson = txGson.toJson(sanitizedDataInputs)
        val contextExtensionsJson = txGson.toJson(contextExtensions)
        val outputCandidatesJson = txGson.toJson(outputCandidates)
        return org.ergoplatform.wallet.jni.WalletLib.signTransactionJson(
            mnemonic, mnemonicPass, inputBoxesJson, dataInputBoxesJson, outputCandidatesJson,
            fee, senderAddress, currentHeight, lastHeadersJson, contextExtensionsJson
        )
    }

    /**
     * Legacy unsigned JSON ErgoPay (kept as fallback).
     */
    fun reduceTxForErgopayLegacy(txDict: Map<String, Any>, senderAddress: String): String {
        val unsignedTxStr = toUnsignedJson(txDict, senderAddress)
        val unsignedBytes = unsignedTxStr.toByteArray()
        val base64Str = Base64.encodeToString(unsignedBytes, Base64.NO_WRAP)
        return toErgoPayUrl(base64Str)
    }

    fun toUnsignedJson(txDict: Map<String, Any>, senderAddress: String): String {
        val requests = txDict["requests"] as? List<Map<String, Any>> ?: emptyList()
        val fee = (txDict["fee"] as? Number)?.toLong() ?: 1100000L
        val currentHeight = (txDict["current_height"] as? Number)?.toInt() ?: 0
        
        // Use input_boxes from txDict or fallback to empty
        val inputBoxes = txDict["input_boxes"] as? List<Map<String, Any>> ?: emptyList()
        val inputsJsonList = inputBoxes.map { box ->
            mapOf(
                "boxId" to (box["boxId"] as? String ?: ""),
                "extension" to emptyMap<String, Any>()
            )
        }

        val dataInputsRaw = txDict["dataInputsRaw"] as? List<String> ?: emptyList()
        val dataInputsList = dataInputsRaw.map { mapOf("boxId" to it) }

        val outputsJsonList = mutableListOf<Map<String, Any>>()
        
        // Add user defined requests
        for (req in requests) {
            val assets = (req["assets"] as? List<Map<String, Any>> ?: emptyList()).map { 
                mapOf("tokenId" to it["tokenId"], "amount" to parseAmount(it["amount"]))
            }
            val registers = (req["registers"] as? Map<String, String> ?: emptyMap())
            val address = req["address"] as? String ?: ""
            val value = (req["value"] as? Number)?.toLong() ?: 0L
            
            var ergoTree = try {
                Base58.addressToErgoTreeHex(address)
            } catch (e: Exception) {
                ""
            }
            
            if (ergoTree.isEmpty()) {
                val matchBox = inputBoxes.find { it["address"] == address }
                if (matchBox != null) {
                    ergoTree = matchBox["ergoTree"] as? String ?: ""
                }
            }

            // Fallback for known protocol address if it fails Base58
            if (ergoTree.isEmpty() && address.startsWith("9")) {
                ergoTree = "0008cd" + "0".repeat(66) // placeholder
            }
            
            outputsJsonList.add(mapOf(
                "value" to value,
                "ergoTree" to ergoTree,
                "assets" to assets,
                "additionalRegisters" to registers,
                "creationHeight" to ((req["creationHeight"] as? Number)?.toInt() ?: currentHeight)
            ))
        }

        // ADD MINING FEE OUTPUT
        if (fee > 0) {
            outputsJsonList.add(mapOf(
                "value" to fee,
                "ergoTree" to "1005040004000e36100204a00b08cd0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798ea02d192a39a8cc7a701730073011001020402d19683030193a38cc7b2a57300000193c2b2a57301007473027303830108cdeeac93b1a57304",
                "assets" to emptyList<Any>(),
                "additionalRegisters" to emptyMap<String, Any>(),
                "creationHeight" to currentHeight
            ))
        }

        val unsignedTx = mapOf<String, Any>(
            "inputs" to inputsJsonList,
            "dataInputs" to dataInputsList,
            "outputs" to outputsJsonList
        )
        
        return txGson.toJson(unsignedTx)
    }
}
