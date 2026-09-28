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
 * Target: com.android.systemui.navigationbar.gestural.NavigationHandle
 *         (extends android.view.View, overrides onDraw(Canvas))
 *
 * Only the drawing is suppressed. Gesture handling (edge back, swipe up home,
 * long-press assistant) lives in other methods/classes and is untouched.
 */
public class HideHandleModule implements IXposedHookLoadPackage {

    private static final String TAG = "HideHandle";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String HANDLE_CLASS =
            "com.android.systemui.navigationbar.gestural.NavigationHandle";

    private void log(String s) {
        XposedBridge.log("[" + TAG + "] " + s);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEMUI.equals(lpparam.packageName)) return;

        try {
            Class<?> handleClass = XposedHelpers.findClass(HANDLE_CLASS, lpparam.classLoader);

            // Primary hook: suppress the round-rect drawing entirely.
            XposedHelpers.findAndHookMethod(handleClass, "onDraw", Canvas.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            // Returning early means Canvas.drawRoundRect() never runs.
                            param.setResult(null);
                        }
                    });

            // Safety net: if any code path forces alpha>0, the View is still
            // invisible because onDraw is suppressed; but also force alpha 0.
            try {
                XposedHelpers.findAndHookMethod(handleClass, "setAlpha", float.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                param.args[0] = 0f;
                            }
                        });
            } catch (Throwable t) {
                log("setAlpha hook skipped: " + t);
            }

            log("Hooked NavigationHandle onDraw -> pill hidden");
        } catch (Throwable t) {
            log("FAILED: " + t);
            XposedBridge.log(t);
        }
    }
}
