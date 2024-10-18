package com.github.wangtk311.plugintest.listeners;

import com.github.wangtk311.plugintest.services.VersionStorage;
import com.github.wangtk311.plugintest.toolWindow.VersionToolWindowFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public class MyDocumentListener implements DocumentListener {
    private final Project project;

    public MyDocumentListener(Project project) {
        this.project = project;
    }

    static int i = 1;
    static int versionIndex = 0;

    private Timer timer = new Timer(); // 计时器实例
    boolean Timerstatus = false;
    private static final long DELAY = 3000; // 延迟时间（毫秒）


    @Override
    public void documentChanged(DocumentEvent event) {
//        // 检查是否为添加或删除分号
//        String newText = event.getNewFragment().toString();
//        String oldText = event.getOldFragment().toString();
//
//        //if (isSemicolonChanged(newText, oldText)) {
//

        if (i == 1) {
            saveProjectVersion();
            // 保存版本后，立即刷新 ToolWindow
            refreshToolWindow();
            i = i - 1;
        }


//        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
//        var currentEditor = fileEditorManager.getSelectedEditor();

        VirtualFile currentFile = getCurrentFile();
        String currentFilecontent = getCurrentFileContent(currentFile);
        String filePath = getCurrentFilePath(currentFile);

        System.out.println("filePath: " + filePath);
        System.out.println("nowfile: " + currentFilecontent);


        Map<String, String> versionContents = VersionStorage.getVersion(versionIndex);
        String filecontent = versionContents.get(filePath);
        System.out.println("filecontent: " + filecontent);


        timer.cancel();
        Timerstatus=false;
        try {
            System.out.println("hasSignificantChanges");
            if (hasChanges(filecontent, currentFilecontent)) {


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
                ApplicationManager.getApplication().invokeLater(() -> {
                    saveProjectVersion();
                    refreshToolWindow();
                    versionIndex++;
                    System.out.println("Version index: " + (versionIndex + 1));
                });
            }
        };

        // 启动计时器任务，在指定延迟后执行
        if(Timerstatus)
            timer.schedule(task, DELAY);

//            try {
//                System.out.println("hasSignificantChanges");
//                if(hasSignificantChanges(filecontent,currentFilecontent)){
//                    saveProjectVersion();
//                    refreshToolWindow();
//                    versionIndex++;
//                }
//                System.out.println("versionIndex: " + (versionIndex+1));
//
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }


    }

    private VirtualFile getCurrentFile() {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        var currentEditor = fileEditorManager.getSelectedEditor();
        VirtualFile currentFile = currentEditor.getFile();
        String currentFilecontent = FileDocumentManager.getInstance().getDocument(currentFile).getText();
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
//        for (ClassOrInterfaceDeclaration oldClass : oldClasses) {
//            newClasses.stream()
//                    .filter(newClass -> newClass.getNameAsString().equals(oldClass.getNameAsString()))
//                    .findFirst()
//                    .ifPresent(newClass -> {
//                        List<String> oldFields = oldClass.getFields().stream()
//                                .map(f -> f.getVariable(0).getNameAsString())
//                                .toList();
//                        List<String> newFields = newClass.getFields().stream()
//                                .map(f -> f.getVariable(0).getNameAsString())
//                                .toList();
//
//                        if (!newFields.equals(oldFields)) {
//                            System.out.println("成员变量发生变化，记录变更");
//
//                            saveProjectVersion();
//                            // 保存版本后，立即刷新 ToolWindow
//                            refreshToolWindow();
//                            versionIndex++;
//
//                        }
//                    });
//        }
        // 检查成员变量是否发生变化
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
//        for (ClassOrInterfaceDeclaration oldClass : oldClasses) {
//            newClasses.stream()
//                    .filter(newClass -> newClass.getNameAsString().equals(oldClass.getNameAsString()))
//                    .findFirst()
//                    .ifPresent(newClass -> {
//                        List<String> oldFields = oldClass.getFields().stream()
//                                .map(f -> f.getVariable(0).getNameAsString())
//                                .toList();
//                        List<String> newFields = newClass.getFields().stream()
//                                .map(f -> f.getVariable(0).getNameAsString())
//                                .toList();
//
//                        // 检查是否有新增的成员变量
//                        List<String> addedFields = newFields.stream()
//                                .filter(field -> !oldFields.contains(field))
//                                .toList();
//
//                        // 检查是否有删除的成员变量
//                        List<String> removedFields = oldFields.stream()
//                                .filter(field -> !newFields.contains(field))
//                                .toList();
//
//                        // 输出新增或删除的成员变量
//                        if (!addedFields.isEmpty()) {
//                            System.out.println("新增的成员变量: " + addedFields);
//                        }
//
//                        if (!removedFields.isEmpty()) {
//                            System.out.println("删除的成员变量: " + removedFields);
//                        }
//
//                        // 也可以继续比较字段是否发生变化
//                        if (!newFields.equals(oldFields)) {
//                            System.out.println("成员变量发生变化，记录变更");
//                            saveProjectVersion();
//                            // 保存版本后，立即刷新 ToolWindow
//                            refreshToolWindow();
//                            versionIndex++;
//                        }
//                    });
//        }
        // 进一步检查类中的方法、成员变量变更...
        // (可以在此扩展检测逻辑)

        return false;  // 如果没有重要变更
    }

    // 计算方法中的语句数量
    private int countStatements(MethodDeclaration method) {
        return method.getBody()
                .map(body -> body.getStatements().size()) // 获取语句数量
                .orElse(0); // 如果方法体为空，返回0
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
