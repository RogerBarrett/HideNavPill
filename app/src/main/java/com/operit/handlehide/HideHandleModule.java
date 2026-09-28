package com.operit.handlehide;

import android.graphics.Canvas;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hide the gesture-navigation "pill" / home handle on Android 17 (PixelOS).
 *
 * ROOT CAUSE (v3, corrected after dynamic evidence collection):
 *   On this device the navigation bar window is NOT owned by SystemUI.
 *   `dumpsys window displays` shows mNavigationBar pointing at the window owned
 *   by the Launcher process (com.google.android.apps.nexuslauncher, uid 10196),
 *   whose WindowRootImpl surface is named "VRI-Taskbar". The real handle is
 *   drawn by the Launcher's Quickstep/Taskbar code:
 *
 *     com.android.launcher3.taskbar.StashedHandleViewController
 *         implements com.android.quickstep.NavHandle
 *     com.android.launcher3.taskbar.StashedHandleView extends android.view.View
 *
 *   StashedHandleView has NO onDraw of its own; it paints the small rounded bar
 *   purely through its background (View.setBackgroundColor / background drawable),
 *   as seen in StashedHandleView.updateHandleColor() which calls
 *   setBackgroundColor() / ObjectAnimator on VIEW_BACKGROUND_COLOR.
 *
 *   That is why hooking SystemUI's NavigationHandle / QuickswitchOrientedNavHandle
 *   logged fine (the classes exist) but did nothing: those classes are never
 *   instantiated on this build.
 *
 * STRATEGY (minimal, zero side effects):
 *   Hook StashedHandleView.draw(Canvas) and make it a no-op -> the bar is never
 *   rasterized. The View itself is left untouched, so layout, window insets,
 *   touch regions and gesture handling (swipe-up-home, edge back, long-press for
 *   assistant) are completely unaffected.
 *   A second guard hooks setBackgroundColor(int) to force full transparency.
 *
 * SCOPE: enable for com.google.android.apps.nexuslauncher (Launcher3).
 */
public class HideHandleModule implements IXposedHookLoadPackage {

    private static final String TAG = "HideHandle";
    private static final String LAUNCHER = "com.google.android.apps.nexuslauncher";
    private static final String HANDLE_VIEW =
            "com.android.launcher3.taskbar.StashedHandleView";

    private void log(String s) {
        XposedBridge.log("[" + TAG + "] " + s);
    }

    private void hookView(ClassLoader cl) {
        try {
            Class<?> c = XposedHelpers.findClass(HANDLE_VIEW, cl);

            XposedHelpers.findAndHookMethod(c, "draw", Canvas.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                        }
                    });
            log("Hooked " + HANDLE_VIEW + ".draw -> pill hidden");

            try {
                XposedHelpers.findAndHookMethod(c, "setBackgroundColor", int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                param.args[0] = 0x00000000;
                            }
                        });
                log("Hooked " + HANDLE_VIEW + ".setBackgroundColor -> transparent");
            } catch (Throwable t) {
                log("skip setBackgroundColor: " + t);
            }

        } catch (Throwable t) {
            log("skip " + HANDLE_VIEW + ": " + t);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!LAUNCHER.equals(lpparam.packageName)) return;
        hookView(lpparam.classLoader);
        log("HideNavPill module active (launcher scope)");
    }
}