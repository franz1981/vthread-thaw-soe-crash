#!/bin/bash
# Compile SoeThaw.java and run the yield and sleep variants N times with -Xcomp, plus one control run without -Xcomp.
#   ./run.sh [JAVA_HOME]            (default: $JAVA_HOME, else java from PATH)
# Env: RUNS (default 3), TIMEOUT (per JVM, default 300 s), EXTRA_FLAGS (extra JVM flags for the -Xcomp runs;
#      -Xcomp is still added unless NO_XCOMP=1), PIN (optional CPU list for taskset, e.g. PIN=8-15; unset = no pinning).
# A run is "crashed" if an hs_err file was written, "ok" if the program printed its DONE line, else "error"
# (timeout, other exit, JVM did not start).
# Output goes to out/<timestamp>-<pid>/ (never deleted).
# Exit status: 0 = every run finished without a crash, 1 = crash reproduced, 2 = setup error or a run that
# neither crashed nor finished.
set -u
here=$(cd "$(dirname "$0")" && pwd)
jdk=${1:-${JAVA_HOME:-}}
if [ -n "$jdk" ]; then java=$jdk/bin/java; javac=$jdk/bin/javac; else java=$(command -v java); javac=$(command -v javac); fi
[ -x "$java" ] && [ -x "$javac" ] || { echo "no java/javac found (pass JAVA_HOME as first argument)" >&2; exit 2; }
runs=${RUNS:-3}
tmo=${TIMEOUT:-300}
extra=${EXTRA_FLAGS:-}
xcomp=-Xcomp; [ "${NO_XCOMP:-0}" = 1 ] && xcomp=
pre="nice -n 19"
[ -n "${PIN:-}" ] && pre="$pre taskset -c $PIN"

out=$here/out/$(date +%Y%m%d-%H%M%S)-$$
mkdir -p "$out/classes"
"$javac" -d "$out/classes" "$here/SoeThaw.java" || exit 2

"$java" --version 2>/dev/null | sed -n 2p
printf '%-16s %5s %8s %6s  %s\n' variant runs crashed error "error line"
reproduced=0
failed=0
run_set() { # name variant flags...
  local name=$1 variant=$2; shift 2
  local crashed=0 other=0 line="-"
  for i in $(seq "$runs"); do
    local log=$out/$name-$i.out.txt err=$out/$name-$i.hs_err.log
    $pre timeout "$tmo" "$java" "$@" -Djdk.virtualThreadScheduler.parallelism=1 \
        -XX:-CreateCoredumpOnCrash -XX:ErrorFile="$err" -cp "$out/classes" SoeThaw "$variant" > "$log" 2>&1
    local rc=$?
    if [ -f "$err" ]; then
      crashed=$((crashed + 1))
      line=$(grep -m1 'Internal Error' "$err" | sed 's/^# *//; s/, pid=.*//')
    elif ! grep -qx 'DONE (no crash)' "$log"; then
      other=$((other + 1)); line="exit $rc, see $log"
    fi
  done
  printf '%-16s %5s %8s %6s  %s\n' "$name" "$runs" "$crashed" "$other" "$line"
  [ "$crashed" -gt 0 ] && [ "$name" != control ] && reproduced=1
  [ "$other" -gt 0 ] && failed=1
}
run_set yield yield $xcomp $extra
run_set sleep sleep $xcomp $extra
runs=1 run_set control yield $extra
echo "logs: $out"
[ $reproduced = 1 ] && exit 1
[ $failed = 1 ] && exit 2
exit 0
