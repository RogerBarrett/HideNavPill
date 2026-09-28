package com.operit.handlehide;

import android.graphics.Canvas;
import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * HideNavPill (Hide Handle) - LSPosed module.
 *
 * Goal: make the gesture navigation "pill" (home handle) invisible on
 * PixelOS / Android 17 (Launcher3 taskbar) WITHOUT breaking gestures:
 *   - swipe up to home
 *   - edge back gesture
 *   - long-press pill -> Google Circle to Search
 *
 * History / lessons learned:
 *   v3: hooked NavigationHandle.onDraw in SystemUI. Wrong target (that is NOT
 *       the pill that is drawn on Pixel launcher). No effect.
 *   v4: hooked StashedHandleView.updateHandleColor -> no-op + forced transparent
 *       background. Pill became BLACK instead of disappearing -> proving we hit
 *       the right View but clearing its color is not enough.
 *   v5: forced the View INVISIBLE + alpha 0 -> pill disappeared (SUCCESS visually)
 *       but long-press Circle to Search stopped working, because an INVISIBLE
 *       View is removed from hit-testing, so the system no longer sees the
 *       handle region.
 *   v6: ONLY skip rasterisation. Hook android.view.View#draw(Canvas); when the
 *       receiver is a StashedHandleView, swallow the call (setResult(null)).
 *       The View stays VISIBLE with alpha 1, keeps its bounds, keeps its
 *       hit-test region, so circle-to-search long-press keeps working, but
 *       nothing is ever drawn -> pill invisible.
 *
 * Note: StashedHandleView does NOT override draw/onDraw (verified via smali),
 * it is plain View + background color. Therefore the only way to intercept it
 * is to hook View#draw itself (hooking the subclass would throw
 * NoSuchMethodError, which is exactly why v3's draw attempt failed).
 */
public class HideHandleModule implements IXposedHookLoadPackage {

    private static final String TAG = "HideHandle";
    private static final String LAUNCHER = "com.google.android.apps.nexuslauncher";
    private static final String HANDLE_VIEW = "com.android.launcher3.taskbar.StashedHandleView";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || lpparam.packageName == null) return;
        if (!LAUNCHER.equals(lpparam.packageName)) return;

        final Class<?> handleView;
        try {
            handleView = XposedHelpers.findClass(HANDLE_VIEW, lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": StashedHandleView not found, abort");
            return;
        }

        // (1) CORE: never rasterise the handle. Keeps View alive & hit-testable.
        try {
            XposedHelpers.findAndHookMethod(View.class, "draw", Canvas.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.thisObject != null
                                    && handleView.isInstance(param.thisObject)) {
                                param.setResult(null); // skip drawing entirely
                            }
                        }
                    });
            XposedBridge.log(TAG + ": Hooked View.draw -> handle is never rasterised (hit area kept)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook View.draw failed: " + t);
        }

        // (2) Safety net: if something sets a background colour, keep it transparent
        //     so that even if draw() is bypassed by a parent composite, no black bar.
        try {
            XposedHelpers.findAndHookMethod(View.class, "setBackgroundColor", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.thisObject != null
                                    && handleView.isInstance(param.thisObject)) {
                                param.args[0] = 0x00000000;
                            }
                        }
                    });
            XposedBridge.log(TAG + ": Hooked View.setBackgroundColor (guard for StashedHandleView)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook View.setBackgroundColor failed: " + t);
        }

        // (3) Legacy: make colour updates a no-op (avoids animations fighting us).
        try {
            XposedHelpers.findAndHookMethod(HANDLE_VIEW, lpparam.classLoader,
                    "updateHandleColor", boolean.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                        }
                    });
            XposedBridge.log(TAG + ": Hooked updateHandleColor -> pill stays transparent");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook updateHandleColor failed: " + t);
        }

        XposedBridge.log(TAG + ": HideNavPill module active (launcher scope) v6");
    }
}
