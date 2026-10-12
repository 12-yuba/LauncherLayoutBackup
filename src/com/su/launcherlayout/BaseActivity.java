package com.su.launcherlayout;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 三个页面的公共基类：负责广播请求 / 回执 / 超时记账 / 配置解析 / 恢复流程。
 *
 * <p>子类只需要实现 {@link #buildContentView()} 和 {@link #onResult}。
 */
abstract class BaseActivity extends Activity {

    protected static final long TIMEOUT_MS = 9000L;

    protected final Handler H = new Handler(Looper.getMainLooper());
    private final AtomicInteger mToken = new AtomicInteger(1);
    private final HashMap<String, Runnable> mPending = new HashMap<String, Runnable>();

    /** 最近一次配置快照（key=value）。 */
    protected final HashMap<String, String> cfg = new HashMap<String, String>();
    /** 备份目录名 -> 网格标注。 */
    protected final HashMap<String, String> grids = new HashMap<String, String>();

    protected String currentGrid = "?";
    protected String rootPath = Schedule.DEFAULT_ROOT;
    protected String lastBackupDir;
    protected String pendingRestoreDir;
    /** 最近一次 LIST 的原始结果（新 -> 旧）。 */
    protected String[] lastDirs = new String[0];
    private boolean mRegistered;

    private static final Pattern DB_PAT = Pattern.compile("^launcher(\\d+)x(\\d+)\\.db$");

    protected static String parseGrid(String dbName) {
        return UiKit.parseGrid(dbName);
    }

    protected static String shortName(String dir) {
        return UiKit.shortName(dir);
    }

    protected static String prettyTime(String dir) {
        return UiKit.prettyTime(dir);
    }

    protected static String fmtTs(String v, String zeroText) {
        return UiKit.fmtTs(v, zeroText);
    }

    // ================================================================== 生命周期

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !Const.ACTION_RESULT.equals(intent.getAction())) {
                return;
            }
            final String token = intent.getStringExtra(Const.EX_TOKEN);
            if (token != null) {
                Runnable r = mPending.remove(token);
                if (r != null) {
                    H.removeCallbacks(r);
                }
            }
            final String op = intent.getStringExtra(Const.EX_OP);
            final boolean ok = intent.getBooleanExtra(Const.EX_OK, false);
            final String msg = intent.getStringExtra(Const.EX_MSG);
            final String dir = intent.getStringExtra(Const.EX_DIR);
            final String[] items = intent.getStringArrayExtra(Const.EX_ITEMS);
            final String[] gridArr = intent.getStringArrayExtra(Const.EX_GRIDS);

            if (items != null && carriesConfig(op)) {
                absorbConfig(items);
            }
            if (items != null && Const.OP_LIST.equals(op)) {
                grids.clear();
                lastDirs = items;
                if (gridArr != null) {
                    for (int i = 0; i < items.length && i < gridArr.length; i++) {
                        grids.put(items[i], gridArr[i]);
                    }
                }
                if (items.length > 0) {
                    lastBackupDir = items[0];
                }
            }
            onResult(op, ok, msg, dir, items, gridArr);
            UiLog.save(BaseActivity.this);
        }
    };

    private static boolean carriesConfig(String op) {
        return Const.OP_INFO.equals(op) || Const.OP_GET_CONFIG.equals(op)
                || Const.OP_SET_CONFIG.equals(op) || Const.OP_SET_ROOT.equals(op)
                || Const.OP_SET_EXTRA.equals(op) || Const.OP_DELETE_ALL.equals(op)
                || Const.OP_DROP_GHOST.equals(op) || Const.OP_RECOVER_GHOST.equals(op);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiLog.load(this);
        setContentView(buildContentView());
        // 必须在发第一个请求之前注册，否则回执可能在 START 事务前回来被丢弃
        registerExported(this, mReceiver, new IntentFilter(Const.ACTION_RESULT));
        mRegistered = true;
        UiLog.log("──────── 打开 App ────────");
        afterCreate();
    }

    @Override
    protected void onStop() {
        UiLog.save(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (mRegistered) {
            try {
                unregisterReceiver(mReceiver);
            } catch (Throwable ignored) {
            }
            mRegistered = false;
        }
        UiLog.unbind();
        UiLog.save(this);
        super.onDestroy();
    }

    // ================================================================== 子类接口

    protected abstract View buildContentView();

    protected void afterCreate() {
    }

    protected abstract void onResult(String op, boolean ok, String msg, String dir,
                                     String[] items, String[] grids);

    // ================================================================== 配置

    protected void absorbConfig(String[] kv) {
        for (String s : kv) {
            int i = s.indexOf('=');
            if (i > 0) {
                cfg.put(s.substring(0, i), s.substring(i + 1));
            }
        }
        if (cfg.containsKey("root")) {
            rootPath = cfg.get("root");
        }
    }

    protected int backupCount() {
        try {
            return Integer.parseInt(cfg.get("count"));
        } catch (Throwable t) {
            return 0;
        }
    }

    // ================================================================== 广播请求

    protected void request(String action, String dir) {
        request(action, dir, null);
    }

    protected void request(String action, String dir, String dbFile) {
        Intent i = new Intent(action);
        if (dir != null) {
            i.putExtra(Const.EX_DIR, dir);
        }
        if (dbFile != null) {
            i.putExtra(Const.EX_DBFILE, dbFile);
        }
        dispatch(i, action, false);
    }

    /** 统一出口：装 token、装包名、起超时、发广播。 */
    protected void dispatch(Intent i, String action, boolean quiet) {
        final String token = String.valueOf(mToken.getAndIncrement());
        armTimeout(token, false);
        i.putExtra(Const.EX_TOKEN, token);
        i.setPackage(Const.TARGET_PKG);
        try {
            sendBroadcast(i);
        } catch (Throwable t) {
            Runnable r = mPending.remove(token);
            if (r != null) {
                H.removeCallbacks(r);
            }
            UiLog.log("❌ 广播发送失败: " + t);
        }
    }

    protected void armTimeout(final String token, final boolean restart) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                mPending.remove(token);
                UiLog.log("⚠️ 请求超时，桌面进程无响应");
                onTimeout();
                if (restart) {
                    confirmRootRestart();
                }
            }
        };
        mPending.put(token, r);
        H.postDelayed(r, TIMEOUT_MS);
    }

    protected boolean isPending(String token) {
        return mPending.containsKey(token);
    }

    /** 重启桌面进程；若模块还没注入（广播无人接），超时后会自动给出 root 兜底选项。 */
    protected void restartLauncher() {
        final String token = "restart-" + System.currentTimeMillis();
        armTimeout(token, true);
        Intent i = new Intent(Const.ACTION_RESTART);
        i.putExtra(Const.EX_TOKEN, token);
        i.setPackage(Const.TARGET_PKG);
        try {
            sendBroadcast(i);
        } catch (Throwable t) {
            UiLog.log("❌ 广播失败: " + t);
        }
    }

    /** 丢弃隐藏安全备份。 */
    protected void discardGhost() {
        Intent i = new Intent(Const.ACTION_DROP_GHOST);
        dispatch(i, Const.ACTION_DROP_GHOST, true);
    }

    /** 找回隐藏安全备份：去掉名字前面的点，让它变成一份正常可见的备份。 */
    protected void recoverGhost() {
        Intent i = new Intent(Const.ACTION_RECOVER_GHOST);
        dispatch(i, Const.ACTION_RECOVER_GHOST, true);
    }

    protected String[] lastDirs() {
        return lastDirs;
    }

    /** 子类可以覆盖，用来提示排查方向。 */
    protected void onTimeout() {
    }

    // ================================================================== 恢复流程（共用）

    protected void startRestoreFlow(String dir) {
        pendingRestoreDir = dir;
        UiLog.log("▶ 选择要恢复的文件: " + shortName(dir));
        request(Const.ACTION_FILES, dir);
    }

    /** 第一步：从备份目录里列出所有可恢复文件，让用户自由挑一个。 */
    protected void showFilePicker(final String dir, final String[] files) {
        final String backupGrid = grids.containsKey(dir) ? grids.get(dir) : "?";
        StringBuilder m = new StringBuilder();
        m.append("备份：").append(prettyTime(dir)).append('\n');
        m.append("该备份网格：").append(backupGrid).append('\n');
        m.append("当前网格：").append(currentGrid).append('\n');
        if (!backupGrid.equals(currentGrid)) {
            m.append("⚠️ 网格不一致，图标可能错位。建议先改成 ").append(backupGrid).append("\n");
        }
        m.append("\n请选择要还原的文件：");

        new AlertDialog.Builder(this)
                .setTitle("选择要恢复的库文件")
                .setMessage(m.toString())
                .setItems(files, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        confirmRestore(dir, files[which], backupGrid);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 第二步：确认并执行。 */
    protected void confirmRestore(final String dir, final String file, String backupGrid) {
        final boolean snap = file.endsWith(".db.snapshot");
        new AlertDialog.Builder(this)
                .setTitle("确认恢复？")
                .setMessage("来源：\n  " + prettyTime(dir) + "\n  └ " + file + "\n\n"
                        + "将覆盖当前桌面布局。\n\n"
                        + "· 备份网格 " + backupGrid + " / 当前网格 " + currentGrid + "\n"
                        + "· 恢复前会先自动备份当前状态，恢复后桌面会自动重启\n"
                        + (snap ? "· 使用一致性快照文件，数据自洽，最稳\n" : ""))
                .setPositiveButton("恢复", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        UiLog.log("▶ 恢复 " + shortName(dir) + " / " + file);
                        request(Const.ACTION_RESTORE, dir, file);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================================================================== 路径工具

    /** 用 DocumentsUI 打开目录（路径必须在内置存储内；越界则只复制路径）。 */
    protected void openDir(String dirName) {
        final String abs = (dirName == null || dirName.isEmpty())
                ? rootPath : (rootPath + "/" + dirName);
        final String docId = Schedule.toDocId(abs);
        if (docId != null) {
            try {
                Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/"
                        + Uri.encode(docId));
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "vnd.android.document/directory");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(i);
                UiLog.log("📂 打开目录: " + abs);
                return;
            } catch (Throwable t) {
                UiLog.log("打开目录失败，回退复制路径: " + t.getClass().getSimpleName());
            }
        }
        copyPath(abs);
        toast("无法打开文件管理器，路径已复制");
    }

    protected void copyPath(String abs) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("path", abs));
            }
            toast("已复制：" + abs);
            UiLog.log("📋 复制路径: " + abs);
        } catch (Throwable t) {
            UiLog.log("复制失败: " + t);
        }
    }

    protected void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ================================================================== 日志面板

    /** 底部常驻日志面板，返回可直接 addView 的 View。 */
    protected View buildLogPanel(int heightDp) {
        final LinearLayout panel = UiKit.column(this);
        panel.setBackgroundColor(Theme.dark ? 0xFF0B0E09 : 0xFFE9EEE4);
        UiKit.pad(this, panel, 16, 10, 10);

        LinearLayout head = UiKit.row(this);
        head.addView(UiKit.sectionLabel(this, "操作日志"), UiKit.weight(this, 1f, 0, 0));
        head.addView(UiKit.mini(this, "清空", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiLog.clear(BaseActivity.this);
                UiLog.log("（日志已清空）");
            }
        }));
        panel.addView(head);

        ScrollView scroll = new ScrollView(this);
        TextView view = UiKit.mono(this, "", Theme.onSurfaceVariant, 10.5f);
        view.setTextIsSelectable(true);
        view.setPadding(0, UiKit.dp(this, 6), 0, 0);
        scroll.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        UiLog.bind(scroll, view);
        return panel;
    }

    // ================================================================== 目录选择（共用）

    protected static final int REQ_TREE = 1001;

    /** 打开系统文件选择器选目录（只接受手机内部存储）。 */
    protected void pickDir() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            String docId = Schedule.toDocId(rootPath);
            if (docId != null) {
                i.putExtra("android.provider.extra.INITIAL_URI",
                        Uri.parse("content://com.android.externalstorage.documents/document/"
                                + Uri.encode(docId)));
            }
            startActivityForResult(i, REQ_TREE);
        } catch (Throwable t) {
            toast("没有可用的文件选择器：" + t);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_TREE || data == null || data.getData() == null) {
            return;
        }
        final String s = data.getData().toString();
        final int idx = s.indexOf("/tree/");
        if (idx < 0) {
            toast("无法解析所选目录");
            return;
        }
        final String docId = Uri.decode(s.substring(idx + "/tree/".length()));
        final String p = docIdToPath(docId);
        if (p == null) {
            new AlertDialog.Builder(this)
                    .setTitle("不支持的位置")
                    .setMessage("只能选手机内部存储。\n\n你选的是：\n" + docId)
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }
        onDirPicked(p);
    }

    /** 文件选择器选到合法目录后回调。 */
    protected void onDirPicked(String path) {
    }

    /** 把路径下发给桌面进程保存（会做合法性 + 可写性校验）。 */
    protected void applyRoot(String path) {
        UiLog.log("⚙️ 保存备份路径: " + path);
        Intent i = new Intent(Const.ACTION_SET_ROOT);
        i.putExtra(Const.EX_ROOT, path);
        dispatch(i, Const.ACTION_SET_ROOT, true);
    }

    private static String docIdToPath(String docId) {
        if (docId == null) {
            return null;
        }
        if (docId.startsWith("primary:")) {
            final int user = android.os.Process.myUid() / 100000;
            String rest = docId.substring("primary:".length());
            return "/storage/emulated/" + user + (rest.isEmpty() ? "" : "/" + rest);
        }
        return null; // SD 卡等其它 volume
    }

    // ================================================================== 兜底重启

    protected void confirmRootRestart() {
        new AlertDialog.Builder(this)
                .setTitle("广播没响应")
                .setMessage("模块还没注入，广播重启无效。改用 root 结束桌面进程？")
                .setPositiveButton("用 root 重启", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        rootRestart();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    protected void rootRestart() {
        UiLog.log("尝试 root 强制重启桌面…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String out = "";
                int code = -1;
                String[] suPaths = {"su", "/data/adb/ksu/bin/su", "/system/bin/su"};
                for (String su : suPaths) {
                    try {
                        java.lang.Process p = Runtime.getRuntime()
                                .exec(new String[]{su, "-c", "am force-stop com.miui.home"});
                        StringBuilder sb = new StringBuilder();
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getInputStream()));
                        String l;
                        while ((l = br.readLine()) != null) {
                            sb.append(l).append('\n');
                        }
                        br.close();
                        code = p.waitFor();
                        out = sb.toString();
                        break;
                    } catch (Throwable t) {
                        out = String.valueOf(t);
                    }
                }
                final int c = code;
                final String o = out;
                H.post(new Runnable() {
                    @Override
                    public void run() {
                        UiLog.log("root 重启: exit=" + c + (o.length() == 0 ? "" : " " + o.trim()));
                        toast(c == 0 ? "已发送重启命令，桌面稍后自动恢复"
                                : "root 重启失败，请手动重启手机");
                        H.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                request(Const.ACTION_INFO, null);
                            }
                        }, 7000L);
                    }
                });
            }
        }, "llb-root-restart").start();
    }

    /** Android 13+ 跨应用动态注册需要 RECEIVER_EXPORTED；本机编译用 jar 较老，走反射。 */
    static void registerExported(Context ctx, BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                Method m = Context.class.getMethod("registerReceiver",
                        BroadcastReceiver.class, IntentFilter.class, int.class);
                m.invoke(ctx, r, f, Integer.valueOf(2 /* RECEIVER_EXPORTED */));
                return;
            } catch (Throwable ignored) {
            }
        }
        ctx.registerReceiver(r, f);
    }
}
