package com.piggytrade.piggytrade.network

import android.util.Log
import com.piggytrade.piggytrade.BuildConfig
import com.piggytrade.piggytrade.data.PreferenceManager
import com.piggytrade.piggytrade.protocol.NetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Owns all node configuration state and the active [NodeClient].
 *
 * Shared via [com.piggytrade.piggytrade.data.SessionManager] so every ViewModel
 * sees the same node without needing to re-read preferences individually.
 *
 * Responsibilities:
 *  - Maintain the list of configured nodes and the selected index
 *  - Build a [NodeClient] from the selected node URL
 *  - Persist node changes to [PreferenceManager]
 */
class NodeManager(private val preferenceManager: PreferenceManager) {

    private val TAG = "NodeManager"

    // ─── Node list state ────────────────────────────────────────────────────

    private val _nodes = MutableStateFlow<List<String>>(emptyList())
    val nodes: StateFlow<List<String>> = _nodes.asStateFlow()

    private val _selectedNodeIndex = MutableStateFlow(0)
    val selectedNodeIndex: StateFlow<Int> = _selectedNodeIndex.asStateFlow()

    private val _nodeUrl = MutableStateFlow("")
    val nodeUrl: StateFlow<String> = _nodeUrl.asStateFlow()

    // ─── Client state ───────────────────────────────────────────────────────

    private val _nodeClient = MutableStateFlow<NodeClient?>(null)
    val nodeClient: StateFlow<NodeClient?> = _nodeClient.asStateFlow()

    /** Directly set the node client (backward-compat for SessionManager.setNodeClient). */
    fun setNodeClientDirect(client: NodeClient?) {
        _nodeClient.value = client
    }

    // ─── Initialisation ─────────────────────────────────────────────────────

    init {
        val savedNodes = preferenceManager.loadNodes()
        val nodesMap = if (savedNodes.isEmpty()) NetworkConfig.NODES else savedNodes
        val nodesList = nodesMap.map { "${it.key}: ${(it.value as Map<*, *>)["url"]}" }
        val savedUrl = preferenceManager.selectedNode
        val index = nodesList.indexOfFirst { it.endsWith(savedUrl) }.coerceAtLeast(0)
        _nodes.value = nodesList
        _selectedNodeIndex.value = index
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    /**
     * Build (or rebuild) the [NodeClient] from the currently selected node entry.
     * Call from a coroutine; performs network-safe construction on IO dispatcher.
     *
     * @param allowHttp whether HTTP (non-TLS) nodes are permitted by the user setting
     * @param onClientReady callback invoked on Main with the new client + resolved URL
     */
    suspend fun initializeNodeClient(
        allowHttp: Boolean,
        onClientReady: (client: NodeClient, url: String) -> Unit = { _, _ -> }
    ) {
        withContext(Dispatchers.IO) {
            try {
                val currentNodes = _nodes.value
                val index = _selectedNodeIndex.value
                if (index !in currentNodes.indices) return@withContext

                var nUrl = currentNodes[index]
                nUrl = when {
                    nUrl.contains("(") -> nUrl.substringAfter("(").substringBefore(")")
                    nUrl.contains(": ") -> nUrl.substringAfter(": ")
                    else -> nUrl
                }

                val finalUrl = when {
                    nUrl.startsWith("https://") -> nUrl
                    nUrl.startsWith("http://") && allowHttp -> nUrl
                    nUrl.startsWith("http://") -> {
                        Log.w(TAG, "HTTP node blocked (setting disabled), falling back to default HTTPS node")
                        "https://ergo-node.eutxo.de"
                    }
                    else -> "https://ergo-node.eutxo.de"
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Using node URL: $finalUrl")

                val client = NodeClient(finalUrl)
                _nodeClient.value = client
                _nodeUrl.value = finalUrl

                withContext(Dispatchers.Main) {
                    onClientReady(client, finalUrl)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing node client", e)
            }
        }
    }

    fun setSelectedNodeIndex(index: Int) {
        _selectedNodeIndex.value = index
    }

    /**
     * Delete the node at [selectedNodeIndex] from preferences and rebuild the list.
     * A new node URL will need to be initialised by the caller afterwards.
     *
     * @return the new selected index after deletion
     */
    fun deleteSelectedNode(): Int {
        val currentNodes = _nodes.value
        val index = _selectedNodeIndex.value
        if (currentNodes.isEmpty()) return 0

        val nodesMap = preferenceManager.loadNodes().toMutableMap()
        val nodeNameFull = currentNodes[index]
        val keyToRemove = if (nodeNameFull.contains(": ")) nodeNameFull.substringBefore(":") else nodeNameFull

        if (nodesMap.containsKey(keyToRemove)) {
            nodesMap.remove(keyToRemove)
            preferenceManager.saveNodes(nodesMap)

            val newNodesMap = if (nodesMap.isEmpty()) NetworkConfig.NODES else nodesMap
            val newNodesList = newNodesMap.map { "${it.key}: ${(it.value as Map<*, *>)["url"]}" }
            val newIndex = index.coerceAtMost(newNodesList.size - 1).coerceAtLeast(0)

            _nodes.value = newNodesList
            _selectedNodeIndex.value = newIndex
            return newIndex
        }
        return index
    }

    /** Reload the node list from preferences (e.g. after adding a new node). */
    fun reloadNodes() {
        val savedNodes = preferenceManager.loadNodes()
        val nodesMap = if (savedNodes.isEmpty()) NetworkConfig.NODES else savedNodes
        val nodesList = nodesMap.map { "${it.key}: ${(it.value as Map<*, *>)["url"]}" }
        _nodes.value = nodesList
    }
}
