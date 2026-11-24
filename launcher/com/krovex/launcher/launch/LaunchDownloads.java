package com.krovex.launcher.launch;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

final class LaunchDownloads {
    private LaunchDownloads() {}

    static void run(List<Callable<Void>> jobs, IntConsumer progress) throws IOException, InterruptedException {
        try (var group = new Batch(jobs, progress)) {
            group.await();
        }
    }

    private static final class Batch implements AutoCloseable {
        private final ExecutorService executor = Executors.newFixedThreadPool(10);
        private final ExecutorCompletionService<Void> results = new ExecutorCompletionService<>(executor);
        private final List<Callable<Void>> jobs;
        private final IntConsumer progress;

        Batch(List<Callable<Void>> jobs, IntConsumer progress) {
            this.jobs = jobs;
            this.progress = progress;
        }

        void await() throws IOException, InterruptedException {
            int submitted = 0;
            while (submitted < Math.min(10, jobs.size())) results.submit(jobs.get(submitted++));
            long lastProgress = System.nanoTime();
            for (int completed = 1; completed <= jobs.size(); completed++) {
                try {
                    results.take().get();
                } catch (ExecutionException error) {
                    if (error.getCause() instanceof IOException failure) throw failure;
                    throw new IOException("Minecraft file preparation failed.", error.getCause());
                }
                if (submitted < jobs.size()) results.submit(jobs.get(submitted++));
                long now = System.nanoTime();
                if (completed == jobs.size() || now - lastProgress >= 80_000_000L) {
                    progress.accept(completed);
                    lastProgress = now;
                }
            }
        }

        @Override public void close() { executor.shutdownNow(); }
    }
}
