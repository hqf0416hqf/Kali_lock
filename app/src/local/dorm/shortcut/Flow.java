package local.dorm.shortcut;

/** Android-free state machine. Every returned click is consumed before dispatch. */
public final class Flow {
    public enum Page { WAITING, HOME, DOOR, OTHER }
    public enum Action { NONE, TAP_ENTRY, REHEARSAL_READY, TAP_UNLOCK, STOP }
    public enum State { ENTRY, DOOR, DONE }
    public static final long TIMEOUT_MS = 180000;
    private static final long STABLE_MS = 600;
    private final long start;
    private final boolean live;
    private State state = State.ENTRY;
    private Page stablePage;
    private int stableWindow = -1;
    private long stableSince;
    public String reason = "";

    public Flow(long now, boolean live) { this.start = now; this.live = live; }
    public boolean active() { return state != State.DONE; }
    public void stop(String why) { state = State.DONE; reason = why; }

    public Action step(long now, boolean unlocked, boolean targetPresent,
                       Page page, int window, int entryCount, int unlockCount) {
        if (!active()) return Action.NONE;
        if (now < start || now - start >= TIMEOUT_MS) return fail("超时停止，未重试");
        if (!unlocked) return fail("手机锁定或息屏，已停止");
        if (!targetPresent) {
            stablePage = null;
            if (now - start > 5000) return fail("已离开目标应用，停止");
            return Action.NONE;
        }
        if (page == Page.OTHER) return fail("出现未适配页面，停止");
        if (page == Page.WAITING) { stablePage = null; return Action.NONE; }
        int count = page == Page.HOME ? entryCount : unlockCount;
        if (count > 1) return fail("出现多个匹配按钮，请手动操作");
        if (count != 1) { stablePage = null; return Action.NONE; }
        if (stablePage != page || stableWindow != window) {
            stablePage = page; stableWindow = window; stableSince = now;
            return Action.NONE;
        }
        if (now - stableSince < STABLE_MS) return Action.NONE;
        if (page == Page.HOME && state == State.ENTRY) {
            state = State.DOOR; stablePage = null;
            return Action.TAP_ENTRY;
        }
        if (page == Page.DOOR) {
            state = State.DONE; // irreversible terminal transition before a click
            reason = live ? "已请求一次按钮点击；门是否打开需现场确认" : "演练完成：找到唯一开锁按钮，未点击";
            return live ? Action.TAP_UNLOCK : Action.REHEARSAL_READY;
        }
        return Action.NONE;
    }
    private Action fail(String why) { stop(why); return Action.STOP; }
}
