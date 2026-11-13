package com.dauntehnusantara.pos.core.designsystem

enum class WindowLayout { Compact, Medium, Expanded }

fun windowLayout(widthDp: Int): WindowLayout = when {
    widthDp < 600 -> WindowLayout.Compact
    widthDp < 840 -> WindowLayout.Medium
    else -> WindowLayout.Expanded
}
