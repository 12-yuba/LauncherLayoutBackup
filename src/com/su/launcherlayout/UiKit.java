package com.su.launcherlayout;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Miuix / M3 风格的控件工厂。 */
final class UiKit {

    private UiKit() {
    }

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    // ------------------------------------------------------------------ 格式化

    private static final java.util.regex.Pattern DB_PAT =
            java.util.regex.Pattern.compile("^launcher(\\d+)x(\\d+)\\.db$");

    /** 从 launcher5x8.db 解析出 "5×8"。 */
    static String parseGrid(String dbName) {
        if (dbName == null) {
            return "?";
        }
        java.util.regex.Matcher m = DB_PAT.matcher(dbName);
        return m.matches() ? (m.group(1) + "×" + m.group(2)) : "?";
    }

    static String shortName(String dir) {
        return dir == null ? "?" : dir.replace(Const.BACKUP_PREFIX, "");
    }

    /** LauncherLayoutBackup-20261012_165555 -> 2026-10-12 16:55:55。 */
    static String prettyTime(String dir) {
        String s = shortName(dir);
        try {
            if (s.length() >= 15) {
                return s.substring(0, 4) + "-" + s.substring(4, 6) + "-" + s.substring(6, 8)
                        + " " + s.substring(9, 11) + ":" + s.substring(11, 13) + ":" + s.substring(13, 15);
            }
        } catch (Throwable ignored) {
        }
        return s;
    }

    /** 毫秒时间戳字符串 -> yyyy-MM-dd HH:mm；<=0 用 zeroText。 */
    static String fmtTs(String v, String zeroText) {
        if (v == null) {
            return "?";
        }
        long t;
        try {
            t = Long.parseLong(v);
        } catch (Throwable e) {
            return "?";
        }
        if (t <= 0) {
            return zeroText;
        }
        if (t < 1000000000000L) {
            return "—";
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                .format(new java.util.Date(t));
    }

    // ------------------------------------------------------------------ 背景

    static Drawable round(Context c, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** 圆角底 + 水波纹。 */
    static Drawable clickable(Context c, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        int ripple = (Theme.dark ? 0x33FFFFFF : 0x22000000);
        return new RippleDrawable(ColorStateList.valueOf(ripple), g, null);
    }

    // ------------------------------------------------------------------ 卡片

    /** 白色圆角卡片（Miuix Card）。 */
    static LinearLayout card(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(round(c, Theme.card, Theme.R_CARD));
        v.setElevation(dp(c, 0.5f));
        v.setClipToOutline(true);
        return v;
    }

    static void pad(Context c, View v, float h, float t, float b) {
        v.setPadding(dp(c, h), dp(c, t), dp(c, h), dp(c, b));
    }

    // ------------------------------------------------------------------ 文字

    static TextView text(Context c, String s, int color, float sizeSp) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextColor(color);
        v.setTextSize(sizeSp);
        return v;
    }

    /** 页面大标题（仿 KernelSU 那个大标题）。 */
    static TextView pageTitle(Context c, String s) {
        TextView v = text(c, s, Theme.onSurface, 30f);
        v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return v;
    }

    static TextView rowTitle(Context c, String s) {
        return text(c, s, Theme.onSurface, 16f);
    }

    static TextView rowSummary(Context c, String s) {
        return text(c, s, Theme.onSurfaceVariant, 13f);
    }

    static TextView sectionLabel(Context c, String s) {
        TextView v = text(c, s, Theme.primary, 13f);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }

    static TextView mono(Context c, String s, int color, float sizeSp) {
        TextView v = text(c, s, color, sizeSp);
        v.setTypeface(Typeface.MONOSPACE);
        return v;
    }

    // ------------------------------------------------------------------ 图标

    static ImageView icon(Context c, int res, float sizeDp, int color) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    // ------------------------------------------------------------------ 行

    /**
     * Miuix 风格的可点击行：左图标 + 标题/副标题 + 右箭头。
     * 传 iconRes=0 则不显示图标。
     */
    static LinearLayout itemRow(final Context c, int iconRes, String title, String summary,
                                final View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(clickable(c, 0x00000000, Theme.R_SMALL));
        pad(c, row, 16, 14, 14);

        if (iconRes != 0) {
            ImageView ic = icon(c, iconRes, 22, Theme.primary);
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) ic.getLayoutParams();
            p.rightMargin = dp(c, 14);
            row.addView(ic);
        }

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(rowTitle(c, title));
        if (summary != null && !summary.isEmpty()) {
            TextView s = rowSummary(c, summary);
            s.setPadding(0, dp(c, 2), 0, 0);
            col.addView(s);
        }
        row.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chev = icon(c, R.drawable.ic_chevron, 18, Theme.onSurfaceVariant);
        row.addView(chev);

        if (onClick != null) {
            row.setOnClickListener(onClick);
            row.setClickable(true);
        }
        return row;
    }

    /** Miuix 风格的「标签 + 值」两行块（不整块可点，内部元素各自可点）。 */
    static LinearLayout kvBlock(Context c, String label, String value) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        col.addView(rowTitle(c, label));
        TextView v = text(c, value, Theme.onSurfaceVariant, 13f);
        v.setPadding(0, dp(c, 3), 0, 0);
        col.addView(v);
        return col;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Theme.outlineVariant);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.6f))));
        return v;
    }

    /** 卡片内的分隔线（左右各留 16dp）。 */
    static LinearLayout dividerRow(Context c) {
        LinearLayout w = new LinearLayout(c);
        w.setOrientation(LinearLayout.HORIZONTAL);
        w.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        w.addView(divider(c));
        return w;
    }

    // ------------------------------------------------------------------ 按钮

    static Button button(Context c, String t, int bg, int fg, float sizeSp,
                         View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(sizeSp);
        b.setTextColor(fg);
        b.setBackground(clickable(c, bg, Theme.R_BTN));
        b.setMinHeight(dp(c, 46));
        b.setMinimumHeight(dp(c, 46));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(c, 18), dp(c, 11), dp(c, 18), dp(c, 11));
        b.setStateListAnimator(null);
        b.setOnClickListener(l);
        return b;
    }

    /** 主色实心按钮。 */
    static Button filled(Context c, String t, View.OnClickListener l) {
        return button(c, t, Theme.primary, Theme.onPrimary, 15f, l);
    }

    /** 主色浅底（tonal）按钮。 */
    static Button tonal(Context c, String t, View.OnClickListener l) {
        return button(c, t, Theme.primaryContainer, Theme.onPrimaryContainer, 14f, l);
    }

    /** 灰底按钮（放在卡片里的次要动作）。 */
    static Button subtle(Context c, String t, View.OnClickListener l) {
        return button(c, t, Theme.dark ? 0xFF2C332A : 0xFFE7ECE2, Theme.onSurface, 13f, l);
    }

    /** 危险操作（实心：浅红底 + 深红字，别用透明底，否则看着像没渲染出来）。 */
    static Button danger(Context c, String t, View.OnClickListener l) {
        return button(c, t, Theme.dark ? 0xFF8C1D18 : 0xFFFFDAD6, Theme.error, 14f, l);
    }

    static Button mini(Context c, String t, View.OnClickListener l) {
        Button b = button(c, t, Theme.dark ? 0xFF2C332A : 0xFFE7ECE2, Theme.onSurface, 12f, l);
        b.setMinHeight(dp(c, 34));
        b.setMinimumHeight(dp(c, 34));
        b.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        b.setOnClickListener(l);
        return b;
    }

    // ------------------------------------------------------------------ 布局参数

    static LinearLayout.LayoutParams full(Context c, int topDp, int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, topDp);
        p.bottomMargin = dp(c, bottomDp);
        return p;
    }

    /** 带右边距的图标（用于行首图标）。 */
    static ImageView iconMr(Context c, int res, float sizeDp, int color, int rightMarginDp) {
        ImageView iv = icon(c, res, sizeDp, color);
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) iv.getLayoutParams();
        p.rightMargin = dp(c, rightMarginDp);
        return iv;
    }

    static LinearLayout.LayoutParams weight(Context c, float w, int leftDp, int rightDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
        p.leftMargin = dp(c, leftDp);
        p.rightMargin = dp(c, rightDp);
        return p;
    }

    /**
     * 保持自身宽度、只加边距。
     * 注意：**不要**用 {@code weight(c, 0f, ...)} 代替 —— 那是宽 0 + weight 0，控件会直接看不见。
     */
    static LinearLayout.LayoutParams wrap(Context c, int leftDp, int rightDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(c, leftDp);
        p.rightMargin = dp(c, rightDp);
        return p;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** 圆形小徽标（仿 KSU 底栏上的数字角标）。 */
    static TextView badge(Context c, String n) {
        TextView v = new TextView(c);
        v.setText(n);
        v.setTextColor(Theme.onPrimary);
        v.setTextSize(9f);
        v.setGravity(Gravity.CENTER);
        v.setBackground(round(c, Theme.error, Theme.R_PILL));
        v.setMinWidth(dp(c, 16));
        v.setMinHeight(dp(c, 16));
        v.setPadding(dp(c, 3), 0, dp(c, 3), 0);
        return v;
    }
}
