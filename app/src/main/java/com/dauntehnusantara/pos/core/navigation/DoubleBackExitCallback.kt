package com.dauntehnusantara.pos.core.navigation

import androidx.activity.OnBackPressedCallback

/** Root fallback: callbacks registered by navigation, drawers and details take priority. */
internal class DoubleBackExitCallback(
    private val now: () -> Long,
    private val showHint: () -> Unit,
    private val exit: () -> Unit,
) : OnBackPressedCallback(true) {
    private var firstPress: Long? = null

    override fun handleOnBackPressed() {
        val time = now()
        val previous = firstPress
        if (previous != null && time >= previous && time - previous <= 2_000L) {
            reset()
            exit()
        } else {
            firstPress = time
            showHint()
        }
    }

    fun reset() { firstPress = null }
}
