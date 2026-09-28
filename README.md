# Hide Nav Pill  (LSPosed module)

Hides the gesture-navigation "pill" / home handle on **PixelOS (Android 17)**.
Only the *drawing* of the pill is suppressed. Edge-back, swipe-up-home and
long-press-assistant are all left untouched.

---

## 1. Why this works (逆向依据)

From the SystemUI apk (`classes2.dex`), class

    com.android.systemui.navigationbar.gestural.NavigationHandle
        .super Landroid/view/View;
        .implements ...ButtonInterface
        public void onDraw(Landroid/graphics/Canvas;)V   (overridden)

Decompiled `onDraw` (baksmali) does, in order:

    invoke-super {p0, p1} Landroid/view/View;->onDraw(Landroid/graphics/Canvas;)V
    ... compute rounded-rect geometry (mRadius / mBottom / mShrink /
        mPulseAnimationProgress / mAdditionalWidthForAnimation ...)
    invoke-virtual {vX, ...F F F F F F, mPaint}
        Landroid/graphics/Canvas;->drawRoundRect(...)V   <-- 这一句画出小白条

So the pill is a single `drawRoundRect()` inside `NavigationHandle.onDraw()`.
Returning before that call hides the pill **completely**, and because gesture
logic lives elsewhere (`animateLongPress`, `abortCurrentGesture`,
`EdgeBackGestureHandler`, ...) nothing about gestures changes.

`QuickswitchOrientedNavHandle` **extends** `NavigationHandle`, so hooking the
base-class `onDraw` also covers the rotated / secondary handle.

## 2. What this module hooks

    Package : com.android.systemui
    Class   : com.android.systemui.navigationbar.gestural.NavigationHandle
    Hook 1  : onDraw(Canvas)        -> setResult(null)  (suppress drawing)
    Hook 2  : setAlpha(float)       -> force arg=0f    (safety net)

Nothing else is touched. No resource/RRO overlay is used, so there is no risk
of the bootloop that the RRO "hide pill" approach causes on Android 17.

---

## 3. Build

Requirements: **JDK 17**, **Android SDK platform 35**, `ANDROID_HOME` or a
`local.properties` file. The Gradle wrapper (8.7) is bundled, no local Gradle
install is needed.

    # one-time: point at your SDK (or export ANDROID_HOME)
    cp local.properties.example local.properties
    # edit local.properties -> sdk.dir=/path/to/Android/Sdk

    ./gradlew :app:assembleRelease

Output:

    app/build/outputs/apk/release/app-release.apk

(it is signed with the AGP built-in debug key, installs as a normal app.)

## 4. Install & enable

1. Install `app-release.apk` on the device.
2. Open **LSPosed Manager -> Modules**, enable **Hide Nav Pill**.
3. Scope: tick **only `com.android.systemui` (System UI)**.
   (You may also tick *System Framework* if your LSPosed build asks for it; the
   module itself only ever acts when SystemUI loads.)
4. Reboot, or just restart SystemUI:

       su -c "killall com.android.systemui"

## 5. Verify

    su -c "logcat -d | grep HideHandle"

Expected line:

    [HideHandle] Hooked NavigationHandle onDraw -> pill hidden

If instead you see `[HideHandle] FAILED: ...`, the class name differs on your
build. Re-check it with:

    baksmali d /system/system_ext/priv-app/SystemUI/*/SystemUI.apk -o out >/dev/null 2>&1       || (unzip -o /system/system_ext/priv-app/SystemUI/*/SystemUI.apk classes*.dex -d sysui)
    grep -rl NavigationHandle sysui/ | head

## 6. Remove the old NavTweaks leftover first (important)

If `/data/adb/HideNavBar_config.sh` still exists (HD=true / VAR3=HP), delete it
so it cannot conflict / re-apply the RRO that bootloops on Android 17:

    su -c "rm -f /data/adb/HideNavBar_config.sh"

## 7. Uninstall / revert

Just disable the module in LSPosed Manager (and reboot / restart SystemUI).
No persistent system change is made.
