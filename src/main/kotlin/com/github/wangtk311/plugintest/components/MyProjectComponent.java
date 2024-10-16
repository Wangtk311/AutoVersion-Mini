package com.github.wangtk311.plugintest.components;

import com.github.wangtk311.plugintest.listeners.MyDocumentListener;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class MyProjectComponent implements ProjectComponent {
    private final Project project;

    public MyProjectComponent(Project project) {
        this.project = project;
    }

    @Override
    public void projectOpened() {
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorCreated(@NotNull EditorFactoryEvent event) {
                // 为每个打开的编辑器添加监听器
                event.getEditor().getDocument().addDocumentListener(new MyDocumentListener(project));
            }
        }, project);
    }
}
