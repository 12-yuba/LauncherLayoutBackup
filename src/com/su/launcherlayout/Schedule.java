package com.su.launcherlayout;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * 自动备份排程。
 *
 * <p>跑在「桌面进程」内 —— 桌面是常驻进程，手机一开机就活着，
 * 所以这里既不需要 AlarmManager，也不需要后台保活，更不要求模块 App 在跑。
 *
 * <p>三档周期各自独立计时：每 7 天 / 每 30 天 / 每 180 天。
 * 若同一时刻多档到期，只做一次备份（不会重复生成）。
 * 另可选「仅在布局真的发生变化时才备份」，避免堆积一堆一模一样的备份。
 */
final class Schedule {

    static final long DAY = 24L * 3600L * 1000L;
    static final long P7 = 7 * DAY;
    static final long P30 = 30 * DAY;
    static final long P180 = 180 * DAY;

    /** 检查间隔。桌面本来就在跑，每小时查一次代价可忽略。 */
    private static final long CHECK_INTERVAL = 3600L * 1000L;

    private static final String FILE_NAME = "llb_schedule.properties";

    static final String DEFAULT_ROOT = "/storage/emulated/0/Download";

    /**
     * 备份根目录，可在设置里改。
     *
     * <p><b>只允许手机内部存储</b>（{@code /storage/emulated/<user>/} 及其别名 /sdcard）。
     * SD 卡（{@code /storage/XXXX-XXXX}）、U 盘 / OTG（{@code /mnt/media_rw/*}）一律拒绝 ——
     * 那些介质会被拔掉，一旦拔掉备份就在 App 里"消失"，反而更危险。
     */
    static volatile String rootPath = DEFAULT_ROOT;

    /** 自动备份最多保留份数；0 = 不限制。手动备份永不自动删除。默认 5。 */
    static volatile int keepAuto = 5;

    /** 是否在主页显示操作日志面板。 */
    static volatile boolean logEnabled = true;

    /** 用户是否已经明确设定过备份路径（没设过就强制弹窗要求设定）。 */
    static volatile boolean rootConfigured = false;

    /** 鸣谢链接（会写进每个备份目录的 README.txt，方便备份文件脱离 App 流传时仍保留署名）。 */
    static volatile String link1Name = "瞑望";
    static volatile String link1Url = "https://www.coolapk.com/u/1175257";
    static volatile String link2Name = "桃酥打水";
    static volatile String link2Url = "https://www.coolapk.com/u/4111144";

    static volatile boolean en7 = true;
    static volatile boolean en30 = true;
    static volatile boolean en180 = true;
    static volatile boolean onShutdown = true;
    static volatile boolean digestOnly = true;

    static volatile long last7;
    static volatile long last30;
    static volatile long last180;
    static volatile long lastAuto;
    static volatile String lastDigest = "";

    private static boolean sStarted;
    private static boolean sShutdownRegistered;

    private Schedule() {
    }

    // ================================================================== 启动

    /** 由 Bridge 在桌面进程注入完成后调用一次。 */
    static synchronized void start(final Context ctx) {
        load(ctx);
        registerShutdownReceiver(ctx);
        if (sStarted) {
            return;
        }
        sStarted = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(30000L); // 等桌面启动稳定，别和启动抢 IO
                } catch (InterruptedException e) {
                    return;
                }
                while (true) {
                    try {
                        checkNow(ctx, "定时");
                    } catch (Throwable x) {
                        Bridge.log(Log.ERROR, Const.TAG, "自动备份检查异常", x);
                    }
                    try {
                        Thread.sleep(CHECK_INTERVAL);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "llb-scheduler");
        t.setDaemon(true);
        t.start();
        Bridge.log(Log.INFO, Const.TAG, "自动备份排程已启动（7天=" + en7 + " / 30天=" + en30
                + " / 180天=" + en180 + " / 关机前=" + onShutdown + " / 仅变化时=" + digestOnly + "）");
    }

    /**
     * 关机 / 重启前备份。
     *
     * <p>动态注册的接收器不受 Android 8+ 隐式广播限制，因此能收到 ACTION_SHUTDOWN；
     * manifest 静态注册则收不到。注意：非正常断电（强制重启 / 拔电）不会发这个广播。
     */
    private static synchronized void registerShutdownReceiver(final Context ctx) {
        if (sShutdownRegistered) {
            return;
        }
        sShutdownRegistered = true;
        try {
            BroadcastReceiver r = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    final String action = (i == null) ? "?" : String.valueOf(i.getAction());
                    if (!onShutdown) {
                        Bridge.log(Log.INFO, Const.TAG, "收到 " + action + "，但「关机前备份」已关闭，跳过");
                        return;
                    }
                    Bridge.log(Log.WARN, Const.TAG, "收到 " + action + "，开始关机前备份");
                    try {
                        BackupEngine.Result res = BackupEngine.backup(ctx, "关机前");
                        Bridge.log(res.ok ? Log.WARN : Log.ERROR, Const.TAG,
                                "关机前备份" + (res.ok ? "完成：" + res.dir : "失败：" + res.message));
                        if (res.ok) {
                            lastAuto = System.currentTimeMillis();
                            lastDigest = BackupEngine.layoutDigest(ctx);
                            save(ctx);
                            try {
                                BackupEngine.pruneAuto(keepAuto);
                            } catch (Throwable ignored) {
                            }
                        }
                    } catch (Throwable t) {
                        Bridge.log(Log.ERROR, Const.TAG, "关机前备份异常", t);
                    }
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SHUTDOWN);              // android.intent.action.ACTION_SHUTDOWN
            f.addAction("android.intent.action.ACTION_SHUTDOWN");
            f.addAction("android.intent.action.QUICKBOOT_POWEROFF");
            f.addAction(Intent.ACTION_REBOOT);
            Bridge.registerExportedReceiver(ctx, r, f);
            Bridge.log(Log.INFO, Const.TAG, "已监听关机 / 重启广播");
        } catch (Throwable t) {
            Bridge.log(Log.ERROR, Const.TAG, "注册关机接收器失败", t);
        }
    }

    // ================================================================== 检查

    /** 到期且（可选）布局有变化时执行一次备份。 */
    static void checkNow(Context ctx, String reason) {
        final long now = System.currentTimeMillis();
        final boolean due7 = en7 && (last7 <= 0 || now - last7 >= P7);
        final boolean due30 = en30 && (last30 <= 0 || now - last30 >= P30);
        final boolean due180 = en180 && (last180 <= 0 || now - last180 >= P180);
        if (!due7 && !due30 && !due180) {
            return;
        }

        if (digestOnly) {
            final String cur = BackupEngine.layoutDigest(ctx);
            if (cur != null && cur.equals(lastDigest)) {
                // 布局没变，不做无意义的重复备份；时间戳不推进，等布局真变了再备。
                Bridge.log(Log.INFO, Const.TAG, "自动备份：" + reason + " 已到期，但布局无变化，跳过");
                return;
            }
        }

        BackupEngine.Result r;
        try {
            r = BackupEngine.backup(ctx, reason);
        } catch (Throwable t) {
            Bridge.log(Log.ERROR, Const.TAG, "自动备份失败", t);
            return;
        }
        if (!r.ok) {
            Bridge.log(Log.ERROR, Const.TAG, "自动备份失败：" + r.message);
            return;
        }
        if (due7) {
            last7 = now;
        }
        if (due30) {
            last30 = now;
        }
        if (due180) {
            last180 = now;
        }
        lastAuto = now;
        lastDigest = BackupEngine.layoutDigest(ctx);
        save(ctx);
        try {
            BackupEngine.pruneAuto(keepAuto);
        } catch (Throwable ignored) {
        }
        Bridge.log(Log.INFO, Const.TAG, "自动备份完成(" + reason + ")：" + r.dir);
    }

    // ================================================================== 配置

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    static synchronized void load(Context ctx) {
        final Properties p = new Properties();
        InputStream in = null;
        try {
            File f = file(ctx);
            if (f.isFile()) {
                in = new FileInputStream(f);
                p.load(new java.io.InputStreamReader(in, "UTF-8"));
            }
        } catch (Throwable t) {
            Bridge.log(Log.WARN, Const.TAG, "读取自动备份配置失败", t);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
        }

        final String rp = normalizeRoot(p.getProperty("root_path", DEFAULT_ROOT));
        rootPath = (rp == null) ? DEFAULT_ROOT : rp;
        keepAuto = (int) getLong(p, "keep_auto", 5);
        logEnabled = getBool(p, "log_enabled", true);
        rootConfigured = getBool(p, "root_configured", false);
        link1Name = p.getProperty("link1_name", link1Name);
        link1Url = p.getProperty("link1_url", link1Url);
        link2Name = p.getProperty("link2_name", link2Name);
        link2Url = p.getProperty("link2_url", link2Url);

        en7 = getBool(p, "enabled_7", true);        en30 = getBool(p, "enabled_30", true);
        en180 = getBool(p, "enabled_180", true);
        onShutdown = getBool(p, "on_shutdown", true);
        digestOnly = getBool(p, "digest_only", true);
        last7 = getLong(p, "last_7");
        last30 = getLong(p, "last_30");
        last180 = getLong(p, "last_180");
        lastAuto = getLong(p, "last_auto");
        lastDigest = p.getProperty("digest", "");

        // 首次启用：计时从此刻开始，不立刻备份（避免装完就悄悄写一份到 Download）
        final long now = System.currentTimeMillis();
        boolean dirty = false;

        // 保留份数默认值迁移：老配置里存的 0 视为「没设过」，一次性抬到 5；
        // 之后用户自己改成任何值（含 0）都会被尊重。
        if (!p.containsKey("keep_migrated")) {
            if (keepAuto < 5) {
                keepAuto = 5;
            }
            dirty = true;
        }

        if (en7 && last7 <= 0) {
            last7 = now;
            dirty = true;
        }
        if (en30 && last30 <= 0) {
            last30 = now;
            dirty = true;
        }
        if (en180 && last180 <= 0) {
            last180 = now;
            dirty = true;
        }
        if (dirty) {
            save(ctx);
        }
    }

    static synchronized void save(Context ctx) {
        final Properties p = new Properties();
        p.setProperty("root_path", rootPath);
        p.setProperty("keep_auto", String.valueOf(keepAuto));
        p.setProperty("keep_migrated", "true");
        p.setProperty("log_enabled", String.valueOf(logEnabled));
        p.setProperty("root_configured", String.valueOf(rootConfigured));
        p.setProperty("link1_name", link1Name == null ? "" : link1Name);
        p.setProperty("link1_url", link1Url == null ? "" : link1Url);
        p.setProperty("link2_name", link2Name == null ? "" : link2Name);
        p.setProperty("link2_url", link2Url == null ? "" : link2Url);
        p.setProperty("enabled_7", String.valueOf(en7));
        p.setProperty("enabled_30", String.valueOf(en30));
        p.setProperty("enabled_180", String.valueOf(en180));
        p.setProperty("on_shutdown", String.valueOf(onShutdown));
        p.setProperty("digest_only", String.valueOf(digestOnly));
        p.setProperty("last_7", String.valueOf(last7));
        p.setProperty("last_30", String.valueOf(last30));
        p.setProperty("last_180", String.valueOf(last180));
        p.setProperty("last_auto", String.valueOf(lastAuto));
        p.setProperty("digest", lastDigest == null ? "" : lastDigest);
        OutputStream out = null;
        try {
            out = new FileOutputStream(file(ctx));
            p.store(new java.io.OutputStreamWriter(out, "UTF-8"), "LauncherLayoutBackup config");
            out.flush();
        } catch (Throwable t) {
            Bridge.log(Log.ERROR, Const.TAG, "保存自动备份配置失败", t);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** 只覆盖传进来的项（null = 不改）。从"关"变"开"时，该档计时重置为现在。 */
    static synchronized void apply(Context ctx, Boolean b7, Boolean b30, Boolean b180,
                                   Boolean bShutdown, Boolean bDigest) {
        final long now = System.currentTimeMillis();
        if (b7 != null) {
            if (b7 && !en7) {
                last7 = now;
            }
            en7 = b7;
        }
        if (b30 != null) {
            if (b30 && !en30) {
                last30 = now;
            }
            en30 = b30;
        }
        if (b180 != null) {
            if (b180 && !en180) {
                last180 = now;
            }
            en180 = b180;
        }
        if (bShutdown != null) {
            onShutdown = bShutdown;
        }
        if (bDigest != null) {
            digestOnly = bDigest;
        }
        save(ctx);
        Bridge.log(Log.INFO, Const.TAG, "自动备份设置已更新：7天=" + en7 + " / 30天=" + en30
                + " / 180天=" + en180 + " / 关机前=" + onShutdown + " / 仅变化时=" + digestOnly);
    }

    /** 给模块 App 的配置快照，key=value 形式。 */
    static String[] snapshot(Context ctx) {
        String changed = "?";
        try {
            final String cur = BackupEngine.layoutDigest(ctx);
            changed = (cur == null) ? "未知" : String.valueOf(!cur.equals(lastDigest));
        } catch (Throwable ignored) {
        }
        int count = 0;
        boolean ghost = false;
        try {
            count = BackupEngine.listBackups().length;
            ghost = BackupEngine.ghostExists();
        } catch (Throwable ignored) {
        }
        return new String[]{
                "en7=" + en7,
                "en30=" + en30,
                "en180=" + en180,
                "onShutdown=" + onShutdown,
                "digestOnly=" + digestOnly,
                "last7=" + nextAt(last7, en7, P7),
                "last30=" + nextAt(last30, en30, P30),
                "last180=" + nextAt(last180, en180, P180),
                "lastAuto=" + lastAuto,
                "digestChanged=" + changed,
                "root=" + rootPath,
                "rootSet=" + (rootConfigured ? 1 : 0),
                "keepAuto=" + keepAuto,
                "logOn=" + (logEnabled ? 1 : 0),
                "count=" + count,
                "ghost=" + (ghost ? 1 : 0),
                "l1n=" + nz(link1Name),
                "l1u=" + nz(link1Url),
                "l2n=" + nz(link2Name),
                "l2u=" + nz(link2Url),
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ================================================================== 备份路径

    static File rootDir() {
        return new File(rootPath);
    }

    /**
     * 规范化备份路径，并强制限定在**手机内部存储**内。
     * 返回 null 表示非法（含 SD 卡 / U 盘 / OTG / 相对路径 / 越级）。
     */
    static String normalizeRoot(String input) {
        if (input == null) {
            return null;
        }
        String p = input.trim();
        if (p.isEmpty() || p.charAt(0) != '/' || p.contains("..")) {
            return null;
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        // /sdcard 与 /storage/self/primary 都是内置主存储的别名
        if (p.equals("/sdcard")) {
            p = "/storage/emulated/0";
        } else if (p.startsWith("/sdcard/")) {
            p = "/storage/emulated/0" + p.substring("/sdcard".length());
        } else if (p.equals("/storage/self/primary")) {
            p = "/storage/emulated/0";
        } else if (p.startsWith("/storage/self/primary/")) {
            p = "/storage/emulated/0" + p.substring("/storage/self/primary".length());
        }
        final int user = android.os.Process.myUid() / 100000;
        final String base = "/storage/emulated/" + user;
        if (!p.equals(base) && !p.startsWith(base + "/")) {
            return null;
        }
        return p;
    }

    /** 把规范化后的内置路径转成 DocumentsUI 的 docId（primary:xxx）。 */
    static String toDocId(String absPath) {
        final int user = android.os.Process.myUid() / 100000;
        final String base = "/storage/emulated/" + user;
        if (absPath == null) {
            return null;
        }
        if (absPath.equals(base)) {
            return "primary:";
        }
        if (absPath.startsWith(base + "/")) {
            return "primary:" + absPath.substring(base.length() + 1);
        }
        return null;
    }

    /** 设置备份路径：先做合法性 + 可写性验证，通过才落盘。 */
    static BackupEngine.Result setRoot(Context ctx, String input) {
        final String norm = normalizeRoot(input);
        if (norm == null) {
            return new BackupEngine.Result(false,
                    "只允许手机内部存储路径。\n"
                            + "正确示例：/storage/emulated/0/Download\n"
                            + "（SD 卡、U 盘 / OTG 一律不支持：这些介质会被拔掉，"
                            + "备份会在 App 里「消失」，反而更危险）", null, null);
        }
        final File d = new File(norm);
        if (!d.isDirectory() && !d.mkdirs()) {
            return new BackupEngine.Result(false, "目录建不出来：" + norm, null, null);
        }
        final File probe = new File(d, ".llb_write_test");
        try {
            FileOutputStream o = new FileOutputStream(probe);
            o.write('x');
            o.flush();
            o.close();
            if (!probe.delete()) {
                probe.deleteOnExit();
            }
        } catch (Throwable t) {
            return new BackupEngine.Result(false, "目录不可写：" + norm + "\n" + t, null, null);
        }
        rootPath = norm;
        rootConfigured = true;
        save(ctx);
        return new BackupEngine.Result(true, "备份路径已设为：\n" + norm, norm, null);
    }

    /** 设置保留策略与鸣谢链接（null = 不改）。 */
    static synchronized void applyExtra(Context ctx, Integer keep, Boolean logOn,
                                        String l1n, String l1u, String l2n, String l2u) {
        if (keep != null) {
            keepAuto = Math.max(0, keep.intValue());
        }
        if (logOn != null) {
            logEnabled = logOn.booleanValue();
        }
        if (l1n != null) {
            link1Name = l1n;
        }
        if (l1u != null) {
            link1Url = l1u;
        }
        if (l2n != null) {
            link2Name = l2n;
        }
        if (l2u != null) {
            link2Url = l2u;
        }
        save(ctx);
        Bridge.log(Log.INFO, Const.TAG, "扩展设置已更新：保留自动备份=" + keepAuto
                + " / 显示日志=" + logEnabled
                + " / 链接1=" + link1Name + " / 链接2=" + link2Name);
    }

    private static long nextAt(long last, boolean en, long period) {
        if (!en) {
            return -1L;
        }
        if (last <= 0) {
            return System.currentTimeMillis();
        }
        return last + period;
    }

    private static boolean getBool(Properties p, String k, boolean def) {
        final String v = p.getProperty(k);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    private static long getLong(Properties p, String k, long def) {
        try {
            return Long.parseLong(p.getProperty(k, String.valueOf(def)));
        } catch (Throwable t) {
            return def;
        }
    }

    private static long getLong(Properties p, String k) {
        return getLong(p, k, 0L);
    }
}
