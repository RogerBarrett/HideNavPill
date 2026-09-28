package com.operit.handlehide;

import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hide the gesture-navigation "pill" / home handle on Android 17 (PixelOS).
 *
 * ROOT CAUSE (v4, corrected after real device logs):
 *   The handle is drawn by the Launcher process, class:
 *     com.android.launcher3.taskbar.StashedHandleView extends android.view.View
 *
 *   This class has NO draw(Canvas) and NO onDraw() override (v3 hooking draw()
 *   failed with NoSuchMethodError). It paints the rounded bar PURELY through its
 *   background colour. The only colour entry point is:
 *     updateHandleColor(boolean dark, boolean animate)
 *        -> ObjectAnimator on VIEW_BACKGROUND_COLOR, or
 *           View.setBackgroundColor(colour)
 *
 * STRATEGY (minimal, zero side effects):
 *   1. Hook StashedHandleView.updateHandleColor(boolean, boolean) -> no-op.
 *   2. Guard hook View.setBackgroundColor(int): force transparent when the
 *      target is a StashedHandleView.
 *   The View stays alive, so layout/insets/touch/gestures are unaffected.
 *
 * SCOPE: com.google.android.apps.nexuslauncher (Launcher3).
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
        Class<?> handleView;
        try {
            handleView = XposedHelpers.findClass(HANDLE_VIEW, cl);
        } catch (Throwable t) {
            log("class not found " + HANDLE_VIEW + ": " + t);
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(handleView, "updateHandleColor",
                    boolean.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                        }
                    });
            log("Hooked updateHandleColor -> pill stays transparent");
        } catch (Throwable t) {
            log("FAILED updateHandleColor hook: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(View.class, "setBackgroundColor",
                    int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (handleView.isInstance(param.thisObject)) {
                                param.args[0] = 0x00000000;
                            }
                        }
                    });
            log("Hooked View.setBackgroundColor (guard for StashedHandleView)");
        } catch (Throwable t) {
            log("FAILED setBackgroundColor guard: " + t);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!LAUNCHER.equals(lpparam.packageName)) return;
        hookView(lpparam.classLoader);
        log("HideNavPill module active (launcher scope) v4");
    }
}