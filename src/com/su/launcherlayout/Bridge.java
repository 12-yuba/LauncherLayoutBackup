package com.su.launcherlayout;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * 跑在「小米桌面进程」里的桥。
 *
 * <p>只做两件事：注册动态广播接收器、把日志写进 LSPosed 日志。
 * 真正的文件读写交给 {@link BackupEngine}。
 */
final class Bridge {

    private static volatile boolean sInstalled = false;
    private static Context sAppCtx;
    private static XposedInterface sXposed;

    private Bridge() {
    }

    static synchronized void install(Context ctx, XposedInterface xposed) {
        if (sInstalled) {
            return;
        }
        sInstalled = true;

        Context app = ctx.getApplicationContext();
        sAppCtx = (app != null) ? app : ctx;
        sXposed = xposed;

        final BroadcastReceiver receiver = new LauncherReceiver();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Const.ACTION_BACKUP);
        filter.addAction(Const.ACTION_RESTORE);
        filter.addAction(Const.ACTION_LIST);
        filter.addAction(Const.ACTION_PING);
        filter.addAction(Const.ACTION_RESTART);
        filter.addAction(Const.ACTION_INFO);
        filter.addAction(Const.ACTION_FILES);
        filter.addAction(Const.ACTION_GET_CONFIG);
        filter.addAction(Const.ACTION_SET_CONFIG);
        filter.addAction(Const.ACTION_SET_ROOT);
        filter.addAction(Const.ACTION_SET_EXTRA);
        filter.addAction(Const.ACTION_DELETE_ALL);
        filter.addAction(Const.ACTION_DROP_GHOST);
        filter.addAction(Const.ACTION_RECOVER_GHOST);

        registerExported(sAppCtx, receiver, filter);
        log(Log.INFO, Const.TAG, "桥已就绪，广播接收器已注册（uid=" + android.os.Process.myUid() + "）");

        // 自动备份排程（定时 + 关机前）
        try {
            Schedule.start(sAppCtx);
        } catch (Throwable t) {
            log(Log.ERROR, Const.TAG, "启动自动备份排程失败", t);
        }
    }

    /** 供 Schedule 注册关机广播接收器复用。 */
    static void registerExportedReceiver(Context ctx, BroadcastReceiver r, IntentFilter f) {
        registerExported(ctx, r, f);
    }

    /**
     * Android 13(API 33) 起跨应用动态注册必须显式给 RECEIVER_EXPORTED 标志。
     * 本机编译用的 android.jar 较老，没有该常量，因此走反射并用文档值 0x2。
     */
    private static void registerExported(Context ctx, BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                Method m = Context.class.getMethod("registerReceiver",
                        BroadcastReceiver.class, IntentFilter.class, int.class);
                m.invoke(ctx, r, f, Integer.valueOf(2 /* Context.RECEIVER_EXPORTED */));
                return;
            } catch (Throwable t) {
                log(Log.WARN, Const.TAG, "registerReceiver(flags) 反射失败，回退两参重载: " + t);
            }
        }
        ctx.registerReceiver(r, f);
    }

    static Context appContext() {
        return sAppCtx;
    }

    static void log(int priority, String tag, String msg) {
        Log.println(priority, tag, msg);
        XposedInterface x = sXposed;
        if (x != null) {
            try {
                x.log(priority, tag, msg);
            } catch (Throwable ignored) {
            }
        }
    }

    static void log(int priority, String tag, String msg, Throwable tr) {
        Log.println(priority, tag, msg + ": " + tr);
        XposedInterface x = sXposed;
        if (x != null) {
            try {
                x.log(priority, tag, msg, tr);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 把结果回执给模块 App。 */
    static void reply(String op, String token, boolean ok, String msg, String[] items, String dir) {
        replyEx(op, token, ok, msg, items, null, dir);
    }

    /** 完整版：额外支持与 items 平行的网格标注数组。 */
    static void replyEx(String op, String token, boolean ok, String msg,
                        String[] items, String[] grids, String dir) {
        Context ctx = sAppCtx;
        if (ctx == null) {
            return;
        }
        try {
            Intent i = new Intent(Const.ACTION_RESULT);
            i.setPackage(Const.MODULE_PKG);
            i.putExtra(Const.EX_OP, op);
            i.putExtra(Const.EX_TOKEN, token);
            i.putExtra(Const.EX_OK, ok);
            i.putExtra(Const.EX_MSG, msg == null ? "" : msg);
            i.putExtra(Const.EX_DIR, dir == null ? "" : dir);
            if (items != null) {
                i.putExtra(Const.EX_ITEMS, items);
            }
            if (grids != null) {
                i.putExtra(Const.EX_GRIDS, grids);
            }
            ctx.sendBroadcast(i);
        } catch (Throwable t) {
            log(Log.ERROR, Const.TAG, "回执发送失败", t);
        }
    }
}
