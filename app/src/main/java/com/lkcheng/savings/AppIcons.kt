package com.lkcheng.savings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

data class AppIcon(val alias: String, val name: String, val fg: Int, val bg: Long)

val APP_ICONS = listOf(
    AppIcon("IconBook", "成长账本", R.drawable.ic_fg_book, 0xFF3E95D6),
    AppIcon("IconCoin", "金币", R.drawable.ic_fg_coin, 0xFF1D4ED8),
    AppIcon("IconLeaf", "绿叶", R.drawable.ic_fg_leaf, 0xFF2E9D5B),
    AppIcon("IconPig", "小猪存钱罐", R.drawable.ic_fg_pig, 0xFFE0538A),
    AppIcon("IconHeart", "紫粉爱心", R.drawable.ic_fg_heart, 0xFF9B4DDB),
)

object AppIcons {
    fun current(ctx: Context): Int = Store(ctx).getI("app_icon", 0)

    /** Switches the launcher icon by enabling one activity-alias. The launcher may take a few seconds to update. */
    fun apply(ctx: Context, index: Int) {
        val pm = ctx.packageManager
        for ((i, ic) in APP_ICONS.withIndex()) {
            pm.setComponentEnabledSetting(
                ComponentName(ctx, "${ctx.packageName}.${ic.alias}"),
                if (i == index) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        Store(ctx).setI("app_icon", index)
    }

    /** Puts a home-screen shortcut that uses the user's own picture. */
    fun pinCustom(ctx: Context, uri: Uri, label: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) return false
        val src = try {
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        } ?: return false
        // centre-crop to a square, 432px (108dp @4x) for adaptive icons
        val side = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        val left = (src.width - side) / 2
        val top = (src.height - side) / 2
        Canvas(out).drawBitmap(src, Rect(left, top, left + side, top + side), Rect(0, 0, 432, 432), null)
        val intent = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
        val info = ShortcutInfoCompat.Builder(ctx, "custom_icon_" + System.currentTimeMillis())
            .setShortLabel(label)
            .setIcon(IconCompat.createWithAdaptiveBitmap(out))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
    }
}
