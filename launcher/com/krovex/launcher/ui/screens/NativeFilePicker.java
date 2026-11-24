package com.krovex.launcher.ui.screens;

import com.krovex.theme.json.Json;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import javax.swing.*;

public final class NativeFilePicker {
    private static final String SCRIPT = """
            $ErrorActionPreference = 'Stop'
            $ProgressPreference = 'SilentlyContinue'
            [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
            Add-Type -AssemblyName System.Windows.Forms
            $dialog = [System.Windows.Forms.OpenFileDialog]::new()
            $dialog.AutoUpgradeEnabled = $true
            $dialog.CheckFileExists = $true
            $dialog.Title = $env:KROVEX_PICKER_TITLE
            $dialog.Filter = $env:KROVEX_PICKER_FILTER
            $dialog.Multiselect = $env:KROVEX_PICKER_MULTI -eq 'true'
            $downloads = Join-Path ([Environment]::GetFolderPath('UserProfile')) 'Downloads'
            if ([IO.Directory]::Exists($downloads)) { $dialog.InitialDirectory = $downloads }
            [Console]::WriteLine("KROVEX_PICKER_READY")
            $files = @()
            if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { $files = @($dialog.FileNames) }
            ConvertTo-Json -InputObject $files -Compress
            """;

    private NativeFilePicker() {}

    public static List<File> open(Window owner, String title, boolean multiple, String... extensions) {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            return openSystemPicker(owner, title, multiple, extensions);
        }
        JDialog waiting = new JDialog(owner, "Open", Dialog.ModalityType.APPLICATION_MODAL);
        waiting.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        waiting.add(new JLabel("  Waiting for the Windows Open dialog…  "));
        waiting.setSize(330, 100);
        waiting.setLocationRelativeTo(owner);
        SwingWorker<List<File>, Void> worker = new SwingWorker<>() {
            @Override protected List<File> doInBackground() throws Exception {
                return runWindowsPicker(SCRIPT, title, multiple, extensions);
            }

            @Override protected void done() { waiting.dispose(); }
        };
        worker.execute();
        waiting.setVisible(true);
        try {
            return worker.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (java.util.concurrent.ExecutionException error) {
            return openSystemPicker(owner, title, multiple, extensions);
        }
    }

    private static List<File> runWindowsPicker(String script, String title, boolean multiple, String... extensions)
            throws java.io.IOException, InterruptedException {
        String systemRoot = System.getenv("SystemRoot");
        String executable = systemRoot == null || systemRoot.isBlank() ? "powershell.exe"
                : Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString();
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        ProcessBuilder builder = new ProcessBuilder(executable, "-NoProfile", "-NonInteractive", "-STA",
                "-WindowStyle", "Hidden", "-EncodedCommand", encoded);
        builder.environment().put("KROVEX_PICKER_TITLE", title);
        builder.environment().put("KROVEX_PICKER_MULTI", Boolean.toString(multiple));
        builder.environment().put("KROVEX_PICKER_FILTER", "Supported files|*." + String.join(";*.", extensions) + "|All files|*.*");
        // PowerShell can send diagnostic XML to stderr even when the picker succeeds.
        Process process = builder.redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            String output;
            try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
                var ready = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try { return reader.readLine(); }
                    catch (java.io.IOException error) { return null; }
                });
                try {
                    if (!"KROVEX_PICKER_READY".equals(ready.get(15, java.util.concurrent.TimeUnit.SECONDS))) {
                        throw new java.io.IOException("Windows file picker did not initialize.");
                    }
                } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException error) {
                    process.destroyForcibly();
                    throw new java.io.IOException("Windows file picker did not initialize.", error);
                }
                StringBuilder response = new StringBuilder();
                for (String line; (line = reader.readLine()) != null;) response.append(line);
                output = response.toString().strip();
            }
            if (process.waitFor() != 0) throw new java.io.IOException("Windows file picker failed.");
            if (output.startsWith("\uFEFF")) output = output.substring(1);
            if (!(Json.parse(output) instanceof List<?> files)
                    || files.stream().anyMatch(file -> !(file instanceof String))) {
                throw new java.io.IOException("Windows file picker returned an invalid file list.");
            }
            return files.stream().map(String.class::cast).map(File::new).toList();
        } finally {
            if (process.isAlive()) process.destroy();
        }
    }

    private static List<File> openSystemPicker(Window owner, String title, boolean multiple, String[] extensions) {
        FileDialog dialog = owner instanceof Dialog parent ? new FileDialog(parent, title, FileDialog.LOAD)
                : new FileDialog(owner instanceof Frame frame ? frame : null, title, FileDialog.LOAD);
        try {
            dialog.setMultipleMode(multiple);
            File downloads = new File(System.getProperty("user.home"), "Downloads");
            if (downloads.isDirectory()) dialog.setDirectory(downloads.getPath());
            if (!System.getProperty("os.name", "").startsWith("Windows")) dialog.setFilenameFilter((folder, filename) -> {
                String lower = filename.toLowerCase(java.util.Locale.ROOT);
                for (String extension : extensions) {
                    if (lower.endsWith("." + extension)) return true;
                }
                return false;
            });
            dialog.setVisible(true);
            return List.of(dialog.getFiles());
        } finally {
            dialog.dispose();
        }
    }
}


