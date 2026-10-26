package local.dorm.shortcut;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Non-exported: only explicit internal UI or a widget-held immutable PendingIntent. */
public final class RunActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { finish(); return; } // configuration restoration must not repeat an action
        DoorService service = DoorService.current();
        if (service == null) { message("请先在助手中查看说明，并手动启用无障碍服务"); return; }
        boolean live = !getIntent().getBooleanExtra("rehearse", false) && Config.live(this);
        String error = service.begin(live);
        if (error != null) { message(error); return; }
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(Config.TARGET);
            if (launch == null) throw new IllegalStateException();
            startActivity(launch);
        } catch (RuntimeException e) { service.cancel("无法启动天猫校园，任务停止"); }
        finish();
    }
    private void message(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); finish(); }
}
