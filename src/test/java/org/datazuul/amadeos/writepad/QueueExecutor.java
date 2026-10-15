package org.datazuul.amadeos.writepad;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An executor for tests that runs nothing until asked: {@link #runAll()} runs the queued tasks, including
 * those they queue, in order. Background work and the continuations on the "event dispatch thread" can so
 * be interleaved with edits deterministically.
 */
public final class QueueExecutor extends AbstractExecutorService {
    private final Queue<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(Runnable command) {
        tasks.add(command);
    }

    /** Runs one task; false if there was none. */
    public boolean runNext() {
        Runnable task = tasks.poll();
        if (task == null) return false;
        task.run();
        return true;
    }

    public void runAll() {
        while (runNext()) {
            // keep going
        }
    }

    public boolean isIdle() {
        return tasks.isEmpty();
    }

    @Override
    public void shutdown() {
    }

    @Override
    public List<Runnable> shutdownNow() {
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }
}
