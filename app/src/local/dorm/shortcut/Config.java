package local.dorm.shortcut;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;

final class Config {
    static final String TARGET = "com.tmall.campus.and";
    static final String HOME = "com.tmall.campus.and.main.MainActivity";
    static final String WEB = "com.tmall.campus.bizwebview.ui.CampusWebActivity";
    static final String SPLASH = "com.tmall.campus.launcher.SplashActivity";
    static final String HOT_SPLASH = "com.tmall.campus.launcher.HotSplashActivity";
    static final String[] ENTRY_LABELS = {"我开门", "点我开门"};
    static final String[] UNLOCK_LABELS = {"点击开锁", "开锁", "立即开锁"};
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("local", Context.MODE_PRIVATE); }
    static String entry(Context c) { return prefs(c).getString("entry", ENTRY_LABELS[0]); }
    static String unlock(Context c) { return prefs(c).getString("unlock", UNLOCK_LABELS[0]); }
    static String profile(Context c) { return version(c) + ":" + entry(c) + ":" + unlock(c); }
    static boolean rehearsed(Context c) { return profile(c).equals(prefs(c).getString("rehearsed", "")); }
    static boolean live(Context c) { return rehearsed(c) && prefs(c).getBoolean("live", false); }
    static boolean supported(Context c) {
        String version = version(c);
        return "5.7.2 / 50007002".equals(version) || "5.8.0 / 50008000".equals(version);
    }
    static String version(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(TARGET, 0);
            long code = Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
            return p.versionName + " / " + code;
        } catch (Exception e) { return "未安装或系统未允许查看"; }
    }
    static void disable(Context c) {
        prefs(c).edit().putBoolean("live", false).remove("rehearsed").apply();
        DoorWidget.refresh(c);
    }
}
