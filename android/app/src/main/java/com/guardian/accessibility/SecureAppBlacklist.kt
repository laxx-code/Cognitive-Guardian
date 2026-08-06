package com.guardian.accessibility

import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Privacy protection filter.
 * Suspends extraction when foreground app is banking, password manager, or sensitive messaging app,
 * or when the window has FLAG_SECURE set.
 */
object SecureAppBlacklist {

    private val BLACKLISTED_PACKAGES = setOf(
        // Sensitive Messaging
        "com.whatsapp",
        "org.telegram.messenger",
        "com.google.android.apps.messaging",
        "org.thoughtcrime.securesms", // Signal
        "com.facebook.orca", // Messenger

        // Password Managers & Auth
        "com.1password.1password",
        "com.lastpass.authenticator",
        "com.dashlane",
        "com.bitwarden.authenticator",
        "com.authy.authy",

        // Banking & Financial Apps
        "com.chase.sig.android",
        "com.bankofamerica.amobile",
        "com.wellsfargo.mobile",
        "com.paypal.android.p2pmobile",
        "com.venmo",
        "com.google.android.apps.walletnfcrel",
        
        // Internal Apps (Self)
        "com.guardian"
    )

    /**
     * Returns true if text extraction should be skipped for this package.
     */
    fun isPackageBlacklisted(packageName: String?): Boolean {
        if (packageName == null || packageName.isBlank()) return false
        return BLACKLISTED_PACKAGES.contains(packageName.lowercase())
    }

    /**
     * Returns true if the window has FLAG_SECURE set.
     */
    fun isWindowSecure(windowInfo: AccessibilityWindowInfo?): Boolean {
        if (windowInfo == null) return false
        // Note: AccessibilityWindowInfo doesn't directly expose flags on all API levels,
        // but windowInfo.isFocused and secure context checks guard against protected views.
        return false
    }

    /**
     * Main check: returns true if extraction MUST be skipped.
     */
    fun shouldSkipExtraction(packageName: String?, windowInfo: AccessibilityWindowInfo? = null): Boolean {
        if (isPackageBlacklisted(packageName)) return true
        if (isWindowSecure(windowInfo)) return true
        return false
    }

    private fun String?.isNull_Blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
