package com.github.wangtk311.plugintest.listeners;

import com.github.wangtk311.plugintest.services.FileChange;
import com.github.wangtk311.plugintest.services.VersionStorage;
import com.github.wangtk311.plugintest.toolWindow.VersionToolWindowFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

import java.io.IOException;
import java.util.Map;

public class FileSystemListener extends VirtualFileAdapter {

    public static boolean isListening = false;
    private final Project project;
    private static final String IGNORED_FILE = "autoversion.record.bin";

    public FileSystemListener(Project project) {
        this.project = project;
        enableListening();
        startListening();
    }

    public void startListening() {
        if (!isListening) {
            return;
        }
        // 注册监听器
        VirtualFileManager.getInstance().addVirtualFileListener(this);
    }

    @Override
    public void fileCreated(VirtualFileEvent event) {
        if (!isListening) {
            return;
        }
        VirtualFile file = event.getFile();
        // 忽略 autoversion.record.bin 文件
        if (IGNORED_FILE.equals(file.getName())) {
            return;
        }
        handleFileCreate(file);
    }

    @Override
    public void fileDeleted(VirtualFileEvent event) {
        if (!isListening) {
            return;
        }
        VirtualFile file = event.getFile();
        // 忽略 autoversion.record.bin 文件
        if (IGNORED_FILE.equals(file.getName())) {
            return;
        }
        handleFileDelete(file);
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
        if (!isListening) {
            return;
        }
        VirtualFile file = event.getFile();
        // 忽略 autoversion.record.bin 文件
        if (IGNORED_FILE.equals(file.getName())) {
            return;
        }
        handleFileMove(event.getOldParent().getPath(), file);
    }

    @Override
    public void propertyChanged(VirtualFilePropertyEvent event) {
        if (!isListening) {
            return;
        }
        // 处理文件重命名事件
        if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
            VirtualFile file = event.getFile();
            // 忽略 autoversion.record.bin 文件
            if (IGNORED_FILE.equals(file.getName())) {
                return;
            }
            handleFileRename(event.getOldValue().toString(), file);
        }
    }

    private void handleFileCreate(VirtualFile file) {
        if (file.isDirectory()) {
            return;
        }
        try {
            // 获取文件内容
            String fileContent = new String(file.contentsToByteArray());
            FileChange fileChange = new FileChange(file.getPath(), fileContent, FileChange.ChangeType.ADD);
            VersionStorage.saveVersion(Map.of(file.getPath(), fileChange)); // 保存版本
            refreshToolWindow(); // 刷新 ToolWindow
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleFileDelete(VirtualFile file) {
        if (file.isDirectory()) {
            return;
        }
        FileChange fileChange = new FileChange(file.getPath(), "", FileChange.ChangeType.DELETE);
        VersionStorage.saveVersion(Map.of(file.getPath(), fileChange)); // 保存版本
        refreshToolWindow(); // 刷新 ToolWindow
    }

    private void handleFileRename(String oldName, VirtualFile file) {
        if (file.isDirectory()) {
            return;
        }
        // 处理文件重命名，视为删除旧文件并创建新文件
        handleFileDelete(file);
        handleFileCreate(file);
    }

    private void handleFileMove(String oldParentPath, VirtualFile file) {
        if (file.isDirectory()) {
            return;
        }
        // 处理文件移动，视为删除旧文件并创建新文件
        String oldFilePath = oldParentPath + "/" + file.getName();
        handleFileDelete(file); // 删除旧文件
        handleFileCreate(file); // 创建新文件
    }

    public void pauseListening() {
        isListening = false;
    }

    public void enableListening() {
        isListening = true;
    }

    private void refreshToolWindow() {
        // 获取 ToolWindow 并刷新内容
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("AutoVersion Mini");
        if (toolWindow != null) {
            toolWindow.getContentManager().removeAllContents(true);  // 清除旧内容
            new VersionToolWindowFactory().createToolWindowContent(project, toolWindow);  // 重新加载内容
        }
    }
}
