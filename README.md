# Hecate

**Runtime concurrency analysis for the JVM.** Hecate attaches to a running Java program as
an agent, records every lock acquire/release as it happens, and then analyzes the captured
trace offline to surface concurrency problems — including **deadlocks that never actually
occurred** during the run.

It works on plain `synchronized` (blocks *and* methods, including inside lambdas) and on
`java.util.concurrent.locks.Lock` (`ReentrantLock`, `ReentrantReadWriteLock`, …).

---

## Why it's interesting

Most concurrency bugs are timing-dependent: a deadlock or a starvation problem may not show
up in the one execution you observed. Hecate's analysis is **predictive** — from a *single
successful run* it reconstructs the lock-ordering behaviour and proves that a different
interleaving *could* deadlock. That's the difference between "it didn't crash this time" and
"this code has a latent deadlock."

---

## Architecture

Capture and analysis are fully decoupled — the agent only writes a JSON trace; all analysis
happens later, offline. This means analysis bugs can't perturb (or crash) the target program,
traces are replayable, and every analyzer is unit-testable on hand-written traces.

```
  Target JVM                          Offline
 ┌───────────────────────┐          ┌──────────────────────────────┐
 │  -javaagent: Hecate   │          │  com.hecate.Hecate (CLI)     │
 │   ├ instrument locks   │  JSON    │   └ LockStateModel (replay)  │
 │   ├ record events  ────┼────────▶ │       ├ DeadlockAnalyzer     │
 │   └ export on shutdown │  trace   │       ├ ContentionAnalyzer   │
 └───────────────────────┘          │       ├ HoldTimeAnalyzer     │
                                     │       └ FairnessAnalyzer     │
                                     │   └ AnalysisReport (txt/json)│
                                     └──────────────────────────────┘
```

### Capture (`com.hecate.agent`, `com.hecate.util`)
- **`LockClassFileTransformer`** — a raw-ASM `ClassFileTransformer` that rewrites
  `synchronized` blocks (`MONITORENTER`/`MONITOREXIT`) and `Lock` call sites
  (`lock`/`lockInterruptibly`/`unlock`/`tryLock`). It visits *every* method, so locks taken
  inside lambda bodies and constructors are captured (ByteBuddy's method wrapper silently
  skips those).
- **`SynchronizedMethodInterceptor`** — ByteBuddy advice for `synchronized` *methods*
  (which have no opcodes to rewrite).
- **`MonitorHelper`** — the injected callbacks; emit `LOCK_WAIT` / `LOCK_ACQUIRE` /
  `LOCK_RELEASE` events into an in-memory queue, exported to `hecate-output/` on shutdown.

### Analysis (`com.hecate.analysis`)
- **`LockStateModel`** — replays the timestamp-sorted events once, pairing
  WAIT→ACQUIRE→RELEASE per thread (handles nesting and reentrancy) into immutable
  `LockAcquisition` records. The shared substrate every analyzer reads.
- Four independent **`Analyzer`** passes (below), combined by **`AnalysisEngine`** into an
  **`AnalysisReport`** (human-readable + JSON).

---

## The analyzers

| Analyzer | What it finds | Core algorithm |
|---|---|---|
| **Deadlock** | Latent circular lock orderings (AB-BA and longer cycles) | iGoodLock |
| **Contention** | Locks that serialize the program | `Σwait / Σhold` per lock |
| **Hold-time** | Abnormally long critical sections | outlier = hold > `mean + 2σ` |
| **Fairness** | Thread starvation | Jain's index over per-thread wait |

### Deadlock detection (iGoodLock)

Each nested acquisition becomes a dependency `(thread, lock, heldLocks)`. The analyzer
searches for a chain of dependencies that closes into a cycle (thread *i* holds the lock
thread *i+1* wants). Three filters remove the classic false positives:

- **Reentrancy** — a lock is removed from its own held-set, so re-locking never forms an edge.
- **Distinct threads** — every dependency in a cycle must come from a different thread.
- **Gate locks** — the held-sets along the cycle must be pairwise disjoint; a shared outer
  lock serializes the threads and makes the cycle benign.

The result is reported even when the analyzed run completed without hanging.

---

## Build

```bash
mvn clean package
```

Produces the agent fat-jar at `target/hecate-1.0-SNAPSHOT.jar` and runs the test suite.

## Usage

**1. Capture** — attach the agent to any Java program:

```bash
java -javaagent:target/hecate-1.0-SNAPSHOT.jar -jar your-program.jar
# → writes hecate-output/hecate-events.json on shutdown
```

**2. Analyze** — run the engine on the trace:

```bash
java -cp target/hecate-1.0-SNAPSHOT.jar com.hecate.Hecate [traceFile] [--json out.json]
# traceFile defaults to hecate-events.json (under hecate-output/)
```

### Try it on the bundled demos

```bash
# Capture from the ReentrantLock AB-BA demo, then analyze
java -javaagent:target/hecate-1.0-SNAPSHOT.jar \
     -cp "target/hecate-1.0-SNAPSHOT.jar:target/test-classes" \
     com.hecate.testapps.ReentrantLockDemo
java -cp target/hecate-1.0-SNAPSHOT.jar com.hecate.Hecate
```

```
[CRITICAL] DEADLOCK   Potential deadlock — circular lock order:
  Thread-1 holds lock@226f0381 wants lock@26aa642e;
  Thread-2 holds lock@26aa642e wants lock@226f0381
```

Demo programs (in `src/test/java/com/hecate/testapps`):
`SynchronizedBlockTest`, `SynchronizedMethodTest`, `DeadlockDemo` (monitor AB-BA),
`ReentrantLockDemo` (j.u.c AB-BA), `OverheadBenchmark`.

---

## Overhead

Measured with `OverheadBenchmark` (4 threads, an *empty* `synchronized` critical section —
the pessimistic case where lock bookkeeping is 100% of the work):

| | work elapsed |
|---|---|
| baseline | ~18 ms |
| with agent | ~190 ms |

That is **~0.86 µs of added overhead per lock acquisition** (three events recorded each).
For realistic critical sections (µs–ms of actual work) this is well under 1%; the ~10×
figure only appears in a degenerate tight loop that locks around nothing.

---

## Known limitations / future work

- **Lock identity** uses `System.identityHashCode`, which can collide and be reused after GC.
  Identity is pluggable (`LockKeyFn`); a stabler allocation-site key is the planned upgrade.
- Only the no-arg `tryLock()` is instrumented; the timed `tryLock(long, TimeUnit)` is not.
- `ThreadStart`/`ThreadEnd` events are modelled but not yet emitted.
- The deadlock pass detects *potential* (lock-order) deadlocks; a wait-for-graph pass for
  *live* deadlocks in a hung trace is a natural addition.
- Analysis is run offline; there is no live/streaming mode.
```
