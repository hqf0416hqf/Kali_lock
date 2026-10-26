package local.dorm.shortcut;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Only configuration; opened by the launcher's long-press settings shortcut. */
public final class SettingsActivity extends Activity {
    private LinearLayout body;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    @Override protected void onResume() {
        super.onResume();
        DoorService service = DoorService.current();
        if (service != null) service.cancel("返回助手设置，任务已停止");
        render();
    }
    private void render() {
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int p = (int)(20 * getResources().getDisplayMetrics().density);
        body.setPadding(p, p, p, p); scroll.addView(body); setContentView(scroll);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
            scroll.requestApplyInsets();
        }
        text("宿舍开门助手设置", 24);
        text("点击应用图标直接进入开门页", 20);
        text("直接点桌面图标即可请求进入天猫校园开门页，不需要先打开本页，也不需要无障碍权限。需要设置时，长按图标选择“助手设置”。开锁仍由你在原应用里点击。", 16);
        button("打开开门页", v -> startActivity(new Intent(this, SetupActivity.class)));
        text("可选：旧版演练与小组件", 20);
        text("以下功能独立于图标直达。小组件模式：" + (Config.live(this) ? "一次开锁" : "演练，不开锁") + "。尚未完成助手的实门验收。", 16);
        text("天猫校园：" + Config.version(this) + (Config.supported(this) ? "（已核对静态入口）" : "（尚未适配）")
            + "\n无障碍服务：" + (DoorService.current() == null ? "未连接" : "已连接"), 16);
        text("权限说明：启用无障碍后，系统允许助手读取界面并执行点击。本助手仅在你发起任务时检查天猫校园的按钮；不保存其他页面文字，不读取凭证，无联网权限。你可以随时关闭服务。", 16);
        button("查看并手动开启无障碍设置", v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (RuntimeException e) { toast("无法打开设置，请从系统设置进入无障碍服务"); }
        });
        button("首页按钮文字：" + Config.entry(this), v -> choose("entry", Config.ENTRY_LABELS));
        button("开锁按钮文字：" + Config.unlock(this), v -> choose("unlock", Config.UNLOCK_LABELS));
        button("开始演练（绝不点击开锁）", v -> {
            Config.prefs(this).edit().putBoolean("live", false).remove("rehearsed").apply();
            DoorWidget.refresh(this);
            startActivity(new Intent(this, RunActivity.class).putExtra("rehearse", true));
        });
        button(Config.live(this) ? "关闭一次开锁，恢复演练" : "演练后启用一次开锁", v -> {
            if (Config.live(this)) {
                Config.prefs(this).edit().putBoolean("live", false).apply();
                DoorWidget.refresh(this); render(); return;
            }
            if (!Config.rehearsed(this)) { toast("请先完成演练，确认能识别唯一开锁按钮"); return; }
            new AlertDialog.Builder(this).setTitle("启用一次开锁")
                .setMessage("请在门边确认：演练停在你有权限使用的正确门的开锁按钮。助手不会选择门，也无法核实当前门的身份。启用后，每次点击桌面小组件将按原应用当前页面提交一次开锁，不再弹窗确认。\n\n这一步只启用模式，不立即开锁。")
                .setNegativeButton("保持演练", null)
                .setPositiveButton("已确认正确门，启用", (d,w) -> {
                    Config.prefs(this).edit().putBoolean("live", true).apply();
                    DoorWidget.refresh(this); render();
                }).show();
        });
        button("添加桌面开门小组件", v -> {
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            if (manager.isRequestPinAppWidgetSupported()) {
                try { manager.requestPinAppWidget(new ComponentName(this, DoorWidget.class), null, null); }
                catch (RuntimeException e) { toast("请长按桌面，手动添加本应用的小组件"); }
            } else toast("请长按桌面，选择小组件，添加宿舍开门助手");
        });
        text("最近一次结果（仅本机保存）", 20);
        text(Config.prefs(this).getString("last_result", "尚未运行。演练记录的是界面时间，不包含门实际响应时间。"), 15);
        button("清除本机记录并恢复演练", v -> {
            Config.prefs(this).edit().clear().apply(); DoorWidget.refresh(this); render();
        });
        text("停止与恢复：返回本页会停止旧版自动化任务；锁屏也会停止。图标直达不启用自动化，也不会点击开锁。关闭无障碍服务、移除小组件或卸载助手即可退出使用。\n\n天猫校园的启动、登录和加载过程仍由原应用处理；直达不保证每次省去启动页，也不保证缩短全部等待。", 15);
    }
    private void choose(String key, String[] options) {
        new AlertDialog.Builder(this).setTitle("选择与你页面完全一致的文字")
            .setItems(options, (d,w) -> {
                Config.prefs(this).edit().putString(key, options[w]).apply();
                Config.disable(this); render();
            }).show();
    }
    private void text(String s, int size) {
        TextView view = new TextView(this); view.setText(s); view.setTextSize(size); view.setTextColor(0xff182b36);
        view.setPadding(0, 10, 0, 10); body.addView(view);
    }
    private void button(String label, View.OnClickListener listener) {
        Button view = new Button(this); view.setText(label);
        view.setFilterTouchesWhenObscured(true); view.setOnClickListener(listener); body.addView(view);
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
