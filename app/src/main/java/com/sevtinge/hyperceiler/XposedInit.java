/*
 * This file is part of HyperCeiler.

 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2025 HyperCeiler Contributions
 */
package com.sevtinge.hyperceiler;

import static com.sevtinge.hyperceiler.utils.Helpers.getPackageVersionCode;
import static com.sevtinge.hyperceiler.utils.Helpers.getPackageVersionName;
import static com.sevtinge.hyperceiler.utils.devicesdk.MiDeviceAppUtilsKt.isPad;
import static com.sevtinge.hyperceiler.utils.devicesdk.SystemSDKKt.getAndroidVersion;
import static com.sevtinge.hyperceiler.utils.devicesdk.SystemSDKKt.getHyperOSVersion;
import static com.sevtinge.hyperceiler.utils.devicesdk.SystemSDKKt.getMiuiVersion;
import static com.sevtinge.hyperceiler.utils.devicesdk.SystemSDKKt.isAndroidVersion;
import static com.sevtinge.hyperceiler.utils.devicesdk.SystemSDKKt.isHyperOSVersion;
import static com.sevtinge.hyperceiler.utils.devicesdk.SystemSDKKt.isMiuiVersion;
import static com.sevtinge.hyperceiler.utils.log.LogManager.logLevelDesc;
import static com.sevtinge.hyperceiler.utils.log.XposedLogUtils.logE;
import static com.sevtinge.hyperceiler.utils.log.XposedLogUtils.logI;
import static com.sevtinge.hyperceiler.utils.prefs.PrefsUtils.mPrefsMap;

import android.os.Process;

import com.github.kyuubiran.ezxhelper.EzXHelper;
import com.hchen.hooktool.HCInit;
import com.sevtinge.hyperceiler.module.app.VariousThirdApps;
import com.sevtinge.hyperceiler.module.base.BaseModule;
import com.sevtinge.hyperceiler.module.base.DataBase;
import com.sevtinge.hyperceiler.module.base.tool.ResourcesTool;
import com.sevtinge.hyperceiler.module.hook.systemframework.AllowManageAllNotifications;
import com.sevtinge.hyperceiler.module.hook.systemframework.AllowUninstall;
import com.sevtinge.hyperceiler.module.hook.systemframework.BackgroundBlurDrawable;
import com.sevtinge.hyperceiler.module.hook.systemframework.CleanOpenMenu;
import com.sevtinge.hyperceiler.module.hook.systemframework.CleanShareMenu;
import com.sevtinge.hyperceiler.module.hook.systemframework.ScreenRotation;
import com.sevtinge.hyperceiler.module.hook.systemframework.ToastBlur;
import com.sevtinge.hyperceiler.module.hook.systemframework.UnlockAlwaysOnDisplay;
import com.sevtinge.hyperceiler.module.hook.systemframework.network.FlightModeHotSpot;
import com.sevtinge.hyperceiler.module.hook.systemsettings.VolumeSeparateControlForSettings;
import com.sevtinge.hyperceiler.module.skip.SystemFrameworkForCorePatch;
import com.sevtinge.hyperceiler.safe.CrashHook;
import com.sevtinge.hyperceiler.utils.api.ProjectApi;
import com.sevtinge.hyperceiler.utils.log.LogManager;
import com.sevtinge.hyperceiler.utils.prefs.PrefsUtils;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedInit implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    private static final String TAG = "HyperCeiler";
    public static boolean isSafeModeOn = false;
    public static String mModulePath = null;
    public static ResourcesTool mResHook;

    // public static XmlTool mXmlTool;

    // 用于处理第三方应用 Hook 的工具实例
    public final VariousThirdApps mVariousThirdApps = new VariousThirdApps();

    // 实现 Zygote 初始化的回调方法
    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        // 初始化 ResourcesTool，用于 Hook 系统资源
        mResHook = new ResourcesTool(startupParam.modulePath);
        // 保存模块路径，供其他部分使用
        mModulePath = startupParam.modulePath;
        // mXmlTool = new XmlTool(startupParam);

        // 初始化 XSharedPrefs，用于存储和读取模块的偏好设置
        setXSharedPrefs();

        // 初始化 EzXHelper，便于辅助 Hook 操作
        EzXHelper.initZygote(startupParam);
        EzXHelper.setLogTag(TAG);
        EzXHelper.setToastTag(TAG);

        // 初始化 HyperCeiler（HC）模块的基础数据
        HCInit.initBasicData(new HCInit.BasicData()
                .setModulePackageName(BuildConfig.APPLICATION_ID)// 模块包名
                .setLogLevel(LogManager.getLogLevel())// 日志等级
                .setTag("HyperCeiler")
        );

        // 初始化启动参数，用于后续操作
        HCInit.initStartupParam(startupParam);

        // 根据用户偏好设置，启用一些特定功能的资源 Hook
        if (mPrefsMap.getBoolean("system_framework_screen_all_rotations")) {
            ScreenRotation.initRes();// 启用屏幕旋转功能
        }
        if (mPrefsMap.getBoolean("system_framework_clean_share_menu")) {
            CleanShareMenu.initRes();// 清理分享菜单
        }
        if (mPrefsMap.getBoolean("system_framework_clean_open_menu")) {
            CleanOpenMenu.initRes();// 清理打开方式菜单
        }
        if (mPrefsMap.getBoolean("system_framework_volume_separate_control")) {
            VolumeSeparateControlForSettings.initRes();// 启用音量分离控制
        }

        // 检查 startupParam 是否为空，确保初始化安全
        if (startupParam != null) {
            // 初始化背景模糊效果
            new BackgroundBlurDrawable().initZygote(startupParam);

            // 初始化系统框架核心补丁
            new SystemFrameworkForCorePatch().initZygote(startupParam);

            // 根据用户偏好设置，初始化一些系统功能模块的 Hook
            if (mPrefsMap.getBoolean("system_framework_allow_uninstall")) {
                new AllowUninstall().initZygote(startupParam); // 允许卸载系统应用
            }
            if (mPrefsMap.getBoolean("system_framework_allow_manage_all_notifications")) {
                new AllowManageAllNotifications().initZygote(startupParam); // 允许管理所有通知
            }
            if (mPrefsMap.getBoolean("system_framework_background_blur_toast")) {
                new ToastBlur().initZygote(startupParam); // 启用 Toast 背景模糊
            }
            if (mPrefsMap.getBoolean("aod_unlock_always_on_display_hyper")) {
                new UnlockAlwaysOnDisplay().initZygote(startupParam); // 启用息屏显示解锁
            }
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 如果当前正在安全模式中，则直接返回，不执行任何 Hook。
        if (isInSafeMode(lpparam.packageName)) return;

        // 初始化 EzXHelper，用于简化 Hook 操作，传入当前加载的包信息。
        EzXHelper.initHandleLoadPackage(lpparam);

        EzXHelper.setLogTag(TAG);
        EzXHelper.setToastTag(TAG);

        // 注入Framework
        new SystemFrameworkForCorePatch().handleLoadPackage(lpparam);

        // 注入APP
        init(lpparam);

        if (mPrefsMap.getBoolean("system_framework_network_flightmode_hotspot"))
            new FlightModeHotSpot().handleLoadPackage(lpparam);// 允许飞行模式下开启热点
    }

    /**
     * 读取本地偏好设置
     * 优先读取 hyperceiler_prefs
     * 次之读取 /data/user_de/0/ BuildConfig.APPLICATION_ID /shared_prefs/hyperceiler_prefs.xml
     * 都失败就写错误日志
     */
    private void setXSharedPrefs() {
        // 如果偏好设置的 Map 是空的，则尝试读取配置文件。
        if (mPrefsMap.isEmpty()) {
            XSharedPreferences mXSharedPreferences;
            try {
                // 尝试读取模块包下的 SharedPreferences 文件。
                mXSharedPreferences = new XSharedPreferences(ProjectApi.mAppModulePkg, PrefsUtils.mPrefsName);
                mXSharedPreferences.makeWorldReadable(); // 设置文件为全局可读。
                Map<String, ?> allPrefs = mXSharedPreferences.getAll(); // 获取所有偏好设置。

                // 如果读取成功，将偏好设置存入 mPrefsMap。
                if (allPrefs != null && !allPrefs.isEmpty()) {
                    mPrefsMap.putAll(allPrefs);
                } else {
                    // 如果读取失败，尝试读取备用文件。
                    mXSharedPreferences = new XSharedPreferences(new File(PrefsUtils.mPrefsFile));
                    mXSharedPreferences.makeWorldReadable();
                    allPrefs = mXSharedPreferences.getAll();

                    // 如果读取成功，将偏好设置存入 mPrefsMap。
                    if (allPrefs != null && !allPrefs.isEmpty()) {
                        mPrefsMap.putAll(allPrefs);
                    } else {
                        // 如果仍然读取失败，记录错误日志。
                        logE("[UID" + Process.myUid() + "]", "Cannot read SharedPreferences, some mods might not work!");
                    }
                }
            } catch (Throwable t) {
                // 捕获所有异常并记录错误日志。
                logE("setXSharedPrefs", t);
            }
        }
    }

    /**
     * 初始化
     */
    private void init(XC_LoadPackage.LoadPackageParam lpparam) {
        // 获取当前正在加载的包名
        String packageName = lpparam.packageName;

        // 如果当前加载的是系统包 android 记录系统版本信息
        if (Objects.equals(packageName, "android"))
            logI(packageName, "androidVersion = " + getAndroidVersion() + ", miuiVersion = " + getMiuiVersion() + ", hyperosVersion = " + getHyperOSVersion());
        else
            // 如果是其他应用，记录其版本信息
            logI(packageName, "versionName = " + getPackageVersionName(lpparam) + ", versionCode = " + getPackageVersionCode(lpparam));

        // 调用初始化方法，设置模块 Hook
        invokeInit(lpparam);

        // 添加针对 Android 崩溃事件的 Hook
        androidCrashEventHook(lpparam);
    }

    /**
     * 初始化hook
     */
    private void invokeInit(XC_LoadPackage.LoadPackageParam lpparam) {
        // 获取当前加载的包名
        String mPkgName = lpparam.packageName;

        // 如果包名为空，直接返回
        if (mPkgName == null) return;

        // 如果当前加载的包是模块自身，则调用模块激活逻辑
        if (ProjectApi.mAppModulePkg.equals(mPkgName)) {
            moduleActiveHook(lpparam);
            return;
        }

        // 检查是否属于其他受限制的包名（如 WebView 等特殊系统组件），如果是则直接返回
        if (isOtherRestrictions(mPkgName)) return;

        // 获取模块的所有目标数据
        HashMap<String, DataBase> dataMap = DataBase.get();

        // 如果目标数据中没有当前包的 Hook 逻辑，则初始化通用的第三方应用 Hook
        if (dataMap.values().stream().noneMatch(dataBase -> dataBase.mTargetPackage.equals(mPkgName))) {
            mVariousThirdApps.init(lpparam);
            return;
        }

        // 遍历数据 Map，并对符合条件的包加载对应的 Hook 类
        dataMap.forEach(new BiConsumer<String, DataBase>() {
            @Override
            public void accept(String s, DataBase dataBase) {
                // 包名不匹配，跳过
                if (!mPkgName.equals(dataBase.mTargetPackage)) return;

                // SDK版本不匹配，跳过
                if (!(dataBase.mTargetSdk == -1) && !isAndroidVersion(dataBase.mTargetSdk)) return;

                // OS 版本不匹配，跳过
                if (!(dataBase.mTargetOSVersion == -1F) &&
                        !(isHyperOSVersion(dataBase.mTargetOSVersion) || isMiuiVersion(dataBase.mTargetOSVersion))) return;

                // 设备类型（平板/手机）不匹配，跳过
                if ((dataBase.isPad == 1 && !isPad()) || (dataBase.isPad == 2 && isPad())) return;

                // 动态加载并初始化对应的 Hook 模块
                try {
                    Class<?> clazz = getClass().getClassLoader().loadClass(s); // 加载类
                    BaseModule module = (BaseModule) clazz.getDeclaredConstructor().newInstance(); // 实例化模块
                    module.init(lpparam); // 初始化模块
                } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                         InstantiationException | InvocationTargetException | NullPointerException e) {
                    // 如果加载模块失败，记录错误日志
                    logE(TAG, e);
                }
            }
        });
    }

    /**
     * 捕获系统库报错
     */
    private void androidCrashEventHook(XC_LoadPackage.LoadPackageParam lpparam) {
        // 如果当前加载的包是系统 android 包
        if ("android".equals(lpparam.packageName)) {
            // 记录日志等级信息
            XposedBridge.log("[HyperCeiler][I]: Log level is " + logLevelDesc());

            // 尝试加载崩溃事件 Hook
            try {
                new CrashHook(lpparam);
            } catch (Exception e) {
                // 如果加载失败，记录错误日志
                logE(TAG, e);
            }
        }
    }

    /**
     * 指定的包是否启动了安全模式
     */
    private boolean isInSafeMode(String pkg) {
        switch (pkg) {
            case "com.android.systemui" -> {
                return isSystemUIModuleEnable();
            }
            case "com.miui.home" -> {
                return isHomeModuleEnable();
            }
            case "com.miui.securitycenter" -> {
                return isSecurityCenterModuleEnable();
            }
        }
        return false;
    }

    /**
     * 当前包是否应该被跳过
     */
    private boolean isOtherRestrictions(String pkg) {
        switch (pkg) {
            case "com.google.android.webview", "com.miui.contentcatcher", "com.miui.catcherpatch" -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * 激活模块自身
     */
    public void moduleActiveHook(XC_LoadPackage.LoadPackageParam lpparam) {
        // 查找模块自身的 "utils.Helpers" 类
        Class<?> mHelpers = XposedHelpers.findClassIfExists(ProjectApi.mAppModulePkg + ".utils.Helpers", lpparam.classLoader);

        // 设置模块激活标志为 true
        XposedHelpers.setStaticBooleanField(mHelpers, "isModuleActive", true);

        // 设置模块的 Xposed 版本号
        XposedHelpers.setStaticIntField(mHelpers, "XposedVersion", XposedBridge.getXposedVersion());

        // 记录日志，确认模块已激活
        XposedBridge.log("[HyperCeiler][I]: Log level is " + logLevelDesc());
    }


    /**
     * 指定目标是否启动了安全模式
     */
    private boolean isSafeModeEnable(String key) {
        return mPrefsMap.getBoolean(key);
    }

    /**
     * [系统界面] 是否启动了安全模式
     */
    private boolean isSystemUIModuleEnable() {
        return isSafeModeEnable("system_ui_safe_mode_enable");
    }

    /**
     * [系统桌面] 是否启动了安全模式
     */
    private boolean isHomeModuleEnable() {
        return isSafeModeEnable("home_safe_mode_enable");
    }

    /**
     * [安全中心] 是否启动了安全模式
     */
    private boolean isSecurityCenterModuleEnable() {
        return isSafeModeEnable("security_center_safe_mode_enable");
    }

}
