package com.su.launcherlayout;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/**
 * 模块入口。
 *
 * <p>严格按 libxposed API 102：
 * <ul>
 *   <li>继承 {@link XposedModule}，必须有 public 无参构造函数；</li>
 *   <li>通过 {@code META-INF/xposed/java_init.list} 注册；</li>
 *   <li>框架自动调用 {@code attachFramework}，模块不得自行调用，也不得在
 *       {@link #onModuleLoaded} 之前做初始化；</li>
 *   <li>不使用任何 legacy {@code de.robv.android.xposed} API（targetApiVersion&gt;=102 会被
 *       classloader 层直接禁止）。</li>
 * </ul>
 */
public final class ModuleEntry extends XposedModule {

    private String mProcessName = "";
    private boolean mHooked = false;

    public ModuleEntry() {
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        mProcessName = param.getProcessName();
        log(Log.INFO, Const.TAG, "onModuleLoaded: process=" + mProcessName
                + ", isSystemServer=" + param.isSystemServer()
                + ", apiVersion=" + getApiVersion()
                + ", framework=" + getFrameworkName() + " " + getFrameworkVersion()
                + " (" + getFrameworkVersionCode() + ")");
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        final String pkg = param.getPackageName();
        Log.i(Const.TAG, "onPackageReady: " + pkg + " first=" + param.isFirstPackage());

        // 只在「小米桌面的主进程」里干活，其余一律断开后续回调。
        if (!Const.TARGET_PKG.equals(pkg) || !Const.TARGET_PKG.equals(mProcessName)) {
            detach();
            return;
        }
        try {
            installHook();
        } catch (Throwable t) {
            log(Log.ERROR, Const.TAG, "installHook 失败", t);
        }
    }

    /**
     * hook {@code Application#onCreate()}，拿到 Context 后注册动态广播接收器。
     * （新版 API 的 onPackageReady 只给 ClassLoader，不给 Context，所以必须从
     * Application 生命周期里取。）
     */
    private void installHook() throws Exception {
        if (mHooked) {
            return;
        }
        mHooked = true;

        final Method onCreate = Application.class.getMethod("onCreate");
        hook(onCreate)
                .setId("llb-application-oncreate")
                .setPriority(PRIORITY_DEFAULT)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        Object self = chain.getThisObject();
                        if (self instanceof Context) {
                            Bridge.install((Context) self, ModuleEntry.this);
                        }
                    } catch (Throwable t) {
                        log(Log.ERROR, Const.TAG, "Bridge.install 失败", t);
                    }
                    return result;
                });

        log(Log.INFO, Const.TAG, "已 hook Application.onCreate in " + Const.TARGET_PKG);
    }
}
