package com.krovex.launcher.launch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class LaunchSession {
    private Thread preparation;
    private Process game;
    private volatile boolean stopping;

    public boolean isStopping() { return stopping; }

    synchronized void begin() throws InterruptedException {
        if (stopping) throw new InterruptedException();
        preparation = Thread.currentThread();
    }

    synchronized Process start(ProcessBuilder command) throws IOException, InterruptedException {
        if (stopping) throw new InterruptedException();
        game = command.start();
        preparation = null;
        return game;
    }

    public void stop() {
        Process launched;
        synchronized (this) {
            if (stopping) return;
            stopping = true;
            if (preparation != null) preparation.interrupt();
            launched = game;
        }
        if (launched != null) Thread.startVirtualThread(() -> closeGame(launched));
    }

    private static void closeGame(Process game) {
        if (!game.isAlive()) return;
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            try {
                String shell = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0",
                        "powershell.exe").toString();
                // Ask the game window to close so Minecraft can run its normal save-and-exit path.
                String command = "$p = Get-Process -Id " + game.pid() + " -ErrorAction SilentlyContinue; "
                        + "if ($p -and $p.CloseMainWindow()) { exit 0 }; exit 1";
                for (int attempt = 0; attempt < 3 && game.isAlive(); attempt++) {
                    Process closeRequest = new ProcessBuilder(shell, "-NoProfile", "-NonInteractive",
                            "-WindowStyle", "Hidden", "-Command", command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                    if (!closeRequest.waitFor(3, TimeUnit.SECONDS)) closeRequest.destroy();
                    else if (closeRequest.exitValue() == 0) return;
                    Thread.sleep(500);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Fall back to terminating a process with no accessible main window.
            }
        }
        if (game.isAlive()) game.destroy();
    }
}
