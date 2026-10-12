package com.su.launcherlayout;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 主页：大标题 + 状态卡 + 三个主动作 + 最近一条备份 + 隐藏安全备份找回入口。 */
final class PageHome extends Page {

    private TextView mStatusTitle;
    private TextView mStatusSub;
    private TextView mAutoSum;
    private LinearLayout mRecentRows;
    private LinearLayout mAllRow;
    private TextView mAllText;
    private LinearLayout mGhostCard;
    private TextView mGhostText;
    private View mLogPanel;
    private AlertDialog mRootDialog;
    private boolean mRootPrompted;

    PageHome(MainActivity h) {
        super(h);
    }

    @Override
    protected View build() {
        LinearLayout root = UiKit.column(host);
        root.setBackgroundColor(Theme.page);

        ScrollView scroll = new ScrollView(host);
        LinearLayout c = UiKit.column(host);
        UiKit.pad(host, c, 20, 22, 18);
        scroll.addView(c, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        mLogPanel = host.buildLogPanel(140);
        root.addView(mLogPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(host, 140)));

        // ---------- 标题行 ----------
        LinearLayout bar = UiKit.row(host);
        bar.addView(UiKit.pageTitle(host, "桌面布局备份"), UiKit.weight(host, 1f, 0, 0));
        ImageView more = UiKit.icon(host, R.drawable.ic_more, 22, Theme.onSurfaceVariant);
        more.setBackground(UiKit.clickable(host, 0x00000000, Theme.R_PILL));
        UiKit.pad(host, more, 10, 10, 10);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });
        bar.addView(more);
        c.addView(bar);

        TextView sub = UiKit.text(host, "只备份排版数据，不含 APK · 需已 root + LSPosed",
                Theme.onSurfaceVariant, 12f);
        sub.setPadding(0, UiKit.dp(host, 4), 0, 0);
        c.addView(sub);

        // ---------- 状态卡 ----------
        LinearLayout st = UiKit.row(host);
        st.setBackground(UiKit.round(host, Theme.statusBg(), Theme.R_CARD));
        UiKit.pad(host, st, 18, 16, 16);

        LinearLayout stc = UiKit.column(host);
        mStatusTitle = UiKit.text(host, "检测中…", Theme.statusFg(), 21f);
        mStatusTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        mStatusSub = UiKit.text(host, "", Theme.statusFg(), 12.5f);
        mStatusSub.setAlpha(0.9f);
        mStatusSub.setPadding(0, UiKit.dp(host, 6), 0, 0);
        stc.addView(mStatusTitle);
        stc.addView(mStatusSub);
        st.addView(stc, UiKit.weight(host, 1f, 0, 0));

        ImageView chk = UiKit.icon(host, R.drawable.ic_check, 38, Theme.primary);
        chk.setAlpha(0.8f);
        st.addView(chk);
        c.addView(st, UiKit.full(host, 16, 0));

        // ---------- 隐藏安全备份（默认隐藏） ----------
        mGhostCard = UiKit.card(host);
        UiKit.pad(host, mGhostCard, 16, 14, 14);
        mGhostText = UiKit.text(host, "", Theme.onSurface, 13f);
        mGhostCard.addView(mGhostText);
        LinearLayout gr = UiKit.row(host);
        gr.addView(UiKit.mini(host, "找回删除前的备份", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recoverGhost();
            }
        }));
        gr.addView(UiKit.mini(host, "丢弃", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dropGhost();
            }
        }), UiKit.wrap(host, 8, 0));
        mGhostCard.addView(gr, UiKit.full(host, 12, 0));
        mGhostCard.setVisibility(View.GONE);
        c.addView(mGhostCard, UiKit.full(host, 14, 0));

        // ---------- 主动作 ----------
        LinearLayout act = UiKit.card(host);
        act.addView(UiKit.itemRow(host, R.drawable.ic_box, "立即备份桌面布局",
                "抓一份当前排版，几百 KB",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        UiLog.log("▶ 立即备份");
                        host.request(Const.ACTION_BACKUP, null);
                    }
                }));
        act.addView(UiKit.dividerRow(host));
        act.addView(UiKit.itemRow(host, R.drawable.ic_check, "一键恢复最近备份",
                "直接用最近一次备份覆盖",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        oneTapRestore();
                    }
                }));
        act.addView(UiKit.dividerRow(host));
        act.addView(UiKit.itemRow(host, R.drawable.ic_tune, "重启桌面进程",
                "让模块注入 / 布局重载生效",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        confirmRestart();
                    }
                }));
        c.addView(act, UiKit.full(host, 14, 0));

        // ---------- 自动备份摘要 ----------
        LinearLayout ack = UiKit.card(host);
        LinearLayout ar = UiKit.row(host);
        ar.setBackground(UiKit.clickable(host, 0x00000000, Theme.R_SMALL));
        UiKit.pad(host, ar, 16, 14, 14);
        ar.addView(UiKit.iconMr(host, R.drawable.ic_tune, 22, Theme.primary, 14));
        LinearLayout arc = UiKit.column(host);
        arc.addView(UiKit.rowTitle(host, "自动备份"));
        mAutoSum = UiKit.rowSummary(host, "读取中…");
        mAutoSum.setPadding(0, UiKit.dp(host, 2), 0, 0);
        arc.addView(mAutoSum);
        ar.addView(arc, UiKit.weight(host, 1f, 0, 0));
        ar.addView(UiKit.icon(host, R.drawable.ic_chevron, 18, Theme.onSurfaceVariant));
        ar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.showTab(MainActivity.TAB_SETTINGS);
            }
        });
        ack.addView(ar);
        c.addView(ack, UiKit.full(host, 14, 0));

        // ---------- 最近备份 ----------
        LinearLayout rk = UiKit.card(host);
        mRecentRows = UiKit.column(host);
        rk.addView(mRecentRows);
        LinearLayout hall = UiKit.row(host);
        hall.setBackground(UiKit.clickable(host, 0x00000000, Theme.R_SMALL));
        UiKit.pad(host, hall, 16, 12, 12);
        mAllText = UiKit.text(host, "查看全部备份", Theme.primary, 14f);
        mAllText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hall.addView(mAllText, UiKit.weight(host, 1f, 0, 0));
        hall.addView(UiKit.icon(host, R.drawable.ic_chevron, 18, Theme.primary));
        hall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.showTab(MainActivity.TAB_BACKUP);
            }
        });
        mAllRow = hall;
        rk.addView(UiKit.dividerRow(host));
        rk.addView(hall);
        c.addView(rk, UiKit.full(host, 14, 0));

        renderRecent(host.lastDirs());
        updateAutoSummary();
        updateGhost();
        updateLogVisibility();
        return root;
    }

    /** 「显示操作日志」关掉后，主页就跟其它页一样干净。 */
    private void updateLogVisibility() {
        if (mLogPanel == null) {
            return;
        }
        final boolean on = !"0".equals(host.cfg.get("logOn"));
        mLogPanel.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ 首次强制设定备份路径

    /**
     * 首次使用必须让用户自己指定备份目录，且弹窗**不可取消**。
     * 理由：默认丢在 Download 里的话，用户清理下载时会把备份一起删掉。
     */
    private void handleRootConfig(String op, boolean ok, String msg) {
        if (Const.OP_SET_ROOT.equals(op)) {
            if (ok) {
                dismissRootDialog();
                host.toast("备份路径已设定");
            } else {
                host.toast(firstLine(msg));
            }
            return;
        }
        if (mRootPrompted || mRootDialog != null) {
            return;
        }
        if (!"0".equals(host.cfg.get("rootSet"))) {
            return;   // 已经设过了，或者还没拿到配置
        }
        mRootPrompted = true;
        promptRoot();
    }

    private void dismissRootDialog() {
        if (mRootDialog != null) {
            try {
                mRootDialog.dismiss();
            } catch (Throwable ignored) {
            }
            mRootDialog = null;
        }
    }

    private void promptRoot() {
        final LinearLayout box = UiKit.column(host);
        UiKit.pad(host, box, 20, 4, 4);

        final EditText input = new EditText(host);
        input.setSingleLine(true);
        input.setTextSize(13f);
        input.setTextColor(Theme.onSurface);
        input.setHint("/storage/emulated/0/我的备份");
        input.setHintTextColor(Theme.onSurfaceVariant);
        input.setBackground(UiKit.round(host, Theme.dark ? 0xFF171B15 : 0xFFF2F6EC, Theme.R_SMALL));
        UiKit.pad(host, input, 12, 10, 10);
        box.addView(input, UiKit.full(host, 10, 0));

        LinearLayout row = UiKit.row(host);
        row.addView(UiKit.subtle(host, "从文件选择器选", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.pickDir();
            }
        }), UiKit.weight(host, 1f, 0, 5));
        row.addView(UiKit.filled(host, "保存", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.applyRoot(input.getText().toString().trim());
            }
        }), UiKit.weight(host, 1f, 5, 0));
        box.addView(row, UiKit.full(host, 12, 0));

        mRootDialog = new AlertDialog.Builder(host)
                .setTitle("先给备份选个位置")
                .setMessage("给备份指定一个单独的目录，不要放在 Download 里。\n只能选手机内部存储。")
                .setView(box)
                .setCancelable(false)
                .create();
        try {
            mRootDialog.setCanceledOnTouchOutside(false);
        } catch (Throwable ignored) {
        }
        mRootDialog.show();
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    @Override
    void onDirPicked(String path) {
        host.applyRoot(path);
    }

    // ------------------------------------------------------------------ 上下线

    void showOffline() {
        if (mStatusTitle == null) {
            return;
        }
        mStatusTitle.setText("未响应");
        mStatusSub.setText("模块没被注入：① LSPosed 里启用模块并勾「系统桌面」"
                + " ② 重启桌面进程 ③ 仍无效重启手机");
        mStatusSub.setTextColor(Theme.statusFg());
    }

    private void showOnline() {
        mStatusTitle.setText("工作中");
    }

    // ------------------------------------------------------------------ 回执

    @Override
    void onResult(String op, boolean ok, String msg, String dir, String[] items, String[] grids) {
        if (Const.OP_INFO.equals(op)) {
            if (ok) {
                showOnline();
                host.request(Const.ACTION_LIST, null);
                host.request(Const.ACTION_GET_CONFIG, null);
            } else {
                showOffline();
            }
            return;
        }
        if (Const.OP_LIST.equals(op) && items != null) {
            renderRecent(items);
            updateGhost();
            return;
        }
        if (Const.OP_GET_CONFIG.equals(op) || Const.OP_SET_CONFIG.equals(op)
                || Const.OP_SET_ROOT.equals(op) || Const.OP_SET_EXTRA.equals(op)
                || Const.OP_DELETE_ALL.equals(op) || Const.OP_DROP_GHOST.equals(op)
                || Const.OP_RECOVER_GHOST.equals(op)) {
            updateAutoSummary();
            updateGhost();
            updateLogVisibility();
            handleRootConfig(op, ok, msg);
            if (Const.OP_RECOVER_GHOST.equals(op)) {
                UiLog.log((ok ? "✅ " : "❌ ") + msg);
                host.toast(ok ? "已找回，去「备份」页查看" : msg);
                host.request(Const.ACTION_LIST, null);
            }
            return;
        }
        if (Const.OP_FILES.equals(op)) {
            if (ok && items != null && items.length > 0) {
                host.showFilePicker(host.pendingRestoreDir, items);
            } else {
                UiLog.log("❌ 读取可选文件失败：" + msg);
            }
            return;
        }
        if (Const.OP_BACKUP.equals(op)) {
            UiLog.log((ok ? "✅ " : "❌ ") + "备份 → " + (ok ? dir : msg));
            return;
        }
        if (Const.OP_RESTORE.equals(op)) {
            UiLog.log((ok ? "✅ " : "❌ ") + "恢复 → " + msg);
            return;
        }
        if (Const.OP_RESTART.equals(op)) {
            UiLog.log((ok ? "✅ " : "❌ ") + "重启桌面 → " + msg);
        }
    }

    // ------------------------------------------------------------------ 渲染

    private void updateAutoSummary() {
        if (mAutoSum == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if ("true".equals(host.cfg.get("en7")) || "true".equals(host.cfg.get("en30"))
                || "true".equals(host.cfg.get("en180"))) {
            if ("true".equals(host.cfg.get("en7"))) {
                sb.append("每 7 天 · 下次 ").append(UiKit.fmtTs(host.cfg.get("last7"), "—"));
            } else if ("true".equals(host.cfg.get("en30"))) {
                sb.append("每 30 天 · 下次 ").append(UiKit.fmtTs(host.cfg.get("last30"), "—"));
            } else {
                sb.append("每 180 天 · 下次 ").append(UiKit.fmtTs(host.cfg.get("last180"), "—"));
            }
        } else {
            sb.append("已关闭 · 点这里去打开");
        }
        sb.append("\n上次自动备份 ").append(UiKit.fmtTs(host.cfg.get("lastAuto"), "还没有"));
        mAutoSum.setText(sb.toString());
    }

    private void renderRecent(String[] items) {
        if (mRecentRows == null) {
            return;
        }
        mRecentRows.removeAllViews();
        mAllText.setText(items.length == 0
                ? "还没有备份，点上面「立即备份桌面布局」"
                : ("查看全部 " + items.length + " 份备份"));

        LinearLayout r = UiKit.row(host);
        UiKit.pad(host, r, 16, 14, 14);
        LinearLayout col = UiKit.column(host);
        if (items.length == 0) {
            col.addView(UiKit.rowTitle(host, "还没有备份"));
            col.addView(UiKit.rowSummary(host, "崩了才想起来备份就晚了"));
        } else {
            final String name = items[0];
            String g = host.grids.containsKey(name) ? host.grids.get(name) : "?";
            boolean same = g.equals(host.currentGrid);
            col.addView(UiKit.rowTitle(host, UiKit.prettyTime(name)));
            TextView s = UiKit.rowSummary(host, "网格 " + g
                    + (same ? "  ✓ 与当前一致" : "  ⚠️ 与当前不一致（当前 " + host.currentGrid + "）"));
            if (!same) {
                s.setTextColor(Theme.error);
            }
            s.setPadding(0, UiKit.dp(host, 3), 0, 0);
            col.addView(s);
            LinearLayout br = UiKit.row(host);
            br.addView(UiKit.mini(host, "打开", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.openDir(name);
                }
            }));
            br.addView(UiKit.mini(host, "恢复", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.startRestoreFlow(name);
                }
            }), UiKit.wrap(host, 8, 0));
            col.addView(br, UiKit.full(host, 10, 0));
        }
        r.addView(col, UiKit.weight(host, 1f, 0, 0));
        r.addView(UiKit.icon(host, R.drawable.ic_chevron, 18, Theme.onSurfaceVariant));
        r.setBackground(UiKit.clickable(host, 0x00000000, Theme.R_SMALL));
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.showTab(MainActivity.TAB_BACKUP);
            }
        });
        mRecentRows.addView(r);
    }

    private void updateGhost() {
        if (mGhostCard == null) {
            return;
        }
        boolean has = "1".equals(host.cfg.get("ghost"));
        mGhostCard.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            mGhostText.setText("已保留一份删除前的安全备份，可随时找回。");
        }
    }

    // ------------------------------------------------------------------ 动作

    private void recoverGhost() {
        new AlertDialog.Builder(host)
                .setTitle("找回删除前的备份？")
                .setMessage("把它变回一份正常备份，出现在「备份」页，之后你自己决定要不要恢复。\n"
                        + "不会直接动你的桌面。")
                .setPositiveButton("找回", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        UiLog.log("▶ 找回删除前的安全备份（转为可见）");
                        host.recoverGhost();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void dropGhost() {
        new AlertDialog.Builder(host)
                .setTitle("丢弃安全备份？")
                .setMessage("删除后无法恢复。")
                .setPositiveButton("丢弃", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        host.discardGhost();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void oneTapRestore() {
        final String dir = host.lastBackupDir;
        if (dir == null) {
            host.toast("还没有备份，先点「立即备份桌面布局」");
            return;
        }
        String g = host.grids.containsKey(dir) ? host.grids.get(dir) : "?";
        new AlertDialog.Builder(host)
                .setTitle("恢复到最近备份？")
                .setMessage("将用最近一次备份覆盖当前桌面布局：\n\n  " + UiKit.prettyTime(dir)
                        + "\n  网格 " + g + "（当前 " + host.currentGrid + "）\n\n"
                        + "· 恢复前会先自动备份当前状态\n"
                        + "· 恢复后桌面会自动重启\n"
                        + (g.equals(host.currentGrid) ? "" : "⚠️ 网格不一致，图标可能错位\n"))
                .setPositiveButton("恢复", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        UiLog.log("▶ 一键恢复最近备份");
                        host.request(Const.ACTION_RESTORE, dir);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmRestart() {
        new AlertDialog.Builder(host)
                .setTitle("重启桌面进程？")
                .setMessage("桌面会重启一次（屏幕闪一下，正常）。\n"
                        + "刚启用模块、或刚恢复过布局，用它生效。")
                .setPositiveButton("重启", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        UiLog.log("▶ 重启桌面进程");
                        host.restartLauncher();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showMenu() {
        final String[] items = {"重新检测模块", "重启桌面进程", "打开备份目录", "复制备份路径"};
        new AlertDialog.Builder(host)
                .setTitle("更多")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0:
                                UiLog.log("▶ 重新检测");
                                host.request(Const.ACTION_INFO, null);
                                break;
                            case 1:
                                confirmRestart();
                                break;
                            case 2:
                                host.openDir(null);
                                break;
                            default:
                                host.copyPath(host.rootPath);
                                break;
                        }
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }
}
