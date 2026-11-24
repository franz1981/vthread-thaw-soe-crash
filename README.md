# JVM crash (ShouldNotReachHere) with -Xcomp when a virtual thread yields near its stack limit

A virtual thread recurses close to its stack limit, then calls `Thread.yield()` or `Thread.sleep(1)`. With `-Xcomp`
the JVM aborts with `ShouldNotReachHere()` in `SharedRuntime::raw_exception_handler_for_return_address`; without
`-Xcomp` the same program finishes. Under gdb, `Continuation::prepare_thaw` returned 0 right before the abort.
The cause has not been investigated.

## Run

```
./run.sh /path/to/jdk       # JAVA_HOME as the argument
RUNS=5 ./run.sh /path/to/jdk
```

Runs `yield` and `sleep` `RUNS` times (default 3) with `-Xcomp -Djdk.virtualThreadScheduler.parallelism=1` (the value
used in all runs; other values not tried), plus one control without `-Xcomp`. `crashed` = an `hs_err` file was written;
`error` = neither a crash nor `DONE (no crash)`. Logs: `out/<timestamp>-<pid>/`. Exit status and env vars: header of `run.sh`.

By hand: `javac SoeThaw.java && java -Xcomp -Djdk.virtualThreadScheduler.parallelism=1 SoeThaw yield`.

## Results (Linux x86_64, parallelism=1 in all runs, 3 runs per variant)

```
OpenJDK Runtime Environment Temurin-25.0.3+9 (build 25.0.3+9-LTS)
variant           runs  crashed  error  error line
yield                3        3      0  Internal Error (sharedRuntime.cpp:596)
sleep                3        3      0  Internal Error (sharedRuntime.cpp:596)
control              1        0      0  -
```

Same result (3/3 yield, 3/3 sleep, control without crash) on Temurin 27+35 (`sharedRuntime.cpp:646`) and
OpenJDK 28-ea+25 (`sharedRuntime.cpp:657`).

## hs_err excerpt (Temurin 25.0.3, yield)

```
#  Internal Error (sharedRuntime.cpp:596), pid=..., tid=...
#  Error: ShouldNotReachHere()
...
# Java VM: OpenJDK 64-Bit Server VM Temurin-25.0.3+9 (25.0.3+9-LTS, compiled mode, sharing, tiered, compressed oops, compressed class ptrs, g1 gc, linux-amd64)
...
V  [libjvm.so+0xf4fe48]  SharedRuntime::raw_exception_handler_for_return_address(JavaThread*, unsigned char*) [clone .part.0]+0x148  (sharedRuntime.cpp:596)
v  ~StubRoutines::forward_exception 0x00007f792f817f0b
```

27+35 and 28-ea+25 show the same two frames.

Other flags (Temurin 25.0.3, 2 runs per variant): still crashes with `-Xcomp` plus
`-XX:TieredStopAtLevel=1`, `-XX:+UseSerialGC` or `-Xss256k`. No crash (program printed `DONE`) with `-Xcomp -XX:-TieredCompilation`, or without `-Xcomp`. No other flags tried.
