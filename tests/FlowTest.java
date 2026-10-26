import local.dorm.shortcut.Flow;

public final class FlowTest {
    private static int checks;
    private static void equal(Object actual, Object expected) {
        checks++; if (!actual.equals(expected)) throw new AssertionError(actual + " != " + expected);
    }
    private static Flow.Action at(Flow f, long t, Flow.Page p, int entry, int unlock) {
        return f.step(t, true, true, p, 4, entry, unlock);
    }
    public static void main(String[] args) {
        Flow f = new Flow(0, false);
        equal(at(f, 0, Flow.Page.HOME, 1, 0), Flow.Action.NONE);
        equal(at(f, 599, Flow.Page.HOME, 1, 0), Flow.Action.NONE);
        equal(at(f, 600, Flow.Page.HOME, 1, 0), Flow.Action.TAP_ENTRY);
        equal(at(f, 1600, Flow.Page.HOME, 1, 0), Flow.Action.NONE);
        equal(at(f, 2300, Flow.Page.HOME, 1, 0), Flow.Action.NONE);
        equal(at(f, 2400, Flow.Page.DOOR, 0, 1), Flow.Action.NONE);
        equal(at(f, 3000, Flow.Page.DOOR, 0, 1), Flow.Action.REHEARSAL_READY);
        equal(at(f, 4000, Flow.Page.DOOR, 0, 1), Flow.Action.NONE);

        f = new Flow(0, true);
        equal(at(f, 0, Flow.Page.DOOR, 0, 1), Flow.Action.NONE);
        equal(at(f, 600, Flow.Page.DOOR, 0, 1), Flow.Action.TAP_UNLOCK);
        for (int i = 0; i < 100; i++) equal(at(f, 700+i*100, Flow.Page.DOOR, 0, 1), Flow.Action.NONE);

        f = new Flow(0, true);
        equal(at(f, 0, Flow.Page.DOOR, 0, 2), Flow.Action.STOP);
        equal(at(f, 1000, Flow.Page.DOOR, 0, 1), Flow.Action.NONE);
        f = new Flow(0, true);
        equal(at(f, 0, Flow.Page.HOME, 2, 0), Flow.Action.STOP);
        f = new Flow(0, true);
        equal(f.step(10, false, true, Flow.Page.DOOR, 4, 0, 1), Flow.Action.STOP);
        f = new Flow(0, true);
        equal(at(f, Flow.TIMEOUT_MS, Flow.Page.DOOR, 0, 1), Flow.Action.STOP);
        f = new Flow(0, true);
        equal(f.step(100, true, false, Flow.Page.WAITING, -1, 0, 0), Flow.Action.NONE);
        equal(f.step(5100, true, false, Flow.Page.WAITING, -1, 0, 0), Flow.Action.STOP);
        f = new Flow(0, true);
        equal(at(f, 100, Flow.Page.OTHER, 0, 0), Flow.Action.STOP);

        f = new Flow(0, true);
        at(f, 0, Flow.Page.DOOR, 0, 1);
        equal(at(f, 500, Flow.Page.DOOR, 0, 0), Flow.Action.NONE);
        equal(at(f, 650, Flow.Page.DOOR, 0, 1), Flow.Action.NONE);
        equal(at(f, 1250, Flow.Page.DOOR, 0, 1), Flow.Action.TAP_UNLOCK);
        f = new Flow(0, true);
        at(f, 0, Flow.Page.DOOR, 0, 1);
        equal(f.step(650, true, true, Flow.Page.DOOR, 5, 0, 1), Flow.Action.NONE);
        equal(f.step(1250, true, true, Flow.Page.DOOR, 5, 0, 1), Flow.Action.TAP_UNLOCK);
        f = new Flow(0, true);
        at(f, 0, Flow.Page.DOOR, 0, 1); f.stop("cancel");
        equal(at(f, 1000, Flow.Page.DOOR, 0, 1), Flow.Action.NONE);
        System.out.println("PASS " + checks + " state-machine assertions; no Android device or real lock used");
    }
}
