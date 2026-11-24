/**
 * Variant of SoeThaw for aarch64. A virtual thread recurses close to its stack limit, then yields there.
 *
 * usage: javac SoeThawA64.java && java -Xcomp -Djdk.virtualThreadScheduler.parallelism=1 SoeThawA64 [k ...]
 *
 * Differences from SoeThaw:
 *  - each attempt runs in its own method (attempt), called once per attempt, instead of inline in the
 *    thread's body, which is invoked only once;
 *  - four warm-up attempts park at depth 0 before the first deep attempt.
 * Without these, on aarch64 every freeze near the limit took the slow path (an interpreted frame was
 * recorded in JavaThread::_cont_fastpath) and failed with StackOverflowError before the thread unmounted.
 *
 * No arguments: k = 0, 1, ..., 5000 after the warm-up. With arguments: only those k values.
 * Each attempt measures the deepest recursion depth D, then recurses to D-k and yields there.
 */
public class SoeThawA64 {
    static int reached;

    static void rec(int depth, int target, Runnable atBottom) {
        reached = depth;
        if (depth < target) {
            rec(depth + 1, target, atBottom);
            return;
        }
        atBottom.run();
    }

    static int maxDepth() {
        try {
            rec(0, Integer.MAX_VALUE, () -> {});
        } catch (StackOverflowError expected) {
        }
        return reached;
    }

    static void attempt(int k) {
        int d = maxDepth();
        try {
            rec(0, d - k, Thread::yield);
        } catch (StackOverflowError e) {
            // expected when the park point is too close to the limit
        }
    }

    public static void main(String[] args) throws Exception {
        Thread vt = Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 4; i++) attempt(1_000_000); // target < 0: yields at depth 0
            if (args.length == 0) {
                for (int k = 0; k <= 5000; k++) attempt(k);
            } else {
                for (String s : args) attempt(Integer.parseInt(s));
            }
        });
        vt.join();
        System.out.println("DONE (no crash)");
    }
}
