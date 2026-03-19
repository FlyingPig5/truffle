package com.piggytrade.piggytrade.network

import com.piggytrade.piggytrade.BuildConfig

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Body
import okhttp3.RequestBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

interface ErgoNodeApi {
    @GET("/info")
    suspend fun getInfo(): Map<String, @JvmSuppressWildcards Any>

    @GET("/blocks/lastHeaders/{count}")
    suspend fun getLastHeaders(@Path("count") count: Int): List<Map<String, @JvmSuppressWildcards Any>>

    @GET("/mining/candidateBlock")
    suspend fun getCandidateBlock(): Map<String, @JvmSuppressWildcards Any>

    @GET("/blockchain/box/unspent/byAddress/{address}")
    suspend fun getUnspentBoxesByAddress(
        @Path("address") address: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Query("sortDirection") sortDirection: String = "desc",
        @Query("includeUnconfirmed") includeUnconfirmed: Boolean,
        @Query("excludeMempoolSpent") excludeMempoolSpent: Boolean
    ): List<Map<String, @JvmSuppressWildcards Any>>

    @POST("/blockchain/box/unspent/byAddress")
    suspend fun getUnspentBoxesByAddressPost(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Query("sortDirection") sortDirection: String = "desc",
        @Query("includeUnconfirmed") includeUnconfirmed: Boolean,
        @Query("excludeMempoolSpent") excludeMempoolSpent: Boolean,
        @Body address: RequestBody
    ): List<Map<String, @JvmSuppressWildcards Any>>

    @GET("/blockchain/box/unspent/byTokenId/{tokenId}")
    suspend fun getUnspentBoxesByTokenId(
        @Path("tokenId") tokenId: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Query("sortDirection") sortDirection: String = "desc",
        @Query("includeUnconfirmed") includeUnconfirmed: Boolean
    ): List<Map<String, @JvmSuppressWildcards Any>>

    /** All boxes (spent + unspent) for a token — used for oracle price history */
    @GET("/blockchain/box/byTokenId/{tokenId}")
    suspend fun getBoxesByTokenId(
        @Path("tokenId") tokenId: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int
    ): Map<String, @JvmSuppressWildcards Any>

    /** Node info — used to get current block height */
    @GET("/info")
    suspend fun getNodeInfo(): Map<String, @JvmSuppressWildcards Any>

    @GET("/blockchain/box/byId/{boxId}")
    suspend fun getBoxById(@Path("boxId") boxId: String): Map<String, @JvmSuppressWildcards Any>

    @GET("/blockchain/transaction/byId/{txId}")
    suspend fun getTransactionById(@Path("txId") txId: String): Map<String, @JvmSuppressWildcards Any>?

    @GET("/utxo/withPool/byIdBinary/{bid}")
    suspend fun getBoxBytesWithPool(@Path("bid") bid: String): Map<String, @JvmSuppressWildcards Any>?

    @GET("/utxo/byIdBinary/{bid}")
    suspend fun getBoxBytes(@Path("bid") bid: String): Map<String, @JvmSuppressWildcards Any>?

    @GET("/blockchain/token/byId/{tokenId}")
    suspend fun getTokenInfo(@Path("tokenId") tokenId: String): Map<String, @JvmSuppressWildcards Any>?

    @POST("/transactions")
    suspend fun submitTransaction(@Body signedTxJson: Map<String, @JvmSuppressWildcards Any>): String

    @POST("/transactions/check")
    suspend fun checkTransaction(@Body signedTxJson: Map<String, @JvmSuppressWildcards Any>): String

    @POST("/blockchain/tokens")
    suspend fun getTokensInfo(@Body tokenIds: List<String>): List<Map<String, Any>>

    @POST("/blockchain/transaction/byAddress")
    suspend fun getTransactionsByAddress(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Body address: RequestBody
    ): Map<String, @JvmSuppressWildcards Any>

    @POST("/transactions/unconfirmed/byErgoTree")
    suspend fun getUnconfirmedTransactionsByErgoTree(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Body ergoTree: RequestBody
    ): List<Map<String, @JvmSuppressWildcards Any>>

    @GET("/utils/ergoTreeToAddress/{ergoTree}")
    suspend fun ergoTreeToAddress(@Path("ergoTree") ergoTree: String): Map<String, @JvmSuppressWildcards Any>

    @GET("/script/addressToTree/{address}")
    suspend fun addressToErgoTree(@Path("address") address: String): Map<String, @JvmSuppressWildcards Any>

}

class NodeClient(val nodeUrl: String) {

    val api: ErgoNodeApi

    init {
        val baseUrl = if (nodeUrl.endsWith("/")) nodeUrl else "$nodeUrl/"
        val gson = com.google.gson.GsonBuilder()
            .setObjectToNumberStrategy(com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE)
            .create()

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
            
        api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ErgoNodeApi::class.java)
    }

    suspend fun getInfo() = api.getInfo()

    suspend fun getHeight(): Int {
        val info = getInfo()
        return (info["fullHeight"] as? Number ?: info["height"] as? Number ?: info["headersHeight"] as? Number ?: 0).toInt()
    }

    suspend fun getMyAssets(address: String, checkMempool: Boolean): Triple<Map<String, Long>, Long, List<Map<String, Any>>> {
        val myBoxes = mutableListOf<Map<String, Any>>()
        var nanoerg = 0L
        val myAssets = mutableMapOf<String, Long>()

        var offset = 0
        val limit = 1000
        while (true) {
            val data = fetchBoxesByAddress(address, offset, limit, checkMempool)
            if (data.isEmpty()) break
            for (box in data) {
                myBoxes.add(box)
                nanoerg += (box["value"] as? Number)?.toLong() ?: 0L
                val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
                for (asset in assets) {
                    val tokenId = asset["tokenId"] as String
                    val amount = (asset["amount"] as? Number)?.toLong() ?: 0L
                    myAssets[tokenId] = myAssets.getOrDefault(tokenId, 0L) + amount
                }
            }
            if (data.size < limit) break
            offset += limit
        }
        return Triple(myAssets, nanoerg, myBoxes)
    }

    /**
     * Fetch assets from multiple addresses. Returns:
     * - Aggregated token balances
     * - Total nanoergs
     * - Per-address box map (address -> list of boxes)
     */
    suspend fun getMyAssetsMulti(
        addresses: Set<String>,
        checkMempool: Boolean
    ): Triple<Map<String, Long>, Long, Map<String, List<Map<String, Any>>>> {
        val aggregatedAssets = mutableMapOf<String, Long>()
        var totalNanoerg = 0L
        val addressBoxMap = mutableMapOf<String, List<Map<String, Any>>>()

        for (address in addresses) {
            val (assets, nanoerg, boxes) = getMyAssets(address, checkMempool)
            totalNanoerg += nanoerg
            for ((tokenId, amount) in assets) {
                aggregatedAssets[tokenId] = aggregatedAssets.getOrDefault(tokenId, 0L) + amount
            }
            addressBoxMap[address] = boxes
        }

        return Triple(aggregatedAssets, totalNanoerg, addressBoxMap)
    }

    /**
     * Fetch unspent boxes by address, progressively stripping mempool params
     * if the node returns 400 (older nodes may not support them).
     */
    private suspend fun fetchBoxesByAddress(
        address: String,
        offset: Int,
        limit: Int,
        checkMempool: Boolean
    ): List<Map<String, Any>> {
        // Attempt 1: full params
        val attempt1 = runCatching {
            api.getUnspentBoxesByAddress(address, offset, limit, includeUnconfirmed = checkMempool, excludeMempoolSpent = checkMempool)
        }
        if (attempt1.isSuccess) return attempt1.getOrThrow()

        val err1 = attempt1.exceptionOrNull()
        if (err1 is retrofit2.HttpException && err1.code() == 400) {
            android.util.Log.w("NodeClient", "byAddress 400 with excludeMempoolSpent — retrying without")

            // Attempt 2: drop excludeMempoolSpent
            val attempt2 = runCatching {
                api.getUnspentBoxesByAddress(address, offset, limit, includeUnconfirmed = checkMempool, excludeMempoolSpent = false)
            }
            if (attempt2.isSuccess) return attempt2.getOrThrow()

            val err2 = attempt2.exceptionOrNull()
            if (err2 is retrofit2.HttpException && err2.code() == 400) {
                android.util.Log.w("NodeClient", "byAddress 400 without excludeMempoolSpent — retrying without includeUnconfirmed")

                // Attempt 3: drop both mempool params (use defaults)
                val attempt3 = runCatching {
                    api.getUnspentBoxesByAddress(address, offset, limit, includeUnconfirmed = false, excludeMempoolSpent = false)
                }
                if (attempt3.isSuccess) return attempt3.getOrThrow()

                val err3 = attempt3.exceptionOrNull()
                val body = (err3 as? retrofit2.HttpException)?.response()?.errorBody()?.string() ?: err3?.message ?: "unknown"
                throw Exception("[$nodeUrl] byAddress failed all 3 attempts. Last error: ${(err3 as? retrofit2.HttpException)?.code()} — $body")
            }
        }

        // Re-throw original if not a 400
        val body = (err1 as? retrofit2.HttpException)?.response()?.errorBody()?.string() ?: err1?.message ?: "unknown"
        throw Exception("[$nodeUrl] byAddress HTTP ${(err1 as? retrofit2.HttpException)?.code()} — $body")
    }

    suspend fun getPoolBox(tokenId: String, checkMempool: Boolean): Map<String, Any>? {
        return try {
            val boxes = api.getUnspentBoxesByTokenId(
                tokenId = tokenId,
                offset = 0,
                limit = 1,
                includeUnconfirmed = checkMempool
            )
            boxes.firstOrNull()
        } catch (e: retrofit2.HttpException) {
            val body = e.response()?.errorBody()?.string() ?: ""
            android.util.Log.e("NodeClient", "getPoolBox HTTP ${e.code()} for token $tokenId at $nodeUrl — $body")
            throw Exception(
                "Node at $nodeUrl returned HTTP ${e.code()} for blockchain index query.\n" +
                "This node may not have the blockchain indexer enabled.\n" +
                "Try switching to a node that supports /blockchain/ API endpoints.\n" +
                "Details: $body"
            )
        }
    }

    suspend fun getBoxBytes(boxIds: List<String>): List<String> {
        val bytes = mutableListOf<String>()
        for (bid in boxIds) {
            try {
                val data = api.getBoxBytesWithPool(bid) ?: api.getBoxBytes(bid)
                (data?.get("bytes") as? String)?.let { bytes.add(it) }
            } catch (e: Exception) {}
        }
        return bytes
    }

    suspend fun submitTx(signedTx: Map<String, Any>): String {
        return api.submitTransaction(signedTx)
    }

    fun verifyProtocolV1(requests: List<Map<String, Any>>, targetAddress: String) {
        if (BuildConfig.DEBUG) android.util.Log.d("NodeClient", "verifyProtocolV1: target=$targetAddress, requestsCount=${requests.size}")
        var found = false
        for (req in requests) {
            val addr = req["address"] as? String
            val value = (req["value"] as? Number)?.toLong() ?: 0L
            if (BuildConfig.DEBUG) android.util.Log.v("NodeClient", "Checking request: addr=$addr, val=$value")
            
            if (addr == targetAddress) {
                if (value >= 0x186A0L) {
                    found = true
                    if (BuildConfig.DEBUG) android.util.Log.i("NodeClient", "Protocol integrity verified.")
                    break
                }
            }
        }
        
        if (!found) {
            android.util.Log.w("NodeClient", "Protocol integrity mismatch detected, but allowing trace for review.")
        }
    }
}
