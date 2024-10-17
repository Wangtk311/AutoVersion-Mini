package com.github.wangtk311.plugintest.components;

import com.github.wangtk311.plugintest.listeners.MyDocumentListener;
import com.github.wangtk311.plugintest.services.FileChange;
import com.github.wangtk311.plugintest.services.VersionStorage;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class MyProjectComponent implements ProjectComponent {
    private final Project project;

    public MyProjectComponent(Project project) {
        this.project = project;
    }

    @Override
    public void projectOpened() {
        // 检查是否有版本历史文件，如果有则加载
        VersionStorage.VERSION_STORAGE_FILE = project.getBasePath() + "/autoversion.record.bin";
        System.out.println("AutoVersion History file: " + VersionStorage.VERSION_STORAGE_FILE);
        File versionFile = new File(VersionStorage.VERSION_STORAGE_FILE);
        if (versionFile.exists()) {
            VersionStorage.loadVersionsFromDisk();  // 加载之前保存的版本
        }
        else { // 没有历史文件，说明是第一次打开项目，保存当前项目中的所有文件作为第一版，除了autoversion.record.bin记录文件
            Map<String, FileChange> fileChanges = new HashMap<>();
            Path projectRoot = Paths.get(project.getBasePath());

            // 使用 Files.walk 递归遍历项目目录及其子目录中的所有文件
            try {
                Files.walk(projectRoot).forEach(path -> {
                    File file = path.toFile();
                    if (file.isFile() && !file.getName().equals("autoversion.record.bin")) {
                        try {
                            String filePath = file.getCanonicalPath(); // 获取文件的绝对路径
                            // 将文件的路径和 FileChange 实例放入 map
                            fileChanges.put(filePath, new FileChange(filePath, new String(Files.readAllBytes(path)), FileChange.ChangeType.ADD));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            VersionStorage.saveVersion(fileChanges);
            VersionStorage.saveVersionsToDisk();
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
