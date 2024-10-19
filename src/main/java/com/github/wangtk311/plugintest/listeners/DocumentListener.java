package com.github.wangtk311.plugintest.listeners;

import com.github.difflib.patch.Patch;
import com.github.wangtk311.plugintest.services.FileChange;
import com.github.wangtk311.plugintest.services.VersionStorage;
import com.github.wangtk311.plugintest.toolWindow.VersionToolWindowFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.difflib.*;
import org.jetbrains.annotations.NotNull;


import java.util.*;

public class DocumentListener implements com.intellij.openapi.editor.event.DocumentListener {
    private final Project project;
    public static boolean isListening = false;
    public static int versionIndex;

    private Timer timer = new Timer(); // 计时器实例
    boolean Timerstatus = false;
    private static final long DELAY = 3000; // 延迟时间（毫秒）

    private String oldFilecontent = "";
    private boolean first = true;

    public DocumentListener(Project project) {
        this.project = project;
        enableListening();
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        if (!isListening) {
            return;
        }
        VirtualFile currentFile = getCurrentFile();
        String filePath = getCurrentFilePath(currentFile);
        if (first) {
            System.out.println("Find last version.");
            // 找到最后一个包含当前追踪文件变动且不是DELETE的版本
            for (int i = 0; i < VersionStorage.getProjectVersions().size(); i++) {
                Map<String, FileChange> version = VersionStorage.getProjectVersions().get(i);
                if (version.containsKey(filePath) && version.get(filePath).getChangeType() != FileChange.ChangeType.DELETE) {
                    versionIndex = i;
                }
            }
            System.out.println("versionIndex: " + versionIndex);

            Map<String, FileChange> versionContents = VersionStorage.getVersion(versionIndex);
            System.out.println("versionContents: " + versionContents);
            // 打印当前版本的所有文件路径
            for (String key : versionContents.keySet()) {
                System.out.println("Version file paths: " + key);
            }
            FileChange filechange = versionContents.get(filePath);
            // 打印当前版本的文件内容
            System.out.println("filechange: " + filechange);
            String filecontent = filechange.getFileContent();
            // 打印当前版本的文件内容
            System.out.println("oldfilecontent: " + filecontent);
            // 写入oldFilecontent
            oldFilecontent = filecontent;

            first = false;
        }

        String currentFilecontent = getCurrentFileContent(currentFile);
        System.out.println("nowfile: \n" + currentFilecontent);

        timer.cancel();
        Timerstatus=false;
        try {
            System.out.println("hasSignificantChanges");
            //if (hasChanges(filecontent, currentFilecontent)) {
            if (hasChanges(oldFilecontent, currentFilecontent)) {
                timer = new Timer();  // 必须重新创建 Timer 实例
                Timerstatus=true;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        // 创建新的任务
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                ApplicationManager.getApplication().invokeLater(()->{
                    saveProjectVersion();
                    refreshToolWindow();

                    List<String> OldFilecontent = convertStringToList(oldFilecontent);
                    List<String> CurrentFilecontent = convertStringToList(currentFilecontent);
                    System.out.println("OldFilecontent:\n " + OldFilecontent);
                    System.out.println("CurrentFilecontent: \n" + CurrentFilecontent);
                    // 比较两个文本文件
                    Patch<String> patch = DiffUtils.diff(OldFilecontent, CurrentFilecontent);
                    // 输出差异
                    patch.getDeltas().forEach(delta->{
                        System.out.println(delta);
                    });

                    oldFilecontent = getCurrentFileContent(currentFile);
                    System.out.println("Version index: " + (versionIndex + 1));
                });
            }
        };

        // 启动计时器任务，在指定延迟后执行
        if(Timerstatus)
            timer.schedule(task, DELAY);
    }

    private VirtualFile getCurrentFile() {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        var currentEditor = fileEditorManager.getSelectedEditor();
        VirtualFile currentFile = currentEditor.getFile();
        //String currentFilecontent = FileDocumentManager.getInstance().getDocument(currentFile).getText();
        return currentFile;
    }

    private String getCurrentFileContent(VirtualFile currentFile) {
        String currentFilecontent = FileDocumentManager.getInstance().getDocument(currentFile).getText();
        return currentFilecontent;
    }

    private String getCurrentFilePath(VirtualFile currentFile) {
        String filePath = currentFile.getPath();
        return filePath;
    }

    private boolean hasChanges(String oldFile, String newFile) throws Exception {
        CompilationUnit oldCU = new JavaParser().parse(oldFile).getResult().orElse(null);
        CompilationUnit newCU = new JavaParser().parse(newFile).getResult().orElse(null);

        List<ClassOrInterfaceDeclaration> oldClasses = oldCU.findAll(ClassOrInterfaceDeclaration.class);
        List<ClassOrInterfaceDeclaration> newClasses = newCU.findAll(ClassOrInterfaceDeclaration.class);

        // 检查是否有新类的声明
        if (newClasses.size() > oldClasses.size()) {
            return true;  // 新增类，记录变更
        }

        for (ClassOrInterfaceDeclaration oldClass : oldClasses) {

            var NewClass = newClasses.stream()
                    .filter(newClass -> newClass.getNameAsString().equals(oldClass.getNameAsString()))
                    .findFirst();

            if (NewClass.isPresent()) {
                List<String> oldFields = oldClass.getFields().stream()
                        .map(f -> f.getVariable(0).getNameAsString())
                        .toList();
                List<String> newFields = NewClass
                        .get()
                        .getFields().stream()
                        .map(f -> f.getVariable(0).getNameAsString())
                        .toList();

                if (!newFields.equals(oldFields)) {
                    System.out.println("成员变量发生变化，记录变更");
                    return true;  // 成员变量变化，记录变更
                }

                // 获取旧类和新类的成员函数签名列表
                List<String> oldMethods = oldClass.getMethods().stream()
                        .map(MethodDeclaration::getDeclarationAsString) // 只获取签名部分
                        .toList();
                List<String> newMethods = NewClass.get().getMethods().stream()
                        .map(MethodDeclaration::getDeclarationAsString)
                        .toList();

                // 检查新增的成员函数
                List<String> addedMethods = newMethods.stream()
                        .filter(method -> !oldMethods.contains(method))
                        .toList();
                if (!addedMethods.isEmpty()) {
                    System.out.println("新增的成员函数: " + addedMethods);
                    return true;  // 记录变更
                }

                // 检查删除的成员函数
                List<String> removedMethods = oldMethods.stream()
                        .filter(method -> !newMethods.contains(method))
                        .toList();
                if (!removedMethods.isEmpty()) {
                    System.out.println("删除的成员函数: " + removedMethods);
                    return true;  // 记录变更
                }


                if (!newMethods.equals(oldMethods)) {
                    System.out.println("成员函数发生变化，记录变更");
                    return true;
                }
                List<MethodDeclaration> oldMethodsContents = oldClass.getMethods();
                List<MethodDeclaration> newMethodsContents = NewClass.get().getMethods();

                // 遍历旧类的方法，检查新类中对应的方法体是否有显著变化
                for (MethodDeclaration OldMethod : oldMethodsContents) {

                    var NewMethod = newMethodsContents.stream()
                            .filter(newMethod -> newMethod.getDeclarationAsString()
                                    .equals(OldMethod.getDeclarationAsString())) // 签名匹配
                            .findFirst();
                    if (NewMethod.isPresent()) {
                        int oldStatementCount = countStatements(OldMethod);
                        int newStatementCount = countStatements(NewMethod.get());

                        if (Math.abs(newStatementCount - oldStatementCount) >= 2) {
                            System.out.println("方法体发生显著变化: " + OldMethod.getNameAsString());
                            System.out.println("旧语句数: " + oldStatementCount + ", 新语句数: " + newStatementCount);
                            return true;
                        }
                    }
                }

            }

        }

        return false;  // 如果没有重要变更
    }

    private void saveProjectVersion() {
        if (!isListening) {
            return;
        }
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


        // 保存项目的文件变化到版本存储中
        VersionStorage.saveVersion(fileChanges);
    }

    // 计算方法中的语句数量
    private int countStatements(MethodDeclaration method) {
        return method.getBody()
                .map(body -> body.getStatements().size()) // 获取语句数量
                .orElse(0); // 如果方法体为空，返回0
    }

    private void refreshToolWindow() {
        // 获取 ToolWindow 并刷新内容
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("AutoVersion Mini");
        if (toolWindow != null) {
            toolWindow.getContentManager().removeAllContents(true);  // 清除旧内容
            new VersionToolWindowFactory().createToolWindowContent(project, toolWindow);  // 重新加载内容
        }
    }

    private static List<String> convertStringToList(String str) {
        // 使用换行符分割字符串
        String[] lines = str.split("\n");

        // 将数组转换为 List
        return new ArrayList<>(Arrays.asList(lines));
    }

    public void enableListening() {
        isListening = true;
    }

    public void pauseListening() {
        isListening = false;
    }
}
