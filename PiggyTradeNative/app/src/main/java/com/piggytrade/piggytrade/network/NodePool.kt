package com.piggytrade.piggytrade.network

import com.piggytrade.piggytrade.protocol.NetworkConfig
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pool of NodeClient instances for distributing read-only API calls across
 * multiple public Ergo nodes. Provides round-robin selection and automatic
 * retry-with-fallback when a node fails.
 *
 * Write operations (wallet balance, TX submission) should still use the
 * user's selected primary node, NOT this pool.
 */
class NodePool {

    private val clients: List<NodeClient>
    private val counter = AtomicInteger(0)

    init {
        clients = NetworkConfig.NODES.values.mapNotNull { config ->
            val url = config["url"] as? String ?: return@mapNotNull null
            try { NodeClient(url) } catch (_: Exception) { null }
        }
    }

    /** Number of available nodes */
    val size: Int get() = clients.size

    /** Get next client via round-robin */
    fun next(): NodeClient {
        if (clients.isEmpty()) throw IllegalStateException("No nodes available")
        return clients[counter.getAndIncrement() % clients.size]
    }

    /** Get a random client */
    fun random(): NodeClient {
        if (clients.isEmpty()) throw IllegalStateException("No nodes available")
        return clients.random()
    }

    /**
     * Execute a block with automatic retry across different nodes.
     * On failure, picks a different node and retries up to [maxRetries] times.
     */
    suspend fun <T> withRetry(maxRetries: Int = 3, block: suspend (NodeClient) -> T): T {
        var lastError: Exception? = null
        val tried = mutableSetOf<Int>()

        repeat(minOf(maxRetries, clients.size)) {
            // Pick a node we haven't tried yet
            var idx = counter.getAndIncrement() % clients.size
            var attempts = 0
            while (idx in tried && attempts < clients.size) {
                idx = counter.getAndIncrement() % clients.size
                attempts++
            }
            tried.add(idx)

            try {
                return block(clients[idx])
            } catch (e: Exception) {
                lastError = e
                // Continue to next node
            }
        }
        throw lastError ?: IllegalStateException("All nodes failed")
    }
}
