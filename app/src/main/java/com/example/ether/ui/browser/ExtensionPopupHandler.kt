package com.example.ether.ui.browser

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import com.example.ether.R
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import timber.log.Timber

/**
 * Handles the display and management of GeckoView extension popups using a PopupWindow.
 */
class ExtensionPopupHandler(
    private val context: Context,
    private val onDismiss: () -> Unit
) {
    private var popupWindow: PopupWindow? = null
    private var geckoView: GeckoView? = null
    private var currentSession: GeckoSession? = null

    /**
     * Shows the extension popup anchored to the provided view.
     * 
     * @param anchor The view to anchor the popup to (usually the toolbar button).
     * @param session The GeckoSession created for the extension popup.
     * @param backgroundColor The themed background color.
     */
    fun showPopup(anchor: View, session: GeckoSession, backgroundColor: Int = Color.WHITE) {
        if (!anchor.isAttachedToWindow) {
            Timber.e("Cannot show popup: anchor view is not attached to window")
            return
        }

        if (popupWindow?.isShowing == true) {
            if (currentSession == session) {
                dismissPopup()
                return
            }
            dismissPopup()
        }

        val inflater = LayoutInflater.from(context)
        val layout = inflater.inflate(R.layout.extension_popup, null)
        
        // Apply theme color to background drawable if it's a shape
        val background = layout.background
        if (background is android.graphics.drawable.GradientDrawable) {
            background.setColor(backgroundColor)
            // Lighter stroke for dark themes
            val isDark = isColorDark(backgroundColor)
            background.setStroke(1, if (isDark) Color.DKGRAY else Color.LTGRAY)
        } else {
            layout.setBackgroundColor(backgroundColor)
        }

        geckoView = layout.findViewById(R.id.extension_gecko_view)
        
        Timber.d("Showing popup session. Private: ${session.settings.usePrivateMode}")

        geckoView?.setSession(session)
        
        currentSession = session

        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val popupWidth = (screenWidth * 0.9f).toInt().coerceAtMost((400 * density).toInt())
        val popupHeight = (500 * density).toInt()

        try {
            popupWindow = PopupWindow(
                layout,
                popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                isOutsideTouchable = true
                isFocusable = true
                elevation = 16f
                
                setOnDismissListener {
                    handleDismiss()
                }
                
                // Calculate if we should show above or below
                val location = IntArray(2)
                anchor.getLocationOnScreen(location)
                val screenHeight = context.resources.displayMetrics.heightPixels
                val isBottomHalf = location[1] > screenHeight / 2
                
                if (isBottomHalf) {
                    val density = context.resources.displayMetrics.density
                    val yOffset = -(500 * density + anchor.height + 24).toInt()
                    showAsDropDown(anchor, 0, yOffset)
                } else {
                    showAsDropDown(anchor, 0, 8)
                }
            }
            
            geckoView?.layoutParams?.height = popupHeight
            geckoView?.requestLayout()
        } catch (e: Exception) {
            Timber.e(e, "Failed to show extension popup")
            handleDismiss()
        }
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }

    private fun handleDismiss() {
        // Clean up session and notify
        geckoView?.releaseSession()
        geckoView = null
        currentSession = null
        popupWindow = null
        
        // Notify and let the owner handle session closing if needed
        onDismiss()
    }

    fun dismissPopup() {
        popupWindow?.dismiss()
    }

    fun isShowing(): Boolean = popupWindow?.isShowing == true
}
