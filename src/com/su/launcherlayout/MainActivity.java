package com.su.launcherlayout;

import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 单 Activity + 底部导航（仿 KernelSU 管理器的结构）。
 * 主页 / 备份 / 设置 三个页面，不重建视图，切换即时。
 */
public final class MainActivity extends BaseActivity {

    static final int TAB_HOME = 0;
    static final int TAB_BACKUP = 1;
    static final int TAB_SETTINGS = 2;

    private static final int[] TAB_ICON = {
            R.drawable.ic_home, R.drawable.ic_box, R.drawable.ic_tune
    };
    private static final String[] TAB_NAME = {"主页", "备份", "设置"};

    private FrameLayout mContent;
    private final Page[] mPages = new Page[3];
    private final ImageView[] mNavIcons = new ImageView[3];
    private final TextView[] mNavLabels = new TextView[3];
    private TextView mBadge;
    private int mTab = TAB_HOME;

    @Override
    protected View buildContentView() {
        Theme.init(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.page);

        mContent = new FrameLayout(this);
        root.addView(mContent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildNav());

        mPages[TAB_HOME] = new PageHome(this);
        mPages[TAB_BACKUP] = new PageBackup(this);
        mPages[TAB_SETTINGS] = new PageSettings(this);
        showTab(TAB_HOME);
        return root;
    }

    /** 状态栏图标跟随深浅色，并去掉启动白闪。 */
    private void tuneWindow() {
        try {
            getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Theme.page));
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                View dv = getWindow().getDecorView();
                int flags = dv.getSystemUiVisibility();
                if (Theme.dark) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                dv.setSystemUiVisibility(flags);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void afterCreate() {
        tuneWindow();
        request(Const.ACTION_INFO, null);
    }

    // ------------------------------------------------------------------ 底部导航

    private View buildNav() {
        LinearLayout wrap = UiKit.column(this);
        wrap.setBackgroundColor(Theme.nav);

        View hair = new View(this);
        hair.setBackgroundColor(Theme.outlineVariant);
        wrap.addView(hair, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, UiKit.dp(this, 0.6f))));

        LinearLayout bar = UiKit.row(this);
        bar.setPadding(0, UiKit.dp(this, 4), 0, UiKit.dp(this, 4));
        for (int i = 0; i < 3; i++) {
            final int t = i;
            LinearLayout item = UiKit.column(this);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, UiKit.dp(this, 8), 0, UiKit.dp(this, 8));
            item.setBackground(UiKit.clickable(this, 0x00000000, Theme.R_SMALL));

            FrameLayout iconWrap = new FrameLayout(this);
            ImageView ic = UiKit.icon(this, TAB_ICON[i], 22, Theme.onSurfaceVariant);
            iconWrap.addView(ic);
            if (i == TAB_BACKUP) {
                mBadge = UiKit.badge(this, "0");
                mBadge.setVisibility(View.GONE);
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                // 贴在图标右上角：不要 margin，否则会被推出 FrameLayout 跑到隔壁 tab 上
                bp.gravity = Gravity.TOP | Gravity.END;
                iconWrap.addView(mBadge, bp);
            }
            // 竖向 LinearLayout 里 addView 不带参数时默认宽是 MATCH_PARENT，
            // 那样 iconWrap 会铺满整个 tab 宽度、角标就被推到隔壁 tab 上了。
            item.addView(iconWrap, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView lab = UiKit.text(this, TAB_NAME[i], Theme.onSurfaceVariant, 11f);
            // 竖向 LinearLayout 里 addView 不带参数时宽度是 MATCH_PARENT，
            // 文字会按左对齐铺满整格 → 和居中的图标错位。必须显式居中。
            lab.setGravity(Gravity.CENTER);
            lab.setPadding(0, UiKit.dp(this, 4), 0, 0);
            item.addView(lab);

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTab(t);
                }
            });
            mNavIcons[i] = ic;
            mNavLabels[i] = lab;
            bar.addView(item, UiKit.weight(this, 1f, 0, 0));
        }
        wrap.addView(bar);
        return wrap;
    }

    void showTab(int t) {
        if (t < 0 || t > 2) {
            return;
        }
        mTab = t;
        mContent.removeAllViews();
        View v = mPages[t].view();
        if (v.getParent() instanceof ViewGroup) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
        mContent.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        for (int i = 0; i < 3; i++) {
            final boolean sel = (i == t);
            mNavIcons[i].setColorFilter(sel ? Theme.primary : Theme.onSurfaceVariant,
                    PorterDuff.Mode.SRC_IN);
            mNavLabels[i].setTextColor(sel ? Theme.primary : Theme.onSurfaceVariant);
            mNavLabels[i].setTypeface(sel
                    ? android.graphics.Typeface.create("sans-serif-medium",
                    android.graphics.Typeface.NORMAL)
                    : android.graphics.Typeface.SANS_SERIF);
        }
        mPages[t].onShow();
    }

    int currentTab() {
        return mTab;
    }

    private void updateBadge() {
        if (mBadge == null) {
            return;
        }
        final int n = backupCount();
        if (n > 0) {
            mBadge.setText(n > 99 ? "99+" : String.valueOf(n));
            mBadge.setVisibility(View.VISIBLE);
        } else {
            mBadge.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------ 回执分发

    @Override
    protected void onResult(String op, boolean ok, String msg, String dir,
                            String[] items, String[] grids) {
        if (Const.OP_INFO.equals(op) && ok) {
            currentGrid = UiKit.parseGrid(dir);
        }
        // 只分发给**当前页**。
        // 其它页的视图是懒建的（没显示过就还没 build），提前塞结果会摸到 null 控件 ——
        // 之前就是这样空指针闪退的。未显示的页在 onShow() 里会自己重新拉一次数据。
        if (mTab >= 0 && mTab < mPages.length && mPages[mTab] != null) {
            mPages[mTab].onResult(op, ok, msg, dir, items, grids);
        }
        updateBadge();
    }

    @Override
    protected void onTimeout() {
        if (mTab >= 0 && mTab < mPages.length && mPages[mTab] instanceof PageHome) {
            ((PageHome) mPages[mTab]).showOffline();
        }
        if (mTab >= 0 && mTab < mPages.length && mPages[mTab] instanceof PageSettings) {
            ((PageSettings) mPages[mTab]).showOffline();
        }
    }

    /** 供各页面共用的请求刷新。 */
    void refreshAll() {
        request(Const.ACTION_INFO, null);
    }

    @Override
    protected void onDirPicked(String path) {
        if (mTab >= 0 && mTab < mPages.length && mPages[mTab] != null) {
            mPages[mTab].onDirPicked(path);
        }
    }
}
