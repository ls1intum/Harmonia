package de.tum.cit.aet.dataProcessing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Manages concurrent task state and lifecycle for analysis pipelines.
 * Tracks active executors, stream threads, and running futures by exercise ID.
 */
@Service
@Slf4j
public class AnalysisTaskManager {

    /** Active download/git-analysis executors by exerciseId (for cancellation). */
    private final Map<Long, ExecutorService> activeExecutors = new ConcurrentHashMap<>();

    /** Main stream-analysis threads by exerciseId (for interrupt-based cancellation). */
    private final Map<Long, Thread> runningStreamTasks = new ConcurrentHashMap<>();

    /** Running Future tasks by exerciseId (used by RequestResource). */
    private final Map<Long, Future<?>> runningFutures = new ConcurrentHashMap<>();

    /**
     * Stops a running analysis by interrupting its executor and stream thread.
     *
     * @param exerciseId the exercise ID to stop
     */
    public void stopAnalysis(Long exerciseId) {
        log.info("Stopping analysis for exerciseId={}", exerciseId);

        // 1) Cancel the Future task
        Future<?> future = runningFutures.remove(exerciseId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }

        // 2) Shut down the download/git-analysis executor
        ExecutorService executor = activeExecutors.remove(exerciseId);
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 3) Interrupt the main stream thread (stops AI analysis phase)
        Thread streamThread = runningStreamTasks.remove(exerciseId);
        if (streamThread != null && streamThread.isAlive()) {
            streamThread.interrupt();
        }
    }

    /**
     * Checks if an analysis task is currently running.
     *
     * @param exerciseId the exercise ID to check
     * @return true if a task is running for the given exercise
     */
    public boolean isTaskRunning(Long exerciseId) {
        Future<?> future = runningFutures.get(exerciseId);
        return future != null && !future.isDone();
    }

    /**
     * Registers a running task for tracking and cancellation.
     *
     * @param exerciseId the exercise ID
     * @param future     the future representing the running task
     */
    public void registerRunningTask(Long exerciseId, Future<?> future) {
        runningFutures.put(exerciseId, future);
    }

    /**
     * Unregisters a completed task.
     *
     * @param exerciseId the exercise ID to unregister
     */
    public void unregisterRunningTask(Long exerciseId) {
        runningFutures.remove(exerciseId);
    }

    public void registerExecutor(Long exerciseId, ExecutorService executor) {
        activeExecutors.put(exerciseId, executor);
    }

    public void unregisterExecutor(Long exerciseId) {
        activeExecutors.remove(exerciseId);
    }

    public void registerStreamThread(Long exerciseId, Thread thread) {
        runningStreamTasks.put(exerciseId, thread);
    }

    public void unregisterStreamThread(Long exerciseId) {
        runningStreamTasks.remove(exerciseId);
    }
}
