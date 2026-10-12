package com.su.launcherlayout;

/** 全局常量。 */
public final class Const {

    private Const() {
    }

    public static final String TAG = "LLB";

    /** 被 hook 的目标：小米桌面 / HyperOS 桌面。 */
    public static final String TARGET_PKG = "com.miui.home";

    /** 本模块 App 包名。 */
    public static final String MODULE_PKG = "com.su.launcherlayout";

    /** 备份写入的默认目录（小米桌面持有 MANAGE_EXTERNAL_STORAGE，可直接写）。可在设置里改。 */
    public static final String PUBLIC_ROOT = "/sdcard/Download";

    /** 备份目录前缀，形如 LauncherLayoutBackup-20261012_060342。 */
    public static final String BACKUP_PREFIX = "LauncherLayoutBackup-";

    /** 模块 App -> 桌面进程。 */
    public static final String ACTION_BACKUP = "com.su.launcherlayout.action.BACKUP";
    public static final String ACTION_RESTORE = "com.su.launcherlayout.action.RESTORE";
    public static final String ACTION_LIST = "com.su.launcherlayout.action.LIST";
    public static final String ACTION_PING = "com.su.launcherlayout.action.PING";
    /** 让桌面进程自杀，由系统自动重新拉起（模块注入生效 / 恢复布局后重载都用它）。 */
    public static final String ACTION_RESTART = "com.su.launcherlayout.action.RESTART";
    /** 查询当前网格 / 当前库名 / 库目录。 */
    public static final String ACTION_INFO = "com.su.launcherlayout.action.INFO";
    /** 列出某个备份目录里所有可恢复的库文件（供用户自由选择）。 */
    public static final String ACTION_FILES = "com.su.launcherlayout.action.FILES";
    /** 读取自动备份设置。 */
    public static final String ACTION_GET_CONFIG = "com.su.launcherlayout.action.GET_CONFIG";
    /** 写入自动备份设置。 */
    public static final String ACTION_SET_CONFIG = "com.su.launcherlayout.action.SET_CONFIG";
    /** 设置备份根目录。 */
    public static final String ACTION_SET_ROOT = "com.su.launcherlayout.action.SET_ROOT";
    /** 设置保留策略 / 鸣谢链接等扩展项。 */
    public static final String ACTION_SET_EXTRA = "com.su.launcherlayout.action.SET_EXTRA";
    /** 一键删除全部备份。 */
    public static final String ACTION_DELETE_ALL = "com.su.launcherlayout.action.DELETE_ALL";
    /** 丢弃隐藏安全备份。 */
    public static final String ACTION_DROP_GHOST = "com.su.launcherlayout.action.DROP_GHOST";
    /** 找回隐藏安全备份（去掉名字前面的点，转成一份正常可见的备份）。 */
    public static final String ACTION_RECOVER_GHOST = "com.su.launcherlayout.action.RECOVER_GHOST";

    /** 桌面进程 -> 模块 App 的回执。 */
    public static final String ACTION_RESULT = "com.su.launcherlayout.action.RESULT";

    public static final String EX_TOKEN = "token";
    public static final String EX_OP = "op";
    public static final String EX_OK = "ok";
    public static final String EX_MSG = "msg";
    public static final String EX_DIR = "dir";
    public static final String EX_ITEMS = "items";
    /** 与 items 平行的网格标注（如 "5×8"），仅备份列表用。 */
    public static final String EX_GRIDS = "grids";
    /** 恢复时用户选定的具体库文件名。 */
    public static final String EX_DBFILE = "dbfile";

    public static final String EX_CFG7 = "cfg7";
    public static final String EX_CFG30 = "cfg30";
    public static final String EX_CFG180 = "cfg180";
    public static final String EX_CFG_SHUTDOWN = "cfgShutdown";
    public static final String EX_CFG_DIGEST = "cfgDigest";
    /** 备份根目录。 */
    public static final String EX_ROOT = "root";
    /** 自动备份最多保留份数（0 = 不限制）。 */
    public static final String EX_KEEP_AUTO = "keepAuto";
    /** 是否显示操作日志面板。 */
    public static final String EX_LOG_ON = "logOn";
    public static final String EX_LINK1_NAME = "l1n";
    public static final String EX_LINK1_URL = "l1u";
    public static final String EX_LINK2_NAME = "l2n";
    public static final String EX_LINK2_URL = "l2u";

    public static final String OP_PING = "ping";
    public static final String OP_BACKUP = "backup";
    public static final String OP_RESTORE = "restore";
    public static final String OP_LIST = "list";
    public static final String OP_RESTART = "restart";
    public static final String OP_INFO = "info";
    public static final String OP_FILES = "files";
    public static final String OP_GET_CONFIG = "getconfig";
    public static final String OP_SET_CONFIG = "setconfig";
    public static final String OP_SET_ROOT = "setroot";
    public static final String OP_SET_EXTRA = "setextra";
    public static final String OP_DELETE_ALL = "deleteall";
    public static final String OP_DROP_GHOST = "dropghost";
    public static final String OP_RECOVER_GHOST = "recoverghost";
}
