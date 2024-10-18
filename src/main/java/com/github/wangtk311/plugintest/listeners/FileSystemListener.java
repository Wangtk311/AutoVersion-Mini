package com.github.wangtk311.plugintest.listeners;

import com.github.wangtk311.plugintest.services.FileChange;
import com.github.wangtk311.plugintest.services.VersionStorage;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

public class FileSystemListener {

    private final Path projectPath;
    private boolean isListening = true;

    public FileSystemListener(Path projectPath) {
        this.projectPath = projectPath;
        enableListening();
        startListening();
    }

    private void startListening() {
        if (!isListening) {
            return;
        }
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            projectPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);

            Thread thread = new Thread(() -> {
                while (true) {
                    try {
                        WatchKey key = watchService.take();
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();

                            // 文件路径
                            Path filePath = projectPath.resolve((Path) event.context());
                            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                                // 处理文件创建事件
                                handleFileCreate(filePath);
                            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                                // 处理文件删除事件
                                handleFileDelete(filePath);
                            }
                        }
                        key.reset();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });

            thread.setDaemon(true);
            thread.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleFileCreate(Path filePath) {
        if (!isListening) {
            return;
        }
        try {
            String fileContent = new String(Files.readAllBytes(filePath));
            FileChange fileChange = new FileChange(filePath.toString(), fileContent, FileChange.ChangeType.ADD);
            VersionStorage.saveVersion(Map.of(filePath.toString(), fileChange)); // 保存版本
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleFileDelete(Path filePath) {
        if (!isListening) {
            return;
        }
        FileChange fileChange = new FileChange(filePath.toString(), "", FileChange.ChangeType.DELETE);
        VersionStorage.saveVersion(Map.of(filePath.toString(), fileChange)); // 保存版本
    }

    public void pauseListening() {
        isListening = false;
    }

    public void enableListening() {
        isListening = true;
    }
}
