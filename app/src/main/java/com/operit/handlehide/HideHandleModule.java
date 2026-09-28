package com.operit.handlehide;

import android.graphics.Canvas;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hide the gesture navigation "pill" (home handle) on Android 17 / PixelOS.
 *
 * From decompiling the device SystemUI:
 *   NavigationHandle (extends android.view.View) has public onDraw(Canvas),
 *   but the concrete drawing path is OVERRIDDEN by the subclass
 *   QuickswitchOrientedNavHandle, whose onDraw is final and calls
 *   computeHomeHandleBounds() + canvas.drawRoundRect(rect, r, r, mPaint).
 *
 * Hooking only NavigationHandle.onDraw therefore logs once or twice but the
 * visible pill keeps being painted by the subclass. We hook BOTH.
 * Gesture handling is untouched (swipe-up-home, edge-back, long-press).
 */
public class HideHandleModule implements IXposedHookLoadPackage {

    private static final String TAG = "HideHandle";
    private static final String SYSTEMUI = "com.android.systemui";

    private static final String HANDLE_CLASS =
            "com.android.systemui.navigationbar.gestural.NavigationHandle";
    private static final String ORIENTED_HANDLE_CLASS =
            "com.android.systemui.navigationbar.gestural.QuickswitchOrientedNavHandle";

    private void log(String s) {
        XposedBridge.log("[" + TAG + "] " + s);
    }

    private void hookHandleClass(ClassLoader cl, String className) {
        try {
            Class<?> c = XposedHelpers.findClass(className, cl);

            XposedHelpers.findAndHookMethod(c, "onDraw", Canvas.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                        }
                    });

            try {
                XposedHelpers.findAndHookMethod(c, "setAlpha", float.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                param.args[0] = 0f;
                            }
                        });
            } catch (Throwable ignored) {
            }

            log("Hooked " + className + ".onDraw -> pill hidden");
        } catch (Throwable t) {
            log("skip " + className + ": " + t);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEMUI.equals(lpparam.packageName)) return;

        hookHandleClass(lpparam.classLoader, HANDLE_CLASS);
        hookHandleClass(lpparam.classLoader, ORIENTED_HANDLE_CLASS);

        log("HideNavPill module active");
    }
}
