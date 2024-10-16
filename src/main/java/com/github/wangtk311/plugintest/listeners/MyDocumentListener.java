package com.github.wangtk311.plugintest.listeners;

import com.github.wangtk311.plugintest.services.VersionStorage;
import com.github.wangtk311.plugintest.toolWindow.VersionToolWindowFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

import java.util.HashMap;
import java.util.Map;

public class MyDocumentListener implements DocumentListener {
    private final Project project;

    public MyDocumentListener(Project project) {
        this.project = project;
    }

    @Override
    public void documentChanged(DocumentEvent event) {
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

    private boolean isSemicolonChanged(String newText, String oldText) {
        return (newText.contains(";") && !oldText.contains(";")) || (!newText.contains(";") && oldText.contains(";"));
    }

    private void saveProjectVersion() {
        Map<String, String> fileContents = new HashMap<>();
        // 使用 FileEditorManager 获取当前项目中所有已打开的文件
        for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
            String content = FileDocumentManager.getInstance().getDocument(file).getText();
            fileContents.put(file.getPath(), content);  // 使用文件路径作为key，内容作为value
        }
        // 保存项目版本
        VersionStorage.saveProjectVersion(fileContents);
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
