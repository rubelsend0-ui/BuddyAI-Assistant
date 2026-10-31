package com.buddy.assistant

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BuddyAccessibilityService : AccessibilityService() {

    companion object {
        var isAutoSendPending = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isAutoSendPending) return
        val rootNode = rootInActiveWindow ?: return

        // 1. WhatsApp View ID search
        val sendById = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        if (sendById.isNotEmpty()) {
            for (node in sendById) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d("BuddyAI", "Send clicked via View ID")
                    isAutoSendPending = false
                    return
                }
            }
        }

        // 2. WhatsApp Text / Description fallback
        val sendByText = rootNode.findAccessibilityNodeInfosByText("Send")
        for (node in sendByText) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d("BuddyAI", "Send clicked via Text")
                isAutoSendPending = false
                return
            }
        }
    }

    override fun onInterrupt() {}
}
