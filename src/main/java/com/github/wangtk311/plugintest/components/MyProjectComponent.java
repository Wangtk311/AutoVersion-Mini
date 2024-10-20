package com.github.wangtk311.plugintest.components;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;

import com.github.wangtk311.plugintest.listeners.DocumentListener;
import com.github.wangtk311.plugintest.listeners.FileSystemListener; // 引入新添加的监听器
import com.github.wangtk311.plugintest.services.FileChange;
import com.github.wangtk311.plugintest.services.VersionStorage;
import com.github.wangtk311.plugintest.toolWindow.VersionToolWindowFactory;
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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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


    @Override
    public void projectOpened() {
        //***********************************************************************
        // 打开项目时关闭所有打开的编辑器
        FileEditorManager editorManager = FileEditorManager.getInstance(project);
        FileEditor[] editors =  editorManager.getAllEditors();
        for (FileEditor editor : editors) {
            editorManager.closeFile(editor.getFile());
        }
        //***********************************************************************已删除


        //***********************************************************************新增*****************************
        System.out.println("Initializing plugin, please wait...");

        if (!isGitRepository()) {
            ProgressManager.getInstance().run(new Task.Modal(project, "Initializing Git Repository", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        // 执行 git init 命令并等待完成
                        ProcessBuilder initProcessBuilder = new ProcessBuilder("git", "init");
                        initProcessBuilder.directory(new File(project.getBasePath().replace("\\", "/")));
                        Process initProcess = initProcessBuilder.start();
                        int exitCode = initProcess.waitFor();

                        if (exitCode != 0) {
                            System.err.println("git init failed with exit code: " + exitCode);
                        }

                        // 在主线程中执行 write action
                        ApplicationManager.getApplication().invokeAndWait(() -> {
                            ApplicationManager.getApplication().runWriteAction(() -> {
                                try {
                                    // 尝试获取并创建 .gitignore 文件
                                    VirtualFile gitIgnore = project.getBaseDir().findChild(".gitignore");
                                    String toAppend = "\nautoversion.record.bin\nautoversion.map.bin\n";
                                    String existingContent = "";

                                    if (gitIgnore != null && gitIgnore.isValid()) {
                                        // 如果文件已存在，读取其内容
                                        existingContent = new String(gitIgnore.contentsToByteArray());
                                        System.out.println("Exist .gitignore:");
                                        System.out.println(gitIgnore.getPath());
                                        System.out.println(existingContent);
                                    } else {
                                        // 在项目根目录创建新的 .gitignore 文件，不使用 VirtualFile 的 createChildData 方法
                                        File newGitIgnore = new File(project.getBasePath().replace("\\", "/") + "/.gitignore");
                                        Files.createFile(newGitIgnore.toPath());
                                        gitIgnore = project.getBaseDir().findChild(".gitignore");

                                        System.out.println("Create new .gitignore file");
                                        if (gitIgnore != null) {
                                            System.out.println("New .gitignore:");
                                            System.out.println(gitIgnore.getPath());
                                        } else {
                                            System.err.println("Failed to create .gitignore file");
                                        }
                                    }

                                    // 检查 .gitignore 中是否已包含要添加的文件
                                    if (!existingContent.contains("autoversion.record.bin") || !existingContent.contains("autoversion.map.bin")) {
                                        // 写入内容
                                        Files.write(Paths.get(gitIgnore.getPath().replace("\\", "/")),
                                                toAppend.getBytes(),
                                                StandardOpenOption.APPEND);
                                        System.out.println("Appended to .gitignore.");
                                    } else {
                                        System.out.println("gitignore contains autoversion.record.bin and autoversion.map.bin, skip adding.");
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace(); // 更好地处理异常
                                }
                            });
                        });

                        // 添加和提交
                        ProcessBuilder addProcessBuilder = new ProcessBuilder("git", "add", ".");
                        addProcessBuilder.directory(new File(project.getBasePath().replace("\\", "/")));
                        Process addProcess = addProcessBuilder.start();
                        addProcess.waitFor();

                        ProcessBuilder commitProcessBuilder = new ProcessBuilder("git", "commit", "-m", "V1.0");
                        commitProcessBuilder.directory(new File(project.getBasePath().replace("\\", "/")));
                        Process commitProcess = commitProcessBuilder.start();
                        commitProcess.waitFor();

                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
            });
            System.out.println("Git repo initialized.");
        }




        //***********************************************************************



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

//******************************************************************
            // 递归遍历项目目录中的文件
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
                            List<String> emptyList = Collections.emptyList();
                            List<String> Filecontent =Files.readAllLines(Paths.get(filePath));
                            Patch<String> patch = DiffUtils.diff(emptyList, Filecontent);
                            fileChanges.put(filePath, new FileChange(filePath, patch, FileChange.ChangeType.ADD));//-------------------------------------
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
                DocumentListener documentListener = new DocumentListener(project, event.getEditor().getDocument());
                event.getEditor().getDocument().addDocumentListener(documentListener);
                documentListeners.add(documentListener);
            }
            //************************************************
            @Override
            public void editorReleased(@NotNull EditorFactoryEvent event) {
                // 从文档监听器组documentlisteners中移除监听器,并removedocumentlistener移除文档监听器
                for (DocumentListener documentListener : documentListeners) {
                    if (documentListener.getDocument().equals(event.getEditor().getDocument())) {
                        event.getEditor().getDocument().removeDocumentListener(documentListener);
                        documentListeners.remove(documentListener);
                        break;
                    }
                }
            }
//************************************************
        }, project);

        System.out.println("Plugin initialized.");
        refreshToolWindow();
    }


    //***********************************************************************新增*****************************
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
    //****************************************************************************************************



}
