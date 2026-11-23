/**
 * A virtual thread recurses close to its stack limit, then yields or sleeps there.
 * With -Xcomp the JVM aborts (ShouldNotReachHere in SharedRuntime::raw_exception_handler_for_return_address).
 *
 * usage: javac SoeThaw.java && java -Xcomp -Djdk.virtualThreadScheduler.parallelism=1 SoeThaw <yield|sleep>
 *
 * Each iteration measures the deepest recursion depth D that fits, then recurses to D-k and parks there
 * (k = 0, 1, 2, ...).
 */
public class SoeThaw {
    static int reached;

    static void rec(int depth, int target, Runnable atBottom) {
        reached = depth;
        if (depth < target) {
            rec(depth + 1, target, atBottom);
            return;
        }
        atBottom.run();
    }

    /** Deepest recursion depth that fits before StackOverflowError. */
    static int maxDepth() {
        try {
            rec(0, Integer.MAX_VALUE, () -> {});
        } catch (StackOverflowError expected) {
        }
        return reached;
    }

    public static void main(String[] args) throws Exception {
        boolean sleep = args.length > 0 && args[0].equals("sleep");
        Runnable park = () -> {
            if (sleep) {
                try { Thread.sleep(1); } catch (InterruptedException e) { throw new RuntimeException(e); }
            } else {
                Thread.yield();
            }
        };
        Thread vt = Thread.ofVirtual().start(() -> {
            for (int k = 0; k <= 5000; k++) {
                int d = maxDepth();
                try {
                    rec(0, d - k, park);
                } catch (StackOverflowError e) {
                    // expected when the park point is too close to the limit
                }
            }
        });
        vt.join();
        System.out.println("DONE (no crash)");
    }
}
