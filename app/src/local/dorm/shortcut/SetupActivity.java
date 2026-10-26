package local.dorm.shortcut;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Toast;

/** Preserve the original launcher component for existing desktop icons.
 * Navigation only: never starts an unlock session; ignores caller extras.
 */
public final class SetupActivity extends Activity {
    private static long lastLaunch = -5000;
    static final String DOOR_PAGE = "https://biz.confong.cn/app/tmall-xiaoyuan/page-m-webview/doorLock";

    static Intent doorIntent() {
        Uri page = Uri.parse(DOOR_PAGE).buildUpon()
            .appendQueryParameter("loginRequired", "true")
            .appendQueryParameter("hideNavigator", "true").build();
        // The exported router accepts a relative path registered by the app itself.
        // Keep loginRequired=true; only the startup-page dispatcher is avoided.
        Uri link = new Uri.Builder().path("/web/open")
            .appendQueryParameter("url", page.toString()).build();
        return new Intent("com.manu.route", link)
            .setComponent(new ComponentName(Config.TARGET, "com.tmall.campus.route.SchemeFilterActivity"));
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) { finish(); return; }
        DoorService service = DoorService.current();
        if (service != null) service.cancel("切换为只打开开门页，旧任务已停止");
        KeyguardManager guard = (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if (guard == null || guard.isKeyguardLocked()) {
            problem("请先手动解锁手机，再点图标进入开门页。"); return;
        }
        if (!Config.supported(this)) {
            problem("当前天猫校园版本尚未核对：" + Config.version(this) + "。请保留原应用，先在设置中查看状态。"); return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunch < 1500) {
            Toast.makeText(this, "已请求打开，请稍候", Toast.LENGTH_SHORT).show(); finish(); return;
        }
        lastLaunch = now;
        try {
            startActivity(doorIntent());
            Config.prefs(this).edit().putString("last_navigation",
                "已向天猫校园请求打开开门页。是否到达需看实际页面；未点击开锁。").apply();
            finish();
        } catch (RuntimeException e) {
            problem("天猫校园没有接受开门页入口。请从原应用手动进入；长按助手图标可以打开设置。");
        }
    }

    private void problem(String text) {
        new AlertDialog.Builder(this).setTitle("暂时无法直达开门页").setMessage(text)
            .setPositiveButton("助手设置", (d,w) -> {
                startActivity(new Intent(this, SettingsActivity.class)); finish();
            }).setNegativeButton("关闭", (d,w) -> finish())
            .setOnCancelListener(d -> finish()).show();
    }
}
