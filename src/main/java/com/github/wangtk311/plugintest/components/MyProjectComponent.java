package com.github.wangtk311.plugintest.components;

import com.github.wangtk311.plugintest.listeners.MyDocumentListener;
import com.github.wangtk311.plugintest.services.VersionStorage;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class MyProjectComponent implements ProjectComponent {
    private final Project project;

    public MyProjectComponent(Project project) {
        this.project = project;
    }

    @Override
    public void projectOpened() {
        // 检查是否有版本历史文件，如果有则加载
        File versionFile = new File(VersionStorage.VERSION_STORAGE_FILE);
        if (versionFile.exists()) {
            VersionStorage.loadVersionsFromDisk();  // 加载之前保存的版本
        }

        // 为每个打开的编辑器添加监听器
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorCreated(@NotNull EditorFactoryEvent event) {
                event.getEditor().getDocument().addDocumentListener(new MyDocumentListener(project));
            }
        }, project);
    }
}
