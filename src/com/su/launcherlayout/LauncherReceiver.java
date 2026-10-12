package com.su.launcherlayout;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 注册在小米桌面进程里的接收器。
 * {@code onReceive} 跑在主线程，所有耗时操作都丢到工作线程。
 */
public final class LauncherReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        final String action = intent.getAction();
        if (action == null) {
            return;
        }

        final String token = intent.getStringExtra(Const.EX_TOKEN);
        final String dir = intent.getStringExtra(Const.EX_DIR);
        final String dbFile = intent.getStringExtra(Const.EX_DBFILE);

        final String op;
        if (Const.ACTION_BACKUP.equals(action)) {
            op = Const.OP_BACKUP;
        } else if (Const.ACTION_RESTORE.equals(action)) {
            op = Const.OP_RESTORE;
        } else if (Const.ACTION_LIST.equals(action)) {
            op = Const.OP_LIST;
        } else if (Const.ACTION_PING.equals(action)) {
            op = Const.OP_PING;
        } else if (Const.ACTION_RESTART.equals(action)) {
            op = Const.OP_RESTART;
        } else if (Const.ACTION_INFO.equals(action)) {
            op = Const.OP_INFO;
        } else if (Const.ACTION_FILES.equals(action)) {
            op = Const.OP_FILES;
        } else if (Const.ACTION_GET_CONFIG.equals(action)) {
            op = Const.OP_GET_CONFIG;
        } else if (Const.ACTION_SET_CONFIG.equals(action)) {
            op = Const.OP_SET_CONFIG;
        } else if (Const.ACTION_SET_ROOT.equals(action)) {
            op = Const.OP_SET_ROOT;
        } else if (Const.ACTION_SET_EXTRA.equals(action)) {
            op = Const.OP_SET_EXTRA;
        } else if (Const.ACTION_DELETE_ALL.equals(action)) {
            op = Const.OP_DELETE_ALL;
        } else if (Const.ACTION_DROP_GHOST.equals(action)) {
            op = Const.OP_DROP_GHOST;
        } else if (Const.ACTION_RECOVER_GHOST.equals(action)) {
            op = Const.OP_RECOVER_GHOST;
        } else {
            return;
        }

        // 自动备份开关（只覆盖请求里带了的项）
        final Boolean cfg7 = intent.hasExtra(Const.EX_CFG7)
                ? Boolean.valueOf(intent.getBooleanExtra(Const.EX_CFG7, true)) : null;
        final Boolean cfg30 = intent.hasExtra(Const.EX_CFG30)
                ? Boolean.valueOf(intent.getBooleanExtra(Const.EX_CFG30, true)) : null;
        final Boolean cfg180 = intent.hasExtra(Const.EX_CFG180)
                ? Boolean.valueOf(intent.getBooleanExtra(Const.EX_CFG180, true)) : null;
        final Boolean cfgShutdown = intent.hasExtra(Const.EX_CFG_SHUTDOWN)
                ? Boolean.valueOf(intent.getBooleanExtra(Const.EX_CFG_SHUTDOWN, true)) : null;
        final Boolean cfgDigest = intent.hasExtra(Const.EX_CFG_DIGEST)
                ? Boolean.valueOf(intent.getBooleanExtra(Const.EX_CFG_DIGEST, true)) : null;

        final Context app = Bridge.appContext() != null ? Bridge.appContext()
                : (context.getApplicationContext() != null ? context.getApplicationContext() : context);

        Bridge.log(Log.INFO, Const.TAG, "收到动作: " + op + (dir == null ? "" : (" dir=" + dir)));

        if (Const.OP_PING.equals(op)) {
            Bridge.reply(op, token, true, "模块已激活：桌面进程注入成功", null, null);
            return;
        }

        // 让桌面进程自杀，系统会自动把桌面重新拉起来。
        // 用途：① 模块刚在 LSPosed 里启用后让注入生效；② 恢复布局后重载数据库。
        if (Const.OP_RESTART.equals(op)) {
            Bridge.reply(op, token, true,
                    "桌面进程即将重启（屏幕会闪一下，属正常）\n重启后模块会自动重新注入", null, null);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Bridge.log(Log.WARN, Const.TAG, "主动重启桌面进程");
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            }, 700L);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (Const.OP_INFO.equals(op)) {
                        BackupEngine.Result r = BackupEngine.info(app);
                        Bridge.reply(op, token, r.ok, r.message, r.items, r.dir);
                        return;
                    }

                    if (Const.OP_LIST.equals(op)) {
                        String[] dirs = BackupEngine.listBackups();
                        String[] grids = BackupEngine.backupGrids(dirs);
                        Bridge.replyEx(op, token, true,
                                "共 " + dirs.length + " 个备份", dirs, grids, null);
                        return;
                    }

                    if (Const.OP_FILES.equals(op)) {
                        BackupEngine.Result r = BackupEngine.files(app, dir);
                        Bridge.reply(op, token, r.ok, r.message, r.items, dir);
                        return;
                    }

                    if (Const.OP_GET_CONFIG.equals(op)) {
                        Bridge.reply(op, token, true, "自动备份设置", Schedule.snapshot(app), null);
                        return;
                    }

                    if (Const.OP_SET_CONFIG.equals(op)) {
                        Schedule.apply(app, cfg7, cfg30, cfg180, cfgShutdown, cfgDigest);
                        Bridge.reply(op, token, true, "自动备份设置已更新", Schedule.snapshot(app), null);
                        return;
                    }

                    if (Const.OP_SET_ROOT.equals(op)) {
                        final String root = intent.getStringExtra(Const.EX_ROOT);
                        BackupEngine.Result r = Schedule.setRoot(app, root);
                        Bridge.reply(op, token, r.ok, r.message, Schedule.snapshot(app), r.dir);
                        return;
                    }

                    if (Const.OP_SET_EXTRA.equals(op)) {
                        final Integer keep = intent.hasExtra(Const.EX_KEEP_AUTO)
                                ? Integer.valueOf(intent.getIntExtra(Const.EX_KEEP_AUTO, 0)) : null;
                        final Boolean logOn = intent.hasExtra(Const.EX_LOG_ON)
                                ? Boolean.valueOf(intent.getBooleanExtra(Const.EX_LOG_ON, true)) : null;
                        Schedule.applyExtra(app, keep, logOn,
                                intent.hasExtra(Const.EX_LINK1_NAME) ? intent.getStringExtra(Const.EX_LINK1_NAME) : null,
                                intent.hasExtra(Const.EX_LINK1_URL) ? intent.getStringExtra(Const.EX_LINK1_URL) : null,
                                intent.hasExtra(Const.EX_LINK2_NAME) ? intent.getStringExtra(Const.EX_LINK2_NAME) : null,
                                intent.hasExtra(Const.EX_LINK2_URL) ? intent.getStringExtra(Const.EX_LINK2_URL) : null);
                        Bridge.reply(op, token, true, "设置已保存", Schedule.snapshot(app), null);
                        return;
                    }

                    if (Const.OP_DELETE_ALL.equals(op)) {
                        BackupEngine.Result r = BackupEngine.deleteAll(app);
                        Bridge.reply(op, token, r.ok, r.message, Schedule.snapshot(app), null);
                        return;
                    }

                    if (Const.OP_DROP_GHOST.equals(op)) {
                        final boolean had = BackupEngine.ghostExists();
                        BackupEngine.retireGhost();
                        Bridge.reply(op, token, true,
                                had ? "已丢弃删除前的安全备份" : "没有可丢弃的安全备份",
                                Schedule.snapshot(app), null);
                        return;
                    }

                    if (Const.OP_RECOVER_GHOST.equals(op)) {
                        BackupEngine.Result r = BackupEngine.revealGhost(app);
                        Bridge.reply(op, token, r.ok, r.message, Schedule.snapshot(app), r.dir);
                        return;
                    }

                    if (Const.OP_BACKUP.equals(op)) {
                        BackupEngine.Result r = BackupEngine.backup(app, "手动");
                        Bridge.reply(op, token, r.ok, r.message, null, r.dir);
                        return;
                    }

                    if (Const.OP_RESTORE.equals(op)) {
                        BackupEngine.Result r = BackupEngine.restore(app, dir, dbFile);
                        // 把「源文件 | 目标库 | 桌面 uid」打包回传给模块 App，
                        // 由 App 用 root 从进程外执行：先杀桌面 -> 覆盖库 -> 修属主/上下文 -> 拉起。
                        // 桌面自己做不到（platform_app 域不能 exec；进程内写库会被回写覆盖）。
                        String pack = r.ok
                                ? (BackupEngine.sRestoreSrc + "|" + BackupEngine.sRestoreDst
                                   + "|" + android.os.Process.myUid())
                                : null;
                        Bridge.reply(op, token, r.ok, r.message, null, pack);
                    }
                } catch (Throwable t) {
                    Bridge.log(Log.ERROR, Const.TAG, op + " 异常", t);
                    Bridge.reply(op, token, false, "失败：" + t, null, null);
                }
            }
        }, "llb-worker").start();
    }
}
