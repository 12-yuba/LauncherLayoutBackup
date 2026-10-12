# 桌面布局备份 · LauncherLayoutBackup

> 小米 / HyperOS 桌面图标排版的一键备份与恢复。**只备份排版数据（几百 KB），不含任何 APK。**
>
> ⚠️ **前提：手机已取得 root（KernelSU / Magisk 等）并且安装了 LSPosed。**
> 本项目是 LSPosed 模块，**离开 root 无法使用**。
>
> 需要澄清的是：**模块 App 自己申请零权限，也不需要被授予 root**。
> 因为真正读写的代码跑在「系统桌面」自己的进程里，用的是桌面自己的身份；
> 所以不必像一般脚本方案那样，额外给某个 App 开 root 权限、再让它去 `cp` 文件。

**English**: An LSPosed module that backs up / restores the MIUI & HyperOS launcher layout
(icon positions, folders, pages) as a tiny snapshot (a few hundred KB, **no APKs included**).

**Requires root (KernelSU / Magisk) + LSPosed** — it is an LSPosed module and cannot work without them.
What it does *not* need: any permission for the module app itself, and no root grant for the app —
the actual file I/O runs inside the system launcher process, under the launcher's own identity.

- 作用域：`com.miui.home`（系统桌面）
- 模块入口：`io.github.libxposed.api.XposedModule` · **libxposed API 102**
- 语言：Java（无 androidx 依赖，UI 全部代码构建）

### ⬇ 下载

**永久直链（永远是最新版，收藏这个就行）**

```
https://github.com/12-yuba/LauncherLayoutBackup/releases/latest/download/LauncherLayoutBackup.apk
```

- 历史版本 / 更新日志：<https://github.com/12-yuba/LauncherLayoutBackup/releases>
- 该直链依赖于「每个 release 都上传一个名为 `LauncherLayoutBackup.apk` 的附件」这一约定，请勿改动附件名。

---

## 1. 它解决什么问题

小米桌面一旦崩溃或被清数据，所有图标就会挤成一堆，只能手动一个一个拖回去。

官方「备份与恢复」确实能存布局，但它会把**所有 APK 一起打包**，动辄几个 GB，平时根本不想用。

这个模块只搬**排版数据**：

| 备份内容 | 说明 |
|---|---|
| `launcher5x8.db`(+`-wal`/`-journal`) | 图标、文件夹、行列与跨屏坐标（`favorites` / `screens` 表） |
| `launcher5x8.db.snapshot` | `VACUUM INTO` 生成的一致性**单文件快照**，最稳的恢复来源 |
| `miui_widget.db` | 小部件库 |
| `app_status.db` / `app_category_new.db` | 桌面应用分类与状态 |
| `drawer/` / `elderlyMan/` | 抽屉分组等子目录 |
| `prefs/*.xml` | SharedPreferences（含网格行列数等配置） |
| `readable/favorites.csv` `screens.csv` `layout_map.txt` | 导成表格，人可直接看 |
| `README.txt` | 本次备份的完整说明（设备/网格/库名/恢复步骤/署名） |
| `MANIFEST.sha256` | 所有文件的 SHA-256 校验 |

一次备份通常 **300 KB 左右**，可以放心地自动备份。

---

## 2. 特性

- **一键备份 / 一键恢复**，恢复前自动把当前状态另存为回滚点
- **自动备份**：每 7 / 30 / 180 天三档独立周期，跑在桌面进程里，App 不用常驻
- **布局指纹**：只在排版真的变化时才自动备份，不堆一模一样的备份
- **保留策略**：自动备份只保留最近 N 份（默认 5），**手动备份永不自动删除**
- **隐藏安全备份**：一键删除全部备份前会偷偷留一份 `.llb_ghost`（点开头，默认不显示），
  随时可「找回」——只把名字前面的点去掉，让它变回一份正常备份
- **首次使用强制设定备份目录**，避免备份混在 Download 里被顺手清理掉
- **备份路径可自选**，但只允许手机内部存储（SD 卡 / U 盘拔掉后备份会「消失」，所以拒绝）
- **关机 / 重启前自动备份**（尽力而为，见 §7）
- **网格自适应**：自动识别 `launcher5xN.db`，标注每个备份的网格并提示与当前是否一致
- **恢复时可自由选择文件**：原始库或一致性快照，由你挑
- 界面仿 KernelSU / Miuix 观感，支持系统深浅色

---

## 3. 工作原理

### 3.1 为什么不用给模块 App 授权 root

模块通过 LSPosed 注入到**系统桌面自己的进程**里（这一步需要 root + LSPosed）。于是：

- 读自己的 `/data/user_de/0/com.miui.home/databases/` —— 天然合法，**不需要额外授权**
- 小米桌面自带 `MANAGE_EXTERNAL_STORAGE`（实测 `granted=true`），
  所以它能把备份**直接写到共享存储**

所以模块 App 本身**不申请任何权限**，只是个遥控器；
你也不需要像用 root 脚本那样，给某个 App 单独开 root 授权。

### 3.2 数据流

```
┌──────────────────────┐        广播 (setPackage=com.miui.home)        ┌───────────────────────────┐
│ 模块 App              │ ───────────────────────────────────────────► │ 系统桌面进程                │
│ MainActivity          │                                              │ LauncherReceiver          │
│  ├ PageHome           │ ◄─────────────────────────────────────────── │   ↓                       │
│  ├ PageBackup         │        回执 (setPackage=模块)                 │ BackupEngine              │
│  └ PageSettings       │                                              │   ↓ 直接读写自己的私有库     │
│ 零权限，只发广播        │                                              │ /sdcard/… 或自选目录        │
└──────────────────────┘                                              └───────────────────────────┘
                                                                              ▲
                                                              Schedule（定时排程 + 关机广播）
```

### 3.3 入口与 hook

```java
public final class ModuleEntry extends XposedModule {          // libxposed API 102
    @Override public void onPackageReady(PackageReadyParam p) {
        if (!"com.miui.home".equals(p.getPackageName())) { detach(); return; }
        hook(Application.class.getMethod("onCreate"))          // onPackageReady 不给 Context
            .setId("llb-application-oncreate")
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Object r = chain.proceed();
                Bridge.install((Context) chain.getThisObject(), this);
                return r;
            });
    }
}
```

`Bridge.install()` 里注册一个**动态广播接收器**，并启动自动备份排程。
之后每一次「备份 / 恢复 / 列举 / 配置」都是一个广播请求 + 一个回执。

### 3.4 恢复是怎么做的

1. 先把**当前状态**另存为一份回滚点（`恢复前自动`）
2. 把选定文件写回数据库目录：**临时文件 → fsync → rename 覆盖**，
   保留原 inode，从而保住属主与 SELinux 上下文
3. 以备份里的文件集合为准增删 `-wal` / `-journal`；`-shm` 一律不恢复
4. **在进程被 kill 前最后一步**再清一次 `-wal` / `-shm`（否则桌面可能重建 wal，
   重启后套到新库上，反而弄坏数据）
5. `Process.killProcess(myPid())` 让桌面进程重启，重新加载新库

### 3.5 布局指纹

只对**排版相关**的列做 SHA-256，**故意排除**：

- `launchCount`（使用次数，用户每点一次图标就变）
- `icon`（BLOB，体积大且可能被主题重绘）
- `itemFlags` / `sortMode` / `displayMode` 等易变列

否则每次都算成「变了」，自动备份会退化成无脑堆文件。

### 3.6 自动备份排程

跑在**桌面进程**里 —— 桌面是常驻进程、开机就活着，所以：

- 不需要 `AlarmManager`
- 不需要后台保活
- **不要求模块 App 在运行**

配置持久化在 `/data/user_de/0/com.miui.home/files/llb_schedule.properties`，
App 通过广播读写。检查间隔 1 小时。

---

## 4. 安装与启用

1. 安装 APK
   - 若提示 `INSTALL_FAILED_USER_RESTRICTED`，说明 HyperOS 关掉了 USB 安装：
     开发者选项里打开「**通过 USB 安装**」；或
     `adb push x.apk /data/local/tmp/ && adb shell su -c 'pm install -r /data/local/tmp/x.apk'`
2. **LSPosed 管理器 → 模块 → 桌面布局备份 → 打开开关**
3. 作用域只需勾 **系统桌面（com.miui.home）**，不要勾系统框架
4. 重启桌面进程（模块里有按钮），或重启手机
5. 打开 App：**首次会强制要求你指定一个备份目录**（不设好进不去）

> 免 root 的前提是**已经装了 LSPosed**。本模块自己不需要 root 权限。

---

## 5. 怎么编译

无需 Android Studio / Gradle，`build.sh` 直接用 `aapt2 + javac + d8 + jarsigner` 出包：

```bash
bash build.sh
```

依赖：

| 工具 | 说明 |
|---|---|
| JDK 17+ | 提供 `javac` / `keytool` / `jarsigner` |
| `android.jar` | 编译用（minSdk 26 就够） |
| `aapt2` | 编译资源 + 生成 `R.java` |
| `r8.jar` | 内含 `D8`，把 class 转成 dex |
| `io.github.libxposed:api:102.0.0` | 从 Maven Central 取；**注意它的 `.jar` 是空壳，真正的类在 `.aar` 的 `classes.jar` 里** |

链路：`aapt2 compile → aapt2 link(+--java R.java) → javac → d8 → 组装 APK → jarsigner(v1)`

`build.sh` 的依赖路径全部可用环境变量覆盖（`JDK_HOME` / `ANDROID_JAR` / `AAPT2_BIN` / `R8_JAR` /
`LIBS_DIR` / `KEYSTORE_FILE` / `KEYSTORE_PASS` / `KEYSTORE_ALIAS`），
`libxposed` 的 aar 若本地没有会自动从 Maven Central 下载 —— **所以从干净检出也能直接构建**。

### 在 GitHub 上自动构建

`.github/workflows/build.yml` 已经配好：推 `v*` tag 或手动触发 `workflow_dispatch`，
就会在 GitHub 的机器上编译，并把 APK 自动附加到对应的 Release
（附件名固定 `LauncherLayoutBackup.apk`，保证永久直链有效）。

需要在仓库 **Settings → Secrets and variables → Actions** 里加三个 Secret：

| Secret | 怎么生成 / 填什么 |
|---|---|
| `KEYSTORE_B64` | `base64 -w0 llb.jks`（Windows 用 `certutil -encode` 或 Git Bash 的 `base64`） |
| `KEYSTORE_PASS` | 密钥库口令 |
| `KEYSTORE_ALIAS` | 密钥别名（如 `llb`） |

> 用别的密钥签出来的包**签名不一致，老用户无法覆盖安装**（只能卸载重装，而卸载会清掉
> LSPosed 的启用状态与作用域）。所以工作流在缺少 `KEYSTORE_B64` 时**直接失败**，不降级成临时签名。

> 本项目用 **v1 签名 + `targetSdkVersion=29`**：因为 Android 11+ 只对 `targetSdk >= 30` 强制 v2/v3，
> 29 走 v1 可正常安装（已实测）。副作用是设置里可能显示"专为旧版 Android 打造"。

---

## 6. 目录结构

```
lsposed-launcher-backup/
├── AndroidManifest.xml                  # 无任何 xposed* meta-data
├── build.sh                             # 一键编译
├── res/
│   ├── drawable/ic_*.xml                # 手写 VectorDrawable（不依赖任何 UI 库）
│   ├── mipmap-xxhdpi/ic_launcher.png
│   ├── values/styles.xml                # NoActionBar + 浅色
│   └── values-night/styles.xml          # 深色
├── resources/META-INF/xposed/           # ★ 新版 API 的模块声明
│   ├── java_init.list                   #   入口类
│   ├── module.prop                      #   minApiVersion/targetApiVersion/staticScope
│   └── scope.list                       #   com.miui.home
└── src/com/su/launcherlayout/
    ├── ModuleEntry.java                 # 入口，extends XposedModule
    ├── Bridge.java                      # 在桌面进程里注册动态广播接收器
    ├── LauncherReceiver.java            # 处理所有请求
    ├── BackupEngine.java                # 备份/恢复/CSV导出/校验和/布局指纹/删除/安全备份
    ├── Schedule.java                    # 定时排程、关机广播、配置持久化
    ├── Theme.java / UiKit.java / UiLog.java / BaseActivity.java / Page.java
    ├── MainActivity.java                # 单 Activity + 底部导航
    ├── PageHome.java / PageBackup.java / PageSettings.java
    └── Const.java
```

约 5000 行 Java，**零第三方依赖**（连 androidx 都没用）。

### 与旧版 Xposed API 的差异

| 项 | 旧版 | 本项目（API 102） |
|---|---|---|
| 入口注册 | `assets/xposed_init` | `META-INF/xposed/java_init.list` |
| 模块元数据 | 4 个 `xposed*` meta-data | `META-INF/xposed/module.prop` |
| 作用域 | `xposedscope` 资源 | `META-INF/xposed/scope.list` |
| 基类 | `IXposedHookLoadPackage` | `extends XposedModule` |
| Hook | `XposedHelpers.findAndHookMethod` | `hook(Executable).setId().setPriority().setExceptionMode().intercept(chain -> …)` |

> `targetApiVersion >= 102` 时，classloader 层就禁止访问旧 API，
> 所以 `XposedHelpers` / `XSharedPreferences` / `XposedBridge` 全部不可用，本项目自带反射工具。

---

## 7. 已知边界（请先读）

- **关机前备份是尽力而为**。`ACTION_SHUTDOWN` 是系统保护广播，
  manifest 静态注册在 Android 8+ 收不到，动态注册能收到（本项目就是这么做的）；
  但**非正常断电**（长按电源强制重启、拔电、内核 panic、`reboot -f`）**根本不发广播**，必然漏。
  另外关机本身并不会弄乱桌面，所以它只是个"顺手多一层"的保险，不需要可以关掉。
- **网格必须一致**。库名与网格绑定（`launcher5x9.db` = 5 列 9 行），
  换网格会各自建库、旧库不会被删，所以两个网格能来回切。
  恢复时若网格不匹配，MIUI 会按当前网格裁剪越界图标 —— 所以界面上会红字提醒先改网格。
- **云同步可能覆盖**：小米云服务在联网时可能回写布局，恢复后建议关掉或立刻反向同步一次。
- **备份路径只允许内置存储**。外置 SD / U 盘一旦拔掉，备份就在 App 里"消失"了，风险更大。
- **修改包名 / 签名后 LSPosed 状态会重置**。换签名只能卸载重装，会清掉启用状态与作用域。
- 编译用的是 API 26～32 区间的公共 `android.jar`，因此
  `Context.RECEIVER_EXPORTED`（API 33+）只能反射调用，`Context.getUserId()` 是 `@hide` 需要绕开。

---

## 8. 隐私

- 模块 App **不申请任何权限**，不联网，无统计，无广告
- 备份文件**只写在你指定的目录**，不上传任何地方
- 权限清单为空 —— 可以在系统设置里自己核对

> 前提说明：本模块以 root + LSPosed 为运行前提；上面说的「零权限」指的是
> **模块 App 自身不申请权限、也不需要被单独授予 root**，
> 而不是说可以在没 root 的手机上使用。

---

## 9. 致谢

- **思路与备份格式**：酷安 [@瞑望](https://www.coolapk.com/u/1175257)
- **模块作者**：酷安 [@桃酥打水](https://www.coolapk.com/u/4111144)
- 界面观感参考 [KernelSU](https://github.com/tiann/KernelSU)（GPL-3.0）的 Miuix / Material 3 设计语言，
  **没有使用其任何代码**

> 这些署名也会写进每个备份目录的 `README.txt`，方便备份文件脱离本 App 流传时保留出处。

## 10. License

MIT —— 见 [LICENSE](LICENSE)。
