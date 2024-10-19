package com.github.wangtk311.plugintest.components;

import com.github.wangtk311.plugintest.toolWindow.VersionToolWindowFactory;
import com.github.wangtk311.plugintest.listeners.DocumentListener;
import com.github.wangtk311.plugintest.listeners.FileSystemListener; // 引入新添加的监听器
import com.github.wangtk311.plugintest.services.FileChange;
import com.github.wangtk311.plugintest.services.VersionStorage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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

    @Override
    public void projectOpened() {
        System.out.println("Initializing plugin, please wait...");

        // 检查当前目录是否是git仓库，如果不是则创建一个，在gitignore的文件内容最后添加autoversion.record.bin和autoversion.map.bin
        if (!isGitRepository()) {
            // 使用 ProgressManager 创建一个后台任务
            ProgressManager.getInstance().run(new Task.Modal(project, "Initializing Git Repository", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        // 执行 git init 命令并等待完成
                        Process process = Runtime.getRuntime().exec("git init", null, new File(project.getBasePath()));
                        process.waitFor();

                        // 在主线程中执行 write action 以确保 .gitignore 文件修改在正确的上下文中进行
                        ApplicationManager.getApplication().invokeAndWait(() -> {
                            ApplicationManager.getApplication().runWriteAction(() -> {
                                try {
                                    VirtualFile gitIgnore = project.getBaseDir().findChild(".gitignore");
                                    // 如果存在 .gitignore 文件，且其中不包含这两个排除文件，将 autoversion.record.bin 和 autoversion.map.bin 添加到文件末尾
                                    if (gitIgnore != null) {
                                        if (new String(gitIgnore.contentsToByteArray()).contains("autoversion.record.bin") &&
                                                new String(gitIgnore.contentsToByteArray()).contains("autoversion.map.bin")) {
                                            return;
                                        }
                                        else {
                                            Files.write(Paths.get(project.getBasePath() + "/.gitignore"), "\nautoversion.record.bin\nautoversion.map.bin".getBytes(), java.nio.file.StandardOpenOption.APPEND);
                                        }
                                    } else {
                                        // 如果不存在 .gitignore 文件，创建一个新的文件
                                        Files.write(Paths.get(project.getBasePath() + "/.gitignore"), "autoversion.record.bin\nautoversion.map.bin".getBytes());
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                        });

                        Process process2 = Runtime.getRuntime().exec("git add .", null, new File(project.getBasePath()));
                        process2.waitFor();
                        Process process3 = Runtime.getRuntime().exec("git commit -m \"V1.0\"", null, new File(project.getBasePath()));
                        process3.waitFor();


                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            System.out.println("Git repo initialized.");
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
        System.out.println("Loading versions...");
        if (versionFile.exists()) {
            VersionStorage.loadVersionsFromDisk();
        } else {
            // 初始化保存当前项目状态为第一版
            Map<String, FileChange> fileChanges = new HashMap<>();
            Path projectRoot = Paths.get(project.getBasePath());

            // 递归遍历项目目录中的文件，排除autoversion.record.bin 文件和 autoversion.map.bin 文件，以及gitignore、gitattributes和.git文件夹下的文件
            try {
                Files.walk(projectRoot).forEach(path -> {
                    File file = path.toFile();
                    String fileName = file.getName();
                    if (file.isFile() && !fileName.equals("autoversion.record.bin") &&
                            !fileName.equals("autoversion.map.bin") &&
                            !fileName.equals(".gitignore") &&
                            !fileName.equals(".gitattributes") &&
                            !filePathContainsGitFolder(path)) {
                        try {
                            String filePath = file.getPath();
                            // 替换所有的反斜杠为正斜杠
                            filePath = filePath.replace("\\", "/");
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

        // 初始化文件系统监听器
        fileSystemListener = new FileSystemListener(project);
        System.out.println("FileSystemListener initialized: " + fileSystemListener);

        // 添加监听器到文档监听器组
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorCreated(@NotNull EditorFactoryEvent event) {
                DocumentListener documentListener = new DocumentListener(project);
                event.getEditor().getDocument().addDocumentListener(documentListener);
                documentListeners.add(documentListener);
            }
        }, project);

        System.out.println("Plugin initialized.");
        refreshToolWindow();
    }

    // 检查当前目录是否是git仓库
    public boolean isGitRepository() {
        File gitDir = new File(project.getBasePath() + "/.git");
        return gitDir.exists() && gitDir.isDirectory();
    }

    // 判断是否属于 .git 文件夹中的文件
    private boolean filePathContainsGitFolder(Path path) {
        return path.toString().contains(File.separator + ".git" + File.separator);
    }

    private void refreshToolWindow() {
        // 获取 ToolWindow 并刷新内容
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("AutoVersion Mini");
        if (toolWindow != null) {
            toolWindow.getContentManager().removeAllContents(true);  // 清除旧内容
            VersionToolWindowFactory.getInstance(project).createToolWindowContent(project, toolWindow);  // 重新加载内容
        }
    }
}
