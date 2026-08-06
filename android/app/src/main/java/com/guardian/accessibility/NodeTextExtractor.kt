package com.guardian.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class ExtractedNodeData(
    val text: String,
    val bounds: Rect
)

object NodeTextExtractor {

    private const val MAX_RECURSION_DEPTH = 25
    private const val MAX_NODE_COUNT = 500

    /**
     * Recursively walks the AccessibilityNodeInfo tree and extracts non-empty text strings
     * along with their screen bounds Rect.
     */
    fun extractTextAndBounds(rootNode: AccessibilityNodeInfo?): List<ExtractedNodeData> {
        if (rootNode == null) return emptyList()

        val results = mutableListOf<ExtractedNodeData>()
        val visitedNodes = mutableSetOf<Int>()

        traverseNode(rootNode, 0, results, visitedNodes)
        return results
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        depth: Int,
        results: MutableList<ExtractedNodeData>,
        visitedNodes: MutableSet<Int>
    ) {
        if (node == null || depth > MAX_RECURSION_DEPTH || results.size >= MAX_NODE_COUNT) {
            return
        }

        // Prevent infinite loops in cyclic layout graphs
        val nodeId = node.hashCode()
        if (visitedNodes.contains(nodeId)) {
            return
        }
        visitedNodes.add(nodeId)

        val rawText = node.text?.toString()?.trim()
        val contentDesc = node.contentDescription?.toString()?.trim()
        val textToUse = if (!rawText.isNullOrEmpty()) rawText else contentDesc

        if (!textToUse.isNullOrEmpty() && textToUse.length > 2) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                results.add(ExtractedNodeData(text = textToUse, bounds = bounds))
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseNode(child, depth + 1, results, visitedNodes)
            }
        }
    }
}
