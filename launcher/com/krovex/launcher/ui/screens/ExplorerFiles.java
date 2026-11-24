package com.krovex.launcher.ui.screens;

import java.awt.Desktop;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.TransferHandler;

public final class ExplorerFiles {
    private ExplorerFiles() {}

    public static void open(Consumer<String> status) {
        File home = new File(System.getProperty("user.home"));
        File downloads = new File(home, "Downloads");
        try {
            Desktop.getDesktop().open(downloads.isDirectory() ? downloads : home);
        } catch (IOException | RuntimeException error) {
            status.accept("Could not open File Explorer. Open it manually and drag the file here.");
        }
    }

    public static TransferHandler drop(Consumer<List<File>> accept) {
        return new TransferHandler() {
            @Override public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    Object data = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!(data instanceof List<?> list) || list.isEmpty()
                            || !list.stream().allMatch(File.class::isInstance)) return false;
                    accept.accept(list.stream().map(File.class::cast).toList());
                    return true;
                } catch (IOException | java.awt.datatransfer.UnsupportedFlavorException error) {
                    return false;
                }
            }
        };
    }
}
