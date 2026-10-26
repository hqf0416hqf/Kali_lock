package local.dorm.shortcut;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public final class DoorService extends AccessibilityService {
    private static DoorService connected;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Flow flow;
    private Flow.Page page = Flow.Page.WAITING;
    private int pageWindow = -1;
    private long started;
    private long lastEnd = -10000;
    private boolean running;
    private String entryLabel, unlockLabel;
    private final StringBuilder trace = new StringBuilder();
    private boolean sawTarget, sawSplash, sawHome;
    private final Runnable poll = () -> inspect();

    static DoorService current() { return connected; }
    boolean busy() { return running; }

    @Override protected void onServiceConnected() {
        connected = this;
        running = false; // never restore an armed session after process/service restart
        if (Config.prefs(this).getBoolean("in_progress", false)) {
            Config.prefs(this).edit().putBoolean("in_progress", false)
                .putString("last_result", "服务曾中断，任务未恢复。请检查原应用，勿假定门已打开。")
                .putBoolean("live", false).apply();
        }
        DoorWidget.refresh(this);
    }

    String begin(boolean live) {
        long now = SystemClock.elapsedRealtime();
        if (running) return "已有任务运行，本次点击已忽略";
        long persistedEnd = Config.prefs(this).getLong("last_end_elapsed", -10000);
        if (now >= persistedEnd && now - persistedEnd < 5000 || now - lastEnd < 5000)
            return "请稍候，连续点击已忽略";
        if (!Config.supported(this)) return "尚未适配当前版本：" + Config.version(this);
        if (live && !Config.live(this)) return "请先完成演练，再在设置中启用一次开锁";
        if (!unlocked()) return "请先手动解锁手机";
        entryLabel = Config.entry(this); unlockLabel = Config.unlock(this);
        flow = new Flow(now, live); started = now; running = true;
        page = Flow.Page.WAITING; pageWindow = -1;
        sawTarget = sawSplash = sawHome = false;
        trace.setLength(0); mark(live ? "开始一次开锁任务" : "开始演练（不点击开锁）");
        Config.prefs(this).edit().putBoolean("in_progress", true).apply();
        handler.postDelayed(poll, 250);
        return null;
    }

    void cancel(String reason) {
        if (!running) return;
        flow.stop(reason); finish(reason);
    }

    private boolean unlocked() {
        PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        return power != null && power.isInteractive() && keyguard != null && !keyguard.isKeyguardLocked();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!running || !Config.TARGET.contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String name = event.getClassName() == null ? "" : event.getClassName().toString();
            if (Config.HOME.equals(name)) { page = Flow.Page.HOME; pageWindow = event.getWindowId(); }
            else if (Config.WEB.equals(name)) { page = Flow.Page.DOOR; pageWindow = event.getWindowId(); }
            else if (Config.SPLASH.equals(name) || Config.HOT_SPLASH.equals(name)) {
                page = Flow.Page.WAITING; pageWindow = event.getWindowId();
                if (!sawSplash) { sawSplash = true; mark("观察到启动页（不等于广告已出现）"); }
            } else if (name.endsWith("Activity") || name.contains("Dialog") || name.contains("Popup")) {
                page = Flow.Page.OTHER; pageWindow = event.getWindowId();
            }
        }
    }

    private void inspect() {
        if (!running) return;
        AccessibilityNodeInfo root = null;
        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        try {
            boolean safe = unlocked();
            if (safe) root = getRootInActiveWindow();
            boolean target = root != null && Config.TARGET.contentEquals(root.getPackageName() == null ? "" : root.getPackageName());
            if (target && !sawTarget) { sawTarget = true; mark("首次观察到目标应用界面"); }
            Flow.Page current = target && root.getWindowId() == pageWindow ? page : Flow.Page.WAITING;
            if (target && current == Flow.Page.HOME && !sawHome) { sawHome = true; mark("观察到首页"); }
            if (target && (current == Flow.Page.HOME || current == Flow.Page.DOOR)) {
                find(root, current == Flow.Page.HOME ? entryLabel : unlockLabel, candidates, new int[]{0}, 0);
            }
            int n = candidates.size();
            Flow.Action action = flow.step(SystemClock.elapsedRealtime(), safe, target, current,
                root == null ? -1 : root.getWindowId(), current == Flow.Page.HOME ? n : 0,
                current == Flow.Page.DOOR ? n : 0);
            if (action == Flow.Action.TAP_ENTRY || action == Flow.Action.TAP_UNLOCK) {
                // The state machine already consumed the one allowed attempt.
                AccessibilityNodeInfo button = n == 1 ? candidates.get(0) : null;
                boolean accepted = button != null && button.refresh() && eligible(button) && unlocked()
                    && button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                mark(action == Flow.Action.TAP_ENTRY ? "首页入口点击尝试" : "开锁按钮点击尝试");
                if (!accepted) { flow.stop("按钮点击未被界面接受，已停止，不重试"); finish(flow.reason); return; }
                if (action == Flow.Action.TAP_UNLOCK) { finish(flow.reason); return; }
                page = Flow.Page.WAITING; pageWindow = -1;
            } else if (action == Flow.Action.REHEARSAL_READY) {
                mark("开门页唯一按钮稳定可用；未点击开锁");
                Config.prefs(this).edit().putString("rehearsed", Config.profile(this)).apply();
                finish(flow.reason); return;
            } else if (action == Flow.Action.STOP) { finish(flow.reason); return; }
        } catch (RuntimeException e) {
            // Never store exception messages that could contain original app content.
            flow.stop("界面读取异常，已停止，不重试"); finish(flow.reason); return;
        } finally {
            for (AccessibilityNodeInfo node : candidates) node.recycle();
            if (root != null) root.recycle();
        }
        if (running) handler.postDelayed(poll, 250);
    }

    private static boolean eligible(AccessibilityNodeInfo n) {
        Rect r = new Rect(); n.getBoundsInScreen(r);
        return n.isEnabled() && n.isVisibleToUser() && n.isClickable() && !r.isEmpty()
            && Config.TARGET.contentEquals(n.getPackageName() == null ? "" : n.getPackageName());
    }

    private static void find(AccessibilityNodeInfo node, String label,
                             List<AccessibilityNodeInfo> out, int[] visited, int depth) {
        if (++visited[0] > 800 || depth > 40) throw new IllegalStateException("tree limit");
        // Read transient labels solely for exact comparison. Do not retain or log other text.
        CharSequence text = node.getText(), desc = node.getContentDescription();
        boolean exact = text != null && label.equals(text.toString().trim())
            || desc != null && label.equals(desc.toString().trim());
        if (exact && node.isVisibleToUser() && node.isEnabled()) {
            AccessibilityNodeInfo click = AccessibilityNodeInfo.obtain(node);
            for (int up = 0; click != null && up <= 2; up++) {
                if (eligible(click)) {
                    boolean duplicate = false;
                    for (AccessibilityNodeInfo prior : out) if (prior.equals(click)) duplicate = true;
                    if (!duplicate) out.add(AccessibilityNodeInfo.obtain(click));
                    break;
                }
                if ("android.webkit.WebView".contentEquals(click.getClassName() == null ? "" : click.getClassName())) break;
                AccessibilityNodeInfo parent = click.getParent(); click.recycle(); click = parent;
            }
            if (click != null) click.recycle();
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try { find(child, label, out, visited, depth + 1); } finally { child.recycle(); }
        }
    }

    private void mark(String event) {
        trace.append(String.format(java.util.Locale.ROOT, "%.2f 秒  %s\n",
            (SystemClock.elapsedRealtime() - started) / 1000.0, event));
    }

    private void finish(String result) {
        running = false; handler.removeCallbacks(poll); lastEnd = SystemClock.elapsedRealtime();
        mark(result);
        Config.prefs(this).edit().putBoolean("in_progress", false)
            .putLong("last_end_elapsed", lastEnd).putString("last_result", trace.toString()).apply();
        DoorWidget.refresh(this);
        Toast.makeText(this, result, Toast.LENGTH_LONG).show();
    }

    @Override public void onInterrupt() { cancel("系统中断服务，已停止"); }
    @Override public boolean onUnbind(Intent intent) {
        cancel("无障碍服务已关闭，任务停止"); connected = null; return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        cancel("服务结束，任务停止"); handler.removeCallbacksAndMessages(null);
        if (connected == this) connected = null;
        super.onDestroy();
    }
}
