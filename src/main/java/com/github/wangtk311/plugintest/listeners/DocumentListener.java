package com.github.wangtk311.plugintest.listeners;

import com.github.wangtk311.plugintest.services.FileChange;
import com.github.wangtk311.plugintest.services.VersionStorage;
import com.github.wangtk311.plugintest.toolWindow.VersionToolWindowFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class DocumentListener implements com.intellij.openapi.editor.event.DocumentListener {
    private final Project project;
    private final Map<String, String> lastFileContentMap = new HashMap<>();

    public DocumentListener(Project project) {
        this.project = project;
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        // 检查是否为添加或删除分号
        String newText = event.getNewFragment().toString();
        String oldText = event.getOldFragment().toString();

        if (isSemicolonChanged(newText, oldText)) {
            // 只有涉及到分号的修改时，才保存整个项目的版本
            saveProjectVersion();

            // 保存版本后，立即刷新 ToolWindow
            refreshToolWindow();
        }
    }

    private boolean isSemicolonChanged(@NotNull String newText, String oldText) {
        return (newText.contains(";") && !oldText.contains(";")) || (!newText.contains(";") && oldText.contains(";"));
    }

    private void saveProjectVersion() {
        Map<String, FileChange> fileChanges = new HashMap<>();

        // 使用 FileEditorManager 获取当前打开的文件，因为发生粒度变化的文件只可能是当前打开的文件
        VirtualFile file = FileEditorManager.getInstance(project).getSelectedEditor().getFile();
            Document document = FileDocumentManager.getInstance().getDocument(file);

            String filePath = file.getPath();
            String currentContent = document.getText();

            // 处理文件的新增、删除或修改
            FileChange.ChangeType changeType = FileChange.ChangeType.MODIFY;

            // 保存文件变化（包括新增、删除、修改）
            FileChange fileChange = new FileChange(filePath, currentContent, changeType);
            fileChanges.put(filePath, fileChange);

            // 更新最后一次文件内容的记录
            lastFileContentMap.put(filePath, currentContent);


        // 保存项目的文件变化到版本存储中
        VersionStorage.saveVersion(fileChanges);
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
