package com.su.launcherlayout;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Material 3 TonalSpot 配色，仿 KernelSU / Miuix 观感。
 *
 * <p>KernelSU 管理器的配色是 materialkolor 从种子色动态生成的（源码里没有写死的 hex），
 * 它内置的种子色里有 {@code 0xFF4FAF50} 这个绿，正好对应截图里的那个绿色卡片。
 * 这里没有动态取色能力（不依赖 androidx material-color-utilities），
 * 所以直接固化一套该绿种子下的 TonalSpot 明/暗色，跟随系统深浅色切换。
 *
 * <p>字体不用特殊处理：HyperOS 的系统默认字体就是 MiSans，和 KernelSU 一致。
 */
final class Theme {

    static boolean dark;

    static int primary;
    static int onPrimary;
    static int primaryContainer;
    static int onPrimaryContainer;

    static int surface;
    static int onSurface;
    static int onSurfaceVariant;
    static int outlineVariant;

    static int card;          // 卡片底色
    static int page;          // 页面底色
    static int nav;           // 底栏底色
    static int error;
    static int ok;

    /** Miuix / M3 圆角。 */
    static final int R_CARD = 18;
    static final int R_BTN = 22;
    static final int R_SMALL = 12;
    static final int R_PILL = 100;

    private Theme() {
    }

    static void init(Context c) {
        final int mode = c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        dark = (mode == Configuration.UI_MODE_NIGHT_YES);
        if (dark) {
            primary = 0xFF86D794;
            onPrimary = 0xFF00390F;
            primaryContainer = 0xFF0B4A20;
            onPrimaryContainer = 0xFFA2F5AF;
            page = 0xFF11140F;
            surface = 0xFF11140F;
            onSurface = 0xFFE1E4DA;
            onSurfaceVariant = 0xFFC2C9BC;
            card = 0xFF1D211A;
            nav = 0xFF191D17;
            outlineVariant = 0xFF424940;
            error = 0xFFFFB4AB;
            ok = 0xFF86D794;
        } else {
            primary = 0xFF146C2E;
            onPrimary = 0xFFFFFFFF;
            primaryContainer = 0xFFC6EFD0;
            onPrimaryContainer = 0xFF06210F;
            page = 0xFFF3F7EE;
            surface = 0xFFFBFDF7;
            onSurface = 0xFF191D17;
            onSurfaceVariant = 0xFF5A6156;
            card = 0xFFFFFFFF;
            nav = 0xFFFFFFFF;
            outlineVariant = 0xFFDCE3D6;
            error = 0xFFBA1A1A;
            ok = 0xFF146C2E;
        }
    }

    /** 状态卡的底色（仿截图里那块绿底「工作中」卡）。 */
    static int statusBg() {
        return primaryContainer;
    }

    static int statusFg() {
        return onPrimaryContainer;
    }
}
