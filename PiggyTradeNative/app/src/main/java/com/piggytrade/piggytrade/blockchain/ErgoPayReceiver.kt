package com.piggytrade.piggytrade.blockchain

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles incoming ErgoPay URLs per EIP-0020.
 *
 * Three flows:
 * 1. Static/opaque:  "ergopay:<base64-reduced-tx>"
 * 2. Dynamic TX:     "ergopay://host/path" → GET → JSON with reducedTx
 * 3. Connect wallet: URL has #P2PK_ADDRESS# → replace → GET → may or may not have reducedTx
 */
class ErgoPayReceiver {

    data class ErgoPayResult(
        val reducedTxBase64: String?,      // null if connect-only
        val originalUrl: String,
        val isUrlBased: Boolean,
        val isConnectRequest: Boolean = false,
        val message: String? = null,
        val messageSeverity: String? = null,
        val replyToUrl: String? = null,
        val addresses: List<String> = emptyList()  // EIP-0020: dApp-specified address(es)
    )

    /** Check if URL needs an address substituted before fetching */
    fun isAddressRequest(url: String): Boolean {
        val t = url.trim()
        return t.contains("#P2PK_ADDRESS#") || t.contains("%23P2PK_ADDRESS%23")
    }

    /** Is this a static (opaque) request with inline reduced TX? */
    private fun isStaticRequest(url: String): Boolean {
        val t = url.trim()
        return t.startsWith("ergopay:", ignoreCase = true) && !t.startsWith("ergopay://", ignoreCase = true)
    }

    suspend fun parseErgoPayUrl(url: String, walletAddress: String = ""): ErgoPayResult {
        val trimmed = url.trim()

        return when {
            // Static/opaque: "ergopay:<base64>"
            isStaticRequest(trimmed) -> {
                val payload = trimmed.removePrefix("ergopay:")
                ErgoPayResult(
                    reducedTxBase64 = normalizeBase64(payload),
                    originalUrl = trimmed,
                    isUrlBased = false
                )
            }

            // Dynamic: "ergopay://host/..."
            trimmed.startsWith("ergopay://", ignoreCase = true) -> {
                val replaced = substituteAddress(trimmed, walletAddress)
                // Use http for local/IP addresses, https otherwise
                val httpUrl = if (isLocalOrIp(replaced)) {
                    replaced.replaceFirst("ergopay://", "http://", ignoreCase = true)
                } else {
                    replaced.replaceFirst("ergopay://", "https://", ignoreCase = true)
                }
                Log.d("ErgoPayReceiver", "Fetching: $httpUrl")
                fetchErgoPayContent(httpUrl, trimmed)
            }

            // Already HTTP/HTTPS
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> {
                val replaced = substituteAddress(trimmed, walletAddress)
                Log.d("ErgoPayReceiver", "Fetching: $replaced")
                fetchErgoPayContent(replaced, trimmed)
            }

            else -> throw IllegalArgumentException("Invalid ErgoPay URL: $trimmed")
        }
    }

    /**
     * Send reply to dApp after successful signing (fire & forget).
     * Per EIP-0020: POST {"txId": "..."} to the replyTo URL.
     */
    suspend fun sendReplyToDApp(replyToUrl: String, txId: String) {
        withContext(Dispatchers.IO) {
            try {
                val json = JsonObject().apply { addProperty("txId", txId) }
                val body = json.toString()

                val connection = java.net.URL(replyToUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }

                val code = connection.responseCode
                Log.d("ErgoPayReceiver", "replyTo POST to $replyToUrl → HTTP $code")
                connection.disconnect()
            } catch (e: Exception) {
                Log.w("ErgoPayReceiver", "replyTo POST failed: ${e.message}")
            }
        }
    }

    // ── Private helpers ──

    private fun substituteAddress(url: String, address: String): String {
        var result = url
        if (result.contains("#P2PK_ADDRESS#")) {
            if (address.isEmpty()) throw IllegalArgumentException("This ErgoPay request requires a wallet address")
            result = result.replace("#P2PK_ADDRESS#", address)
        }
        if (result.contains("%23P2PK_ADDRESS%23")) {
            if (address.isEmpty()) throw IllegalArgumentException("This ErgoPay request requires a wallet address")
            result = result.replace("%23P2PK_ADDRESS%23", address)
        }
        return result
    }

    private fun isLocalOrIp(url: String): Boolean {
        val afterScheme = url.substringAfter("://").substringBefore("/").substringBefore(":")
        return afterScheme == "localhost" || afterScheme == "127.0.0.1"
                || afterScheme == "10.0.2.2"
                || afterScheme.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))
    }

    private suspend fun fetchErgoPayContent(url: String, originalUrl: String): ErgoPayResult {
        return withContext(Dispatchers.IO) {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            // EIP-0020 headers
            connection.setRequestProperty("ErgoPay-CanSelectMultipleAddresses", "supported")

            try {
                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    val errorBody = try {
                        connection.errorStream?.bufferedReader()?.readText() ?: ""
                    } catch (_: Exception) { "" }
                    throw Exception("HTTP $responseCode${if (errorBody.isNotEmpty()) ": $errorBody" else ""}")
                }

                val responseBody = connection.inputStream.bufferedReader().readText()
                Log.d("ErgoPayReceiver", "Response (${responseBody.length} chars): ${responseBody.take(200)}")

                try {
                    val jsonObject = JsonParser.parseString(responseBody).asJsonObject

                    val reducedTx = jsonObject.get("reducedTx")?.let { if (it.isJsonNull) null else it.asString }
                    val message = jsonObject.get("message")?.let { if (it.isJsonNull) null else it.asString }
                    val severity = jsonObject.get("messageSeverity")?.let { if (it.isJsonNull) null else it.asString }
                    val replyTo = jsonObject.get("replyTo")?.let { if (it.isJsonNull) null else it.asString }

                    // EIP-0020: dApp can specify which address(es) to use
                    val addresses = mutableListOf<String>()
                    jsonObject.get("addresses")?.let { el ->
                        if (!el.isJsonNull && el.isJsonArray) {
                            el.asJsonArray.forEach { addresses.add(it.asString) }
                        }
                    }
                    jsonObject.get("address")?.let { el ->
                        if (!el.isJsonNull && addresses.isEmpty()) {
                            addresses.add(el.asString)
                        }
                    }

                    // If severity is ERROR and no TX, it's a server error
                    if (reducedTx.isNullOrBlank() && severity == "ERROR") {
                        throw Exception("ErgoPay error: ${message ?: "Unknown error"}")
                    }

                    val isConnect = reducedTx.isNullOrBlank()

                    ErgoPayResult(
                        reducedTxBase64 = if (isConnect) null else reducedTx,
                        originalUrl = originalUrl,
                        isUrlBased = true,
                        isConnectRequest = isConnect,
                        message = message,
                        messageSeverity = severity,
                        replyToUrl = replyTo,
                        addresses = addresses
                    )
                } catch (e: com.google.gson.JsonSyntaxException) {
                    // Not JSON → treat as raw base64 reduced TX
                    ErgoPayResult(
                        reducedTxBase64 = responseBody.trim(),
                        originalUrl = originalUrl,
                        isUrlBased = true
                    )
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun normalizeBase64(input: String): String {
        var result = input.trim()
        while (result.length % 4 != 0) result += "="
        return result
    }
}
