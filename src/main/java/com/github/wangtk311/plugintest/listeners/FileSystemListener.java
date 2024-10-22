package com.github.wangtk311.plugintest.listeners;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.*;
import com.github.wangtk311.plugintest.services.FileChange;
import com.github.wangtk311.plugintest.services.VersionStorage;
import com.github.wangtk311.plugintest.toolWindow.VersionToolWindowFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class FileSystemListener extends VirtualFileAdapter {

    public static boolean isListening = false;
    private final Project project;


    public FileSystemListener(Project project) {
        this.project = project;
        enableListening();
        startListening();
    }

    public static void deleteDirectory(Path path) {
        if (Files.isDirectory(path)) {
            try {
                Files.list(path).forEach(p -> {
                    deleteDirectory(p);
                });
                Files.deleteIfExists(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public void startListening() {
        if (!isListening) {
            return;
        }
        // 注册监听器
        VirtualFileManager.getInstance().addVirtualFileListener(this);
    }

    @Override
    public void fileCreated(VirtualFileEvent event) {
        if (!isListening) {
            return;
        }

        VirtualFile file = event.getFile();
        Path path = Path.of(file.getPath());
        String fileName = file.getName();

        if (!fileName.equals("autoversion.record.bin") &&
                !fileName.equals("autoversion.map.bin") &&
                !fileName.equals(".gitignore") &&
                !fileName.equals(".gitattributes") &&
                !filePathContainsGitFolder(path)) {
            handleFileCreate(file);
        }

    }

    @Override
    public void fileDeleted(VirtualFileEvent event) {
        if (!isListening) {
            return;
        }
        VirtualFile file = event.getFile();
        Path path = Path.of(file.getPath());
        String fileName = file.getName();

        if (!fileName.equals("autoversion.record.bin") &&
                !fileName.equals("autoversion.map.bin") &&
                !fileName.equals(".gitignore") &&
                !fileName.equals(".gitattributes") &&
                !filePathContainsGitFolder(path)) {
            handleFileDelete(file);
        }
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
        if (!isListening) {
            return;
        }
        VirtualFile file = event.getFile();
        Path oldParentPath = Path.of(event.getOldParent().getPath());
        String fileName = file.getName();

        if (!fileName.equals("autoversion.record.bin") &&
                !fileName.equals("autoversion.map.bin") &&
                !fileName.equals(".gitignore") &&
                !fileName.equals(".gitattributes") &&
                !filePathContainsGitFolder(oldParentPath)) {
            handleFileMove(event.getOldParent().getPath(), file);
        }
    }

    @Override
    public void propertyChanged(VirtualFilePropertyEvent event) {
        if (!isListening) {
            return;
        }

        if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
            VirtualFile file = event.getFile();
            if (!file.getName().equals("autoversion.record.bin") &&
                    !file.getName().equals("autoversion.map.bin") &&
                    !file.getName().equals(".gitignore") &&
                    !file.getName().equals(".gitattributes") &&
                    !filePathContainsGitFolder(Path.of(file.getPath()))) {
                handleFileRename(event.getOldValue().toString(), file);
            }
        }
    }

    private void handleFileCreate(VirtualFile file) {
        if (file.isDirectory()) {
            return;
        }
        try {

            // 获取文件内容
            String fileContent = new String(file.contentsToByteArray());

            List<String> emptyList = new ArrayList<>();

            List<String> Filecontent = Files.readAllLines(Paths.get(file.getPath()));

            Patch<String> patch ;

            if (Filecontent.isEmpty()) {
                // 手动创建 Chunk，源和目标的起始位置为 0
                List<String> target = List.of("");
                patch = DiffUtils.diff(emptyList, target);

                // 打印 Patch 详细信息
                printPatchDetails(patch);
            }
            else{
                patch = DiffUtils.diff(emptyList, Filecontent);
            }
            FileChange fileChange = new FileChange(file.getPath(), patch, FileChange.ChangeType.ADD);

            VersionStorage.saveVersion(Map.of(file.getPath(), fileChange)); // 保存版本
            refreshToolWindow(); // 刷新 ToolWindow
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    // 打印 Patch 的详细信息
    private static void printPatchDetails(Patch<String> patch) {
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            System.out.println("Delta Type: " + delta.getType());
            System.out.println("Source Position: " + delta.getSource().getPosition());
            System.out.println("Source Lines: " + delta.getSource().getLines());
            System.out.println("Target Position: " + delta.getTarget().getPosition());
            System.out.println("Target Lines: " + delta.getTarget().getLines());
            System.out.println("----");
        }
    }

    private  List<String> convertStringToList(String str) {
        // 使用换行符分割字符串
        String[] lines = str.split("\n");

        // 将数组转换为 List
        return new ArrayList<>(Arrays.asList(lines));
    }

    private void handleFileDelete(VirtualFile file) {
        if (file.isDirectory()) {
            return;
        }
        int versionIndex=0;
        for (int i = 0; i < VersionStorage.getProjectVersions().size(); i++) {
            Map<String, FileChange> version = VersionStorage.getProjectVersions().get(i);
            if (version.containsKey(file.getPath()) && version.get(file.getPath()).getChangeType() != FileChange.ChangeType.DELETE) {
                versionIndex = i;
            }
        }
        Map<String, FileChange> lastVersion =VersionStorage.getVersion(versionIndex);
        FileChange filechange = lastVersion.get(file.getPath());
        String fileContent = filechange.getFileContent(versionIndex);
        List<String>OldFilecontent = convertStringToList(fileContent);
        List<String> NewFilecontent = new ArrayList<>();
        Patch<String> patch = DiffUtils.diff(OldFilecontent, NewFilecontent);



        FileChange fileChange = new FileChange(file.getPath(), patch, FileChange.ChangeType.DELETE);

        VersionStorage.saveVersion(Map.of(file.getPath(), fileChange)); // 保存版本
        refreshToolWindow(); // 刷新 ToolWindow
    }

    private void handleFileDelete(String filePath, String fileName) {
        if (filePath == null) {
            return;
        }

        Patch<String> emptyPatch = new Patch<>();
        FileChange fileChange = new FileChange(filePath, emptyPatch, FileChange.ChangeType.DELETE);

        VersionStorage.saveVersion(Map.of(filePath, fileChange)); // 保存版本
        refreshToolWindow(); // 刷新 ToolWindow
    }


    private void handleFileRename(String oldName, VirtualFile file) {
        if (file.isDirectory()) {
            return;
        }

        // 处理文件重命名，视为删除旧文件并创建新文件，但是文件内容不变
        String oldFilePath = file.getParent().getPath() + "/" + oldName;
        handleFileDelete(oldFilePath, oldName); // 删除旧文件

        handleFileCreate(file);
    }

    private void handleFileMove(String oldParentPath, VirtualFile file) {
        if (file.isDirectory()) {
            return;
        }
        // 处理文件移动，视为删除旧文件并创建新文件
        String oldFilePath = oldParentPath + "/" + file.getName();
        handleFileDelete(oldFilePath, file.getName()); // 删除旧文件
        handleFileCreate(file); // 创建新文件
    }

    public void pauseListening() {
        isListening = false;
    }

    public void enableListening() {
        isListening = true;
    }

    private void refreshToolWindow() {
        // 获取 ToolWindow 并刷新内容
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("AutoVersion Mini");
        if (toolWindow != null) {
            toolWindow.getContentManager().removeAllContents(true);  // 清除旧内容
            VersionToolWindowFactory.getInstance(project).createToolWindowContent(project, toolWindow);  // 重新加载内容
        }
    }

    // 判断是否属于 .git 文件夹中的文件
    private boolean filePathContainsGitFolder(Path path) {
        return path.toString().contains("/.git/") || path.toString().contains("\\.git\\");
    }
}
