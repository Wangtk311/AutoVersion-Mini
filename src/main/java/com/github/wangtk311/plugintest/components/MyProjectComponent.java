package com.github.wangtk311.plugintest.components;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.github.wangtk311.plugintest.listeners.DocumentListener;
import com.github.wangtk311.plugintest.listeners.FileSystemListener; // 引入新添加的监听器
import com.github.wangtk311.plugintest.services.FileChange;
import com.github.wangtk311.plugintest.services.VersionStorage;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class MyProjectComponent implements ProjectComponent {
    private final Project project;
    public FileSystemListener fileSystemListener; // 新增文件系统监听器
    public ArrayList<DocumentListener> documentListeners = new ArrayList<>(); // 新增文档监听器组
    private static MyProjectComponent myProjectComponent; // 单例

    private MyProjectComponent(Project project) {
        this.project = project;
        myProjectComponent = this;
    }

    public static MyProjectComponent getInstance(Project project) {
        if (myProjectComponent == null) {
            System.out.println("MyProjectComponent initialized");
            myProjectComponent = new MyProjectComponent(project);
        }
        return myProjectComponent;
    }

    private  List<String> convertStringToList(String str) {//------------------------xinjian-------------------------------------------------------------------------------------------------------------
        // 使用换行符分割字符串
        String[] lines = str.split("\n");

        // 将数组转换为 List
        return new ArrayList<>(Arrays.asList(lines));
    }


    @Override
    public void projectOpened() {
        // 打开项目时关闭所有打开的编辑器
        FileEditorManager editorManager = FileEditorManager.getInstance(project);
        FileEditor[] editors =  editorManager.getAllEditors();
        for (FileEditor editor : editors) {
            editorManager.closeFile(editor.getFile());
        }

        VersionStorage.VERSION_STORAGE_FILE = project.getBasePath() + "/autoversion.record.bin";
        System.out.println("AutoVersion History file: " + VersionStorage.VERSION_STORAGE_FILE);
        File versionFile = new File(VersionStorage.VERSION_STORAGE_FILE);

        VersionStorage.VERSION_MAP_FILE = project.getBasePath() + "/autoversion.map.bin";
        System.out.println("AutoVersion Map file: " + VersionStorage.VERSION_MAP_FILE);
        File versionMapFile = new File(VersionStorage.VERSION_MAP_FILE);

        // 加载大小版本映射
        if (versionMapFile.exists()) {
            VersionStorage.loadMapFromDisk();
        } else {
            try {
                Files.createFile(versionMapFile.toPath());
                Map<Integer, Integer> majorToMinorVersionMap = new HashMap<>();
                majorToMinorVersionMap.put(1, 0);
                VersionStorage.saveMapToDisk();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // 加载历史版本
        if (versionFile.exists()) {
            VersionStorage.loadVersionsFromDisk();
        } else {
            // 初始化保存当前项目状态为第一版
            Map<String, FileChange> fileChanges = new HashMap<>();
            Path projectRoot = Paths.get(project.getBasePath());


            // 递归遍历项目目录中的文件
            try {
                Files.walk(projectRoot).forEach(path -> {
                    File file = path.toFile();
                    if (file.isFile() && !file.getName().equals("autoversion.record.bin")) {
                        try {
                            String filePath = file.getPath();
                            // 替换所有的反斜杠为正斜杠
                            filePath = filePath.replace("\\", "/");
                            List<String> emptyList = Collections.emptyList();
                            List<String> Filecontent =Files.readAllLines(Paths.get(filePath));
                            Patch<String> patch = DiffUtils.diff(emptyList, Filecontent);
                            fileChanges.put(filePath, new FileChange(filePath, patch, FileChange.ChangeType.ADD));//--------------------------------------------------------------------------------------------------------------------------------
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

        // 初始化文件系统监听器
        fileSystemListener = new FileSystemListener(project);
        System.out.println("FileSystemListener initialized: " + fileSystemListener);

        // 为每个打开的编辑器添加监听器到文档监听器组
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorCreated(@NotNull EditorFactoryEvent event) {
                DocumentListener documentListener = new DocumentListener(project);
                event.getEditor().getDocument().addDocumentListener(documentListener);
                documentListeners.add(documentListener);
            }
        }, project);

        System.out.println("Plugin initialized.");
    }
}
