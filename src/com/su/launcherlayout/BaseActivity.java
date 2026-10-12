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
            // 恢复：桌面进程只负责备料，真正的换库由 App 用 root 从进程外完成
            if (Const.OP_RESTORE.equals(op) && ok && dir != null && dir.indexOf('|') > 0) {
                String[] parts = dir.split("\\|");
                if (parts.length == 3) {
                    try {
                        doRootRestore(parts[0], parts[1], Integer.parseInt(parts[2]));
                    } catch (Throwable t) {
                        UiLog.log("❌ 解析恢复参数失败: " + t);
                    }
                }
            } else if (Const.OP_RESTORE.equals(op) && !ok) {
                UiLog.log("❌ 恢复准备失败：" + msg);
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

    /**
     * 用 root 从【进程外】完成恢复 —— 顺序照旧版 restore.sh 验证过的做法：
     * 1) am force-stop com.miui.home   先杀桌面（释放连接与内存缓存）
     * 2) cp 备份库 -> databases/        再覆盖文件
     * 3) rm -f 同名 -wal/-shm/-journal
     * 4) chown uid:uid + chmod 660 + restorecon   修属主与 SELinux 上下文
     * 5) am start MAIN/HOME             最后拉起来
     */
    protected void doRootRestore(final String src, final String dst, final int uid) {
        UiLog.log("▶ root 恢复 → " + dst.substring(dst.lastIndexOf('/') + 1));
        new Thread(new Runnable() {
            @Override
            public void run() {
                String script =
                        "am force-stop com.miui.home; " +
                        "sleep 1; " +
                        "cp -f '" + src + "' '" + dst + "'; " +
                        "rm -f '" + dst + "-wal' '" + dst + "-shm' '" + dst + "-journal'; " +
                        "chown " + uid + ":" + uid + " '" + dst + "'; " +
                        "chmod 660 '" + dst + "'; " +
                        "restorecon '" + dst + "'; " +
                        // 清掉备料残留，否则桌面下次启动会再接管一次、把用户新改的布局回滚掉
                        "rm -f '" + dst + ".pending' '" + dst + ".pending' "
                                + "'$(dirname '" + dst + "')/.llb_restore_pending'; " +
                        "echo SRC_MD5=$(md5sum '" + src + "' | cut -d' ' -f1); " +
                        "echo DST_MD5=$(md5sum '" + dst + "' | cut -d' ' -f1); " +
                        "am start -a android.intent.action.MAIN -c android.intent.category.HOME";
                String out = "";
                int code = -1;
                StringBuilder report = new StringBuilder();
                // 逐个尝试，并把【每个候选的真实报错】都记下来 —— 只报最后一个会掩盖真相。
                String[] cands = {
                        "su",
                        "/system/bin/su",
                        "/system/xbin/su",
                        "/sbin/su",
                        "/debug_ramdisk/su",
                        "/data/adb/ksu/bin/su",
                        "/data/adb/magisk/su",
                };
                for (String su : cands) {
                    try {
                        java.lang.Process p = Runtime.getRuntime()
                                .exec(new String[]{su, "-c", script});
                        StringBuilder sb = new StringBuilder();
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getInputStream()));
                        String l;
                        while ((l = br.readLine()) != null) {
                            sb.append(l).append('\n');
                        }
                        br.close();
                        int rc = p.waitFor();
                        report.append(su).append(" exit=").append(rc)
                              .append(' ').append(sb.toString().trim()).append(" | ");
                        if (rc == 0) {
                            code = rc;
                            out = sb.toString();
                            break;
                        }
                    } catch (Throwable t) {
                        report.append(su).append(" <- ").append(t.getMessage()).append(" | ");
                    }
                }
                if (out.isEmpty()) {
                    out = report.toString();
                }
                final int c = code;
                final String o = out;
                H.post(new Runnable() {
                    @Override
                    public void run() {
                        String smd5 = pick(o, "SRC_MD5=");
                        String dmd5 = pick(o, "DST_MD5=");
                        final boolean same = smd5 != null && smd5.equals(dmd5);
                        UiLog.log("root 恢复: exit=" + c
                                + " | 源=" + smd5 + " 目标=" + dmd5
                                + (same ? " ✅ 一致" : " ❌ 不一致"));
                        if (!same) {
                            UiLog.log("   完整输出: " + o.replace('\n', ' '));
                        }
                        toast(same ? "恢复完成，桌面正在重新加载"
                                : "恢复失败：写入内容与备份不符，看操作日志");
                    }
                });
            }
        }, "llb-root-restore").start();
    }

    /** 从脚本输出里抓 key=value。 */
    private static String pick(String s, String key) {
        if (s == null) {
            return null;
        }
        int i = s.indexOf(key);
        if (i < 0) {
            return null;
        }
        int j = s.indexOf('\n', i);
        String v = (j < 0) ? s.substring(i + key.length()) : s.substring(i + key.length(), j);
        return v.trim();
    }

    protected String[] lastDirs() {
        return lastDirs;
    }

    /** 子类可以覆盖，用来提示排查方向。 */
    protected void onTimeout() {
    }

    // ================================================================== 恢复流程（共用）

    /** 点「恢复」直接进入确认；后端会自动优先用最稳的一致性快照，不再让用户选文件。 */
    protected void startRestoreFlow(String dir) {
        pendingRestoreDir = dir;
        confirmRestore(dir, null, grids.containsKey(dir) ? grids.get(dir) : "?");
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
        new AlertDialog.Builder(this)
                .setTitle("确认恢复？")
                .setMessage("来源：" + prettyTime(dir) + "\n\n"
                        + "将覆盖当前桌面布局。\n\n"
                        + "· 备份网格 " + backupGrid + " / 当前网格 " + currentGrid + "\n"
                        + "· 恢复前会先自动备份当前状态，恢复后桌面会自动重启\n"
                        + "· 恢复需要 root（首次会弹一次 KernelSU 授权）")
                .setPositiveButton("恢复", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        UiLog.log("▶ 恢复 " + shortName(dir));
                        request(Const.ACTION_RESTORE, dir);
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
