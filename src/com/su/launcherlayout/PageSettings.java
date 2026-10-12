package com.su.launcherlayout;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** 设置页：自动备份 / 备份路径 / 保留策略 / 鸣谢 / 关于。 */
final class PageSettings extends Page {

    private Switch sw7;
    private Switch sw30;
    private Switch sw180;
    private Switch swShutdown;
    private Switch swDigest;

    private TextView mAutoStatus;
    private TextView mRootNow;
    private EditText mPath;
    private EditText mKeep;
    private Switch swLog;

    private boolean loading;

    PageSettings(MainActivity h) {
        super(h);
    }

    @Override
    protected View build() {
        LinearLayout root = UiKit.column(host);
        root.setBackgroundColor(Theme.page);

        ScrollView scroll = new ScrollView(host);
        LinearLayout c = UiKit.column(host);
        UiKit.pad(host, c, 20, 22, 24);
        scroll.addView(c, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        c.addView(UiKit.pageTitle(host, "设置"));

        // ---------- 自动备份 ----------
        c.addView(section("自动备份"));
        LinearLayout auto = UiKit.card(host);
        LinearLayout autoIn = UiKit.column(host);
        UiKit.pad(host, autoIn, 16, 14, 14);
        autoIn.addView(UiKit.rowSummary(host, "跑在桌面进程里，App 不用常驻。改动立刻生效。"));
        sw7 = addSwitch(autoIn, "每 7 天");
        sw30 = addSwitch(autoIn, "每 30 天");
        sw180 = addSwitch(autoIn, "每 180 天");
        swShutdown = addSwitch(autoIn, "关机 / 重启前备份（尽力而为）");
        swDigest = addSwitch(autoIn, "仅在布局有变化时才备份");

        mAutoStatus = UiKit.mono(host, "读取中…", Theme.onSurfaceVariant, 11f);
        mAutoStatus.setBackgroundColor(Theme.dark ? 0xFF171B15 : 0xFFF2F6EC);
        mAutoStatus.setPadding(UiKit.dp(host, 12), UiKit.dp(host, 10),
                UiKit.dp(host, 12), UiKit.dp(host, 10));
        autoIn.addView(mAutoStatus, UiKit.full(host, 12, 0));
        auto.addView(autoIn);
        c.addView(auto, UiKit.full(host, 8, 0));

        // ---------- 备份路径 ----------
        c.addView(section("备份路径"));
        LinearLayout pathCard = UiKit.card(host);
        LinearLayout pathIn = UiKit.column(host);
        UiKit.pad(host, pathIn, 16, 14, 14);
        pathIn.addView(UiKit.rowSummary(host, "只允许手机内部存储，SD 卡 / U 盘不支持。改路径不动老备份。"));
        mRootNow = UiKit.mono(host, "当前：—", Theme.primary, 11f);
        pathIn.addView(mRootNow, UiKit.full(host, 8, 0));
        mPath = addEdit(pathIn, "路径", "/storage/emulated/0/Download", false);
        LinearLayout pr = UiKit.row(host);
        pr.addView(UiKit.subtle(host, "从文件选择器选", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickDir();
            }
        }), UiKit.weight(host, 1f, 0, 5));
        pr.addView(UiKit.filled(host, "保存路径", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiLog.log("⚙️ 保存备份路径: " + text(mPath));
                Intent i = new Intent(Const.ACTION_SET_ROOT);
                i.putExtra(Const.EX_ROOT, text(mPath));
                host.dispatch(i, Const.ACTION_SET_ROOT, true);
            }
        }), UiKit.weight(host, 1f, 5, 0));
        pathIn.addView(pr, UiKit.full(host, 12, 0));
        pathCard.addView(pathIn);
        c.addView(pathCard, UiKit.full(host, 8, 0));

        // ---------- 保留策略 ----------
        c.addView(section("保留策略"));
        LinearLayout keepCard = UiKit.card(host);
        LinearLayout keepIn = UiKit.column(host);
        UiKit.pad(host, keepIn, 16, 14, 14);
        keepIn.addView(UiKit.rowSummary(host, "自动备份最多保留几份，只删最早的自动备份。手动备份不会删。0 = 不限制。"));
        mKeep = addEdit(keepIn, "最多保留份数", "5", true);
        keepCard.addView(keepIn);
        c.addView(keepCard, UiKit.full(host, 8, 0));

        // ---------- 操作日志 ----------
        c.addView(section("操作日志"));
        LinearLayout logCard = UiKit.card(host);
        LinearLayout logIn = UiKit.column(host);
        UiKit.pad(host, logIn, 16, 14, 14);
        logIn.addView(UiKit.rowSummary(host, "关掉后主页不显示日志面板。日志保留 360 天。"));
        swLog = addSwitch(logIn, "在主页显示操作日志");
        logCard.addView(logIn);
        c.addView(logCard, UiKit.full(host, 8, 0));

        // ---------- 鸣谢 ----------
        c.addView(section("鸣谢"));
        LinearLayout credit = UiKit.card(host);
        credit.addView(UiKit.itemRow(host, 0, creditName("l1n", "瞑望") + " · 思路提供",
                creditUrl("l1u"), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openUrl(creditUrl("l1u"));
                    }
                }));
        credit.addView(UiKit.dividerRow(host));
        credit.addView(UiKit.itemRow(host, 0, creditName("l2n", "桃酥打水") + " · 模块作者",
                creditUrl("l2u"), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openUrl(creditUrl("l2u"));
                    }
                }));
        c.addView(credit, UiKit.full(host, 8, 0));

        // ---------- 关于 ----------
        c.addView(section("关于"));
        LinearLayout about = UiKit.card(host);
        LinearLayout aboutIn = UiKit.column(host);
        UiKit.pad(host, aboutIn, 16, 14, 14);
        aboutIn.addView(UiKit.kvBlock(host, "模块", "LSPosed 模块 · libxposed API 102"));
        aboutIn.addView(UiKit.dividerRow(host));
        aboutIn.addView(UiKit.kvBlock(host, "作用域", "com.miui.home（系统桌面）"));
        aboutIn.addView(UiKit.dividerRow(host));
        aboutIn.addView(UiKit.kvBlock(host, "运行前提",
                "手机已 root（KernelSU / Magisk 等）+ 已安装 LSPosed\n"
                        + "本模块是 LSPosed 模块，离开 root 无法使用"));
        aboutIn.addView(UiKit.dividerRow(host));
        aboutIn.addView(UiKit.kvBlock(host, "本 App 的权限",
                "不申请任何权限。\n"
                        + "备份由桌面进程以自身身份完成，不提权；\n"
                        + "恢复需要一次 root 授权（要在桌面进程之外替换它的数据库文件）"));
        aboutIn.addView(UiKit.dividerRow(host));
        aboutIn.addView(UiKit.kvBlock(host, "备份内容",
                "launcher*.db(+wal) + 一致性快照 + 三个附属库 + drawer/ + prefs + CSV + MANIFEST"));
        aboutIn.addView(UiKit.dividerRow(host));
        aboutIn.addView(UiKit.kvBlock(host, "安全备份",
                "一键删除全部备份前会自动留一份，可随时找回"));
        about.addView(aboutIn);
        c.addView(about, UiKit.full(host, 8, 0));

        return root;
    }

    @Override
    void onShow() {
        host.request(Const.ACTION_GET_CONFIG, null);
    }

    // ------------------------------------------------------------------ 控件

    private TextView section(String t) {
        TextView v = UiKit.sectionLabel(host, t);
        v.setPadding(0, UiKit.dp(host, 22), 0, UiKit.dp(host, 8));
        return v;
    }

    private Switch addSwitch(LinearLayout parent, String text) {
        Switch s = new Switch(host);
        s.setText(text);
        s.setTextColor(Theme.onSurface);
        s.setTextSize(15f);
        s.setChecked(false);
        try {
            s.setThumbTintList(new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, new int[]{}},
                    new int[]{Theme.onPrimary, Theme.dark ? 0xFF8A9287 : 0xFFFFFFFF}));
            s.setTrackTintList(new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, new int[]{}},
                    new int[]{Theme.primary, Theme.dark ? 0xFF3A4238 : 0xFFCFD6CB}));
        } catch (Throwable ignored) {
        }
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (loading) {
                    return;
                }
                if (b == swLog) {
                    Intent i = new Intent(Const.ACTION_SET_EXTRA);
                    i.putExtra(Const.EX_LOG_ON, checked);
                    UiLog.log("⚙️ " + (checked ? "显示" : "隐藏") + "操作日志");
                    host.dispatch(i, Const.ACTION_SET_EXTRA, true);
                    return;
                }
                pushConfig();
            }
        });
        parent.addView(s);
        return s;
    }

    private EditText addEdit(LinearLayout parent, String hint, String example, boolean number) {
        TextView lab = UiKit.rowSummary(host, hint);
        lab.setPadding(0, UiKit.dp(host, 12), 0, UiKit.dp(host, 4));
        parent.addView(lab);

        EditText e = new EditText(host);
        e.setTextSize(13f);
        e.setSingleLine(true);
        e.setTextColor(Theme.onSurface);
        e.setHint(example);
        e.setHintTextColor(Theme.onSurfaceVariant);
        e.setBackground(UiKit.round(host, Theme.dark ? 0xFF171B15 : 0xFFF2F6EC, Theme.R_SMALL));
        e.setPadding(UiKit.dp(host, 12), UiKit.dp(host, 10),
                UiKit.dp(host, 12), UiKit.dp(host, 10));
        if (number) {
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
        parent.addView(e);
        return e;
    }

    private String creditName(String key, String fallback) {
        String v = host.cfg.get(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    private String creditUrl(String key) {
        String v = host.cfg.get(key);
        if (v != null && !v.isEmpty()) {
            return v;
        }
        return "l1u".equals(key)
                ? "https://www.coolapk.com/u/1175257"
                : "https://www.coolapk.com/u/4111144";
    }

    private static String text(EditText e) {
        return e == null ? "" : e.getText().toString().trim();
    }

    // ------------------------------------------------------------------ 回执

    @Override
    void onResult(String op, boolean ok, String msg, String dir, String[] items, String[] grids) {
        if (mAutoStatus == null) {
            return; // 视图还没建（本页没显示过），onShow() 会重新拉
        }
        if (Const.OP_INFO.equals(op) && ok) {
            host.currentGrid = UiKit.parseGrid(dir);
            return;
        }
        if (Const.OP_GET_CONFIG.equals(op) || Const.OP_SET_CONFIG.equals(op)
                || Const.OP_SET_ROOT.equals(op) || Const.OP_SET_EXTRA.equals(op)) {
            populate();
            if (Const.OP_SET_ROOT.equals(op) || Const.OP_SET_EXTRA.equals(op)) {
                host.toast(ok ? "已保存" : msg);
                UiLog.log((ok ? "✅ " : "❌ ") + msg);
            }
        }
    }

    void showOffline() {
        if (mAutoStatus != null) {
            mAutoStatus.setText("⚠️ 桌面进程没有响应（模块未启用 / 桌面进程没重启）");
        }
    }

    private void populate() {
        loading = true;
        try {
            setSwitch(sw7, host.cfg.get("en7"));
            setSwitch(sw30, host.cfg.get("en30"));
            setSwitch(sw180, host.cfg.get("en180"));
            setSwitch(swShutdown, host.cfg.get("onShutdown"));
            setSwitch(swDigest, host.cfg.get("digestOnly"));

            String root = host.cfg.get("root");
            if (root != null) {
                host.rootPath = root;
                mRootNow.setText("当前：" + root);
                if (mPath != null && !mPath.hasFocus() && mPath.getText().length() == 0) {
                    mPath.setText(root);
                }
            }
            if (mKeep != null && mKeep.getText().length() == 0) {
                mKeep.setText(host.cfg.containsKey("keepAuto") ? host.cfg.get("keepAuto") : "5");
            }
            setSwitch(swLog, "1".equals(host.cfg.get("logOn")) ? "true" : "false");
        } finally {
            loading = false;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("上次自动备份  ").append(UiKit.fmtTs(host.cfg.get("lastAuto"), "（还没有）")).append('\n');
        sb.append("7 天档下次   ").append(UiKit.fmtTs(host.cfg.get("last7"), "（已关闭）")).append('\n');
        sb.append("30 天档下次  ").append(UiKit.fmtTs(host.cfg.get("last30"), "（已关闭）")).append('\n');
        sb.append("180 天档下次 ").append(UiKit.fmtTs(host.cfg.get("last180"), "（已关闭）")).append('\n');
        final String ch = host.cfg.get("digestChanged");
        sb.append("布局自上次自动备份：")
                .append("true".equals(ch) ? "已变化" : ("false".equals(ch) ? "无变化" : "未知"))
                .append('\n');
        sb.append("当前目录下有 ").append(host.backupCount()).append(" 份备份");
        if ("1".equals(host.cfg.get("ghost"))) {
            sb.append("\n存在一份隐藏安全备份（" + BackupEngine.GHOST_DIR + "）");
        }
        mAutoStatus.setText(sb.toString());
    }

    private static void setSwitch(Switch s, String v) {
        if (s != null && v != null) {
            s.setChecked(Boolean.parseBoolean(v));
        }
    }

    // ------------------------------------------------------------------ 动作

    private void pushConfig() {
        Intent i = new Intent(Const.ACTION_SET_CONFIG);
        i.putExtra(Const.EX_CFG7, sw7.isChecked());
        i.putExtra(Const.EX_CFG30, sw30.isChecked());
        i.putExtra(Const.EX_CFG180, sw180.isChecked());
        i.putExtra(Const.EX_CFG_SHUTDOWN, swShutdown.isChecked());
        i.putExtra(Const.EX_CFG_DIGEST, swDigest.isChecked());
        UiLog.log("⚙️ 保存自动备份开关");
        host.dispatch(i, Const.ACTION_SET_CONFIG, true);
    }

    private void saveExtra() {
        Intent i = new Intent(Const.ACTION_SET_EXTRA);
        try {
            i.putExtra(Const.EX_KEEP_AUTO, Integer.parseInt(text(mKeep)));
        } catch (Throwable ignored) {
        }
        i.putExtra(Const.EX_LOG_ON, swLog != null && swLog.isChecked());
        UiLog.log("⚙️ 保存保留策略");
        host.dispatch(i, Const.ACTION_SET_EXTRA, true);
    }

    private void openUrl(String url) {
        if (url == null || url.isEmpty()) {
            host.toast("链接是空的");
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            host.startActivity(i);
            UiLog.log("🔗 打开 " + url);
        } catch (Throwable t) {
            host.toast("打不开：" + t);
        }
    }

    private void pickDir() {
        host.pickDir();
    }

    @Override
    void onDirPicked(String path) {
        mPath.setText(path);
        host.applyRoot(path);
    }
}
