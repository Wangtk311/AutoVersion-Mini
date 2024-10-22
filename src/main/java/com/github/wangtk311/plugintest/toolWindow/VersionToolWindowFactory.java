package com.github.wangtk311.plugintest.toolWindow;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.github.wangtk311.plugintest.components.MyProjectComponent;
import com.github.wangtk311.plugintest.listeners.FileSystemListener;
import com.github.wangtk311.plugintest.services.FileChange;
import com.github.wangtk311.plugintest.services.VersionStorage;
import com.github.wangtk311.plugintest.listeners.DocumentListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class VersionToolWindowFactory implements ToolWindowFactory {

    private static VersionToolWindowFactory instance;

    private VersionToolWindowFactory() {}

    public static VersionToolWindowFactory getInstance(Project project) {
        if (instance == null) {
            instance = new VersionToolWindowFactory();
        }
        return instance;
    }

    private JPanel historyWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel();
        JLabel label = new JLabel("AutoVersion Mini 已存档的版本\n\n", SwingConstants.CENTER);
        JLabel label2 = new JLabel("-", SwingConstants.CENTER);
        JButton gitButton = new JButton("→☍ 将最新版本推送到Git");
        gitButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(panel, "确定要推送到Git吗?\n这将保存当前的版本作为一个大版本的提交,\n并保存一系列小版本的提交。\n该操作不可逆!\n\n确认后请等待操作结果出现后\n再进行后续操作!", "双重确认", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                // 获取最新的小版本号
                int latestMinorVersion = VersionStorage.getProjectVersions().size() - 1;
                // 获取最新的大版本号
                int latestMajorVersion = VersionStorage.majorToMinorVersionMap.size();
                // 如果最新大版本号后面没有小版本，不进行推送
                if (latestMinorVersion == VersionStorage.majorToMinorVersionMap.get(latestMajorVersion)) {
                    JOptionPane.showMessageDialog(panel, "没有新的版本需要推送!", "推送失败", JOptionPane.CLOSED_OPTION);
                    return;
                }
                //int latestMinorVersion = VersionStorage.getProjectVersions().size() - 1;
                // 更新大版本号映射表
                VersionStorage.majorToMinorVersionMap.put(VersionStorage.majorToMinorVersionMap.size() + 1, latestMinorVersion);
                // 将版本数据写入版本映射文件
                VersionStorage.saveMapToDisk();

                // 更新latestMajorVersion
                latestMajorVersion = latestMajorVersion + 1;

                // 暂时关闭文件系统监听器和文档监听器
                pauseAllListeners(project);

                // 获取上一个大版本号及对应的小版本号
                int lastMajorVersion = VersionStorage.majorToMinorVersionMap.size() - 1; // 上一个大版本号
                int lastMinorVersion = VersionStorage.majorToMinorVersionMap.get(lastMajorVersion); // 上一个小版本号

                // 首先切换到主分支
                ProcessBuilder processBuilder1 = new ProcessBuilder("git", "checkout", "main");
                processBuilder1.directory(new File(project.getBasePath()));
                try {
                    Process process = processBuilder1.start();
                    process.waitFor();
                } catch (IOException | InterruptedException e1) {
                    e1.printStackTrace();
                }

                // 从上一次commit的小版本号开始，逐个commit到最新的小版本号，首先使用git branch **从当前创建新的分支，如果新的大版本为x.0，则命名此小版本分支为Vx-1，例如新提交的大版本为2.0，则命名分支为V1分支
                String branchName = "V" + lastMajorVersion;
                ProcessBuilder processBuilder2 = new ProcessBuilder("git", "checkout", "-b", branchName);

                // 设置工作目录
                processBuilder2.directory(new File(project.getBasePath()));
                try {
                    Process process = processBuilder2.start();
                    process.waitFor();
                } catch (IOException | InterruptedException e1) {
                    e1.printStackTrace();
                }

                // 从上一次commit的小版本号开始，逐个commit到最新的小版本号
                for (int i = lastMinorVersion + 1; i <= latestMinorVersion; i++) {
                    // 回滚到当前小版本
                    try {
                        Files.walk(Paths.get(project.getBasePath())).forEach(path -> {
                            File file = path.toFile();

                            String fileName = file.getName();

                            // 排除不需要删除的文件
                            if (file.isFile() && !fileName.equals("autoversion.record.bin") &&
                                    !fileName.equals("autoversion.map.bin") &&
                                    !fileName.equals(".gitignore") &&
                                    !fileName.equals(".gitattributes") &&
                                    !filePathContainsGitFolder(path)) {
                                try {
                                    Files.deleteIfExists(path); // 删除文件
                                } catch (IOException e2) {
                                    e2.printStackTrace();
                                }
                            }
                        });
                    } catch (IOException e3) {
                        throw new RuntimeException(e3);
                    }

                    Map<String, FileChange> versionFiles;

                    // 从第一个版本开始恢复到选中的版本
                    for (int j = 0; j <= i; j++){
                        versionFiles = VersionStorage.getVersion(j);
                        for (Map.Entry<String, FileChange> entry : versionFiles.entrySet()) {
                            FileChange fileChange = entry.getValue();
                            String filePath = fileChange.getFilePath();
                            switch (fileChange.getChangeType()) {
                                case DELETE:
                                    VersionStorage.deleteFile(filePath);
                                    break;
                                case ADD, MODIFY:
                                    VersionStorage.restoreFileToDirectory(filePath, fileChange.getFileContent(j));
                                    break;
                            }
                        }
                    }

                    // 提交当前小版本到Vx-1分支
                    ProcessBuilder processBuilder3 = new ProcessBuilder("git", "add", ".");
                    processBuilder3.directory(new File(project.getBasePath()));
                    try {
                        Process process = processBuilder3.start();
                        process.waitFor();
                    } catch (IOException | InterruptedException e1) {
                        e1.printStackTrace();
                    }

                    ProcessBuilder processBuilder4 = new ProcessBuilder("git", "commit", "-m\"" + "V" + lastMajorVersion + "." + (i - lastMinorVersion) + "\"");
                    processBuilder4.directory(new File(project.getBasePath()));
                    try {
                        Process process = processBuilder4.start();
                        process.waitFor();
                        System.out.println("Commit V" + lastMajorVersion + "." + (i - lastMinorVersion) + " to branch " + branchName);
                    } catch (IOException | InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }

                // 切换到主分支
                ProcessBuilder processBuilder5 = new ProcessBuilder("git", "checkout", "main");
                processBuilder5.directory(new File(project.getBasePath()));
                try {
                    Process process = processBuilder5.start();
                    process.waitFor();
                    System.out.println("Switch to main branch");
                } catch (IOException | InterruptedException e1) {
                    e1.printStackTrace();
                }

                // 合并Vx-1分支到主分支
                ProcessBuilder processBuilder6 = new ProcessBuilder("git", "merge", branchName, "--no-ff", "-m", "V" + latestMajorVersion + ".0");
                processBuilder6.directory(new File(project.getBasePath()));
                try {
                    Process process = processBuilder6.start();
                    process.waitFor();
                    System.out.println("Merge branch " + branchName + " to main branch");
                    System.out.println("Commit: V" + latestMajorVersion + ".0" + " to main branch");
                } catch (IOException | InterruptedException e1) {
                    e1.printStackTrace();
                }

                JOptionPane.showMessageDialog(panel, "已成功推送到Git!\n请刷新文件系统!", "推送成功", JOptionPane.CLOSED_OPTION);

                // 刷新文件系统
                project.getBaseDir().refresh(false, true);

                // 重启文件系统监听器和文档监听器
                enableAllListeners(project);

                // 清空当前面板内容并重新加载历史版本列表
                panel.removeAll(); // 清空面板
                panel.add(historyWindowContent(project, toolWindow)); // 显示历史版本列表

                // 重新绘制面板
                panel.revalidate(); // 通知 Swing 重新布局
                panel.repaint(); // 重新绘制面板
            }
        });

        // 获取所有历史版本
        List<Map<String, FileChange>> projectVersions = VersionStorage.getProjectVersions();

        // 打印所有映射
        System.out.println("majorToMinorVersionMap: " + VersionStorage.majorToMinorVersionMap);

        // 创建UI列表以显示所有版本
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (int majorVersion = 1; majorVersion <= VersionStorage.majorToMinorVersionMap.size(); majorVersion++) {
            int startMinorVersion = VersionStorage.majorToMinorVersionMap.get(majorVersion);
            int endMinorVersion;

            // 判断是否是最后一个大版本
            if (majorVersion == VersionStorage.majorToMinorVersionMap.size()) {
                // 如果是最后一个大版本，显示到最新的小版本
                endMinorVersion = projectVersions.size() - 1;
            } else {
                // 否则，显示到下一个大版本的前一个小版本
                int nextStartMinorVersion = VersionStorage.majorToMinorVersionMap.get(majorVersion + 1);
                endMinorVersion = nextStartMinorVersion - 1;
            }

            // 根据起始小版本和结束小版本，生成每个小版本的显示条目
            for (int minorVersion = startMinorVersion; minorVersion <= endMinorVersion; minorVersion++) {
                listModel.addElement("【 Version " + majorVersion + "." + (minorVersion - startMinorVersion)
                        + " 】版本 - 存档了 " + projectVersions.get(minorVersion).size() + " 个文件变动");
            }
        }

        JList<String> versionList = new JBList<>(listModel);
        versionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedIndex = versionList.getSelectedIndex();
                if (selectedIndex >= 0) {
                    showVersionDetails(panel, selectedIndex, project, toolWindow);
                }
            }
        });

        JButton backButton = new JButton("✖ 抹掉所有历史版本");
        backButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(panel, "确定要抹掉所有历史版本吗?\n这将同步保存当前状态作为第一个\n历史版本并提交到Git。\n该操作不可逆!\n\n确认后请等待操作结果出现后\n再进行后续操作!", "双重确认", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                // 暂时关闭文件系统监听器和文档监听器
                pauseAllListeners(project);

                // 保存所有编辑器中的文件
                FileEditorManager editorManager = FileEditorManager.getInstance(project);
                FileEditor[] editors =  editorManager.getAllEditors();

                for (FileEditor editor : editors) {
                    VirtualFile file = editor.getFile();
                    if (file != null && file.isValid()) {
                        // 获取与该文件关联的 Document
                        Document document = FileDocumentManager.getInstance().getDocument(file);
                        if (document != null) {
                            // 在写入命令中保存文档内容
                            WriteCommandAction.runWriteCommandAction(project, () -> {
                                try {
                                    // 将文档内容写入文件
                                    file.setBinaryContent(document.getText().getBytes());
                                    file.refresh(false, false); // 刷新文件系统，确保文件更新
                                } catch (Exception e4) {
                                    e4.printStackTrace(); // 处理异常
                                }
                            });
                        }
                    }
                }

                // 将vfs虚拟文件系统中的所有文件保存到磁盘
                try {
                    project.getBaseDir().refresh(false, true);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }

                // 清空autoversion.record.bin文件(写入空列表)
                VersionStorage.clearVersions();

                // 清空版本映射表
                VersionStorage.majorToMinorVersionMap.clear();

                // 创建新的 fileChanges map 来保存当前项目状态
                Map<String, FileChange> fileChanges = new HashMap<>();
                Path projectRoot = Paths.get(project.getBasePath());

                // 使用 Files.walk 递归遍历项目目录及其子目录中的所有文件
                try {
                    Files.walk(projectRoot).forEach(path -> {
                        File file = path.toFile();
                        //**************************修改patch
                        String fileName = file.getName();


                        // 排除不需要的文件
                        if (!fileName.equals("autoversion.record.bin") &&
                                !fileName.equals("autoversion.map.bin") &&
                                !fileName.equals(".gitignore") &&
                                !fileName.equals(".gitattributes") &&
                                !filePathContainsGitFolder(path)) {
                            if (file.isFile()) {
                                try {
                                    String filePath = file.getCanonicalPath();
                                    // 替换所有的反斜杠为正斜杠
                                    filePath = filePath.replace("\\", "/");
                                    List<String> emptyList = Collections.emptyList();
                                    List<String> Filecontent = Files.readAllLines(Paths.get(filePath));
                                    Patch<String> patch = DiffUtils.diff(emptyList, Filecontent);
                                    fileChanges.put(filePath, new FileChange(filePath, patch, FileChange.ChangeType.ADD));//---
                                } catch (IOException e2) {
                                    e2.printStackTrace();
                                }
                            }
                        }



                    });
                } catch (IOException e3) {
                    throw new RuntimeException(e3);
                }

                // 保存新的版本
                VersionStorage.saveVersion(fileChanges);

                // 保存版本映射表
                VersionStorage.majorToMinorVersionMap.put(1, 0);

                // 将版本数据写入磁盘
                VersionStorage.saveVersionsToDisk();

                // 从磁盘刷新一下项目目录
                project.getBaseDir().refresh(false, true);

                gitInit(project);

                // 对每一个listener都应用getOldfileContentFirst(filePath);
                for (DocumentListener listener : MyProjectComponent.getInstance(project).documentListeners){
                    listener.getOldfileContentFirst(listener.getTracingFilePath());
                }

                // 重新启用文件系统监听器和文档监听器
                enableAllListeners(project);

                // 显示成功信息
                JOptionPane.showMessageDialog(panel, "已抹除所有历史版本!", "抹除成功", JOptionPane.CLOSED_OPTION);

                // 清空当前面板内容并重新加载历史版本列表
                panel.removeAll(); // 清空面板
                panel.add(historyWindowContent(project, toolWindow)); // 显示历史版本列表

                // 重新绘制面板
                panel.revalidate(); // 通知 Swing 重新布局
                panel.repaint(); // 重新绘制面板

            }
        });


        JPanel labelPanel = new JPanel();
        labelPanel.setLayout(new GridLayout(2, 1));
        labelPanel.add(label);
        labelPanel.add(label2);

        panel.setLayout(new BorderLayout());
        panel.add(labelPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(versionList), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 2));
        buttonPanel.add(backButton);
        buttonPanel.add(gitButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    public void gitInit(Project project) {
        try {
            Files.walk(Paths.get(project.getBasePath())).forEach(path -> {
                File file = path.toFile();
                if (file.isDirectory() && file.getName().equals(".git")) {
                    FileSystemListener.deleteDirectory(path);
                }
            });
        } catch (IOException e3) {
            throw new RuntimeException(e3);
        }

        try {
            // 执行 git init 命令并等待完成
            ProcessBuilder initProcessBuilder = new ProcessBuilder("git", "init");
            initProcessBuilder.directory(new File(project.getBasePath().replace("\\", "/")));
            Process initProcess = initProcessBuilder.start();
            int exitCode = initProcess.waitFor();

            if (exitCode != 0) {
                System.err.println("git init failed with exit code: " + exitCode);
            }

            //切换到主分支
            ProcessBuilder checkoutProcessBuilder = new ProcessBuilder("git", "checkout", "-b", "main");
            checkoutProcessBuilder.directory(new File(project.getBasePath().replace("\\", "/")));
            Process checkoutProcess = checkoutProcessBuilder.start();
            checkoutProcess.waitFor();

            // 从磁盘刷新一下项目目录
            project.getBaseDir().refresh(false, true);

            // 在主线程中执行 write action
            ApplicationManager.getApplication().invokeAndWait(() -> {
                ApplicationManager.getApplication().runWriteAction(() -> {
                    try {
                        // 刷新虚拟文件系统，但在新线程中执行，避免占用EDT，等待刷新完成再继续
                        VirtualFileManager.getInstance().asyncRefresh(() -> {
                            System.out.println("Virtual File System refreshed.");
                        });

                        // 尝试获取并创建 .gitignore 文件
                        VirtualFile gitIgnore = project.getBaseDir().findChild(".gitignore");
                        String toAppend = "\nautoversion.record.bin\nautoversion.map.bin\n";
                        String existingContent;

                        if (gitIgnore != null && gitIgnore.isValid()) {
                            // 如果文件已存在，读取其内容
                            existingContent = new String(gitIgnore.contentsToByteArray());
                            System.out.println("Exist .gitignore:");
                            System.out.println(gitIgnore.getPath());
                            System.out.println(existingContent);
                            System.out.println("autoversion.record.bin--exist:  " + existingContent.contains("autoversion.record.bin"));
                            System.out.println("autoversion.map.bin:  " + existingContent.contains("autoversion.map.bin"));
                            if (!existingContent.contains("autoversion.record.bin") || !existingContent.contains("autoversion.map.bin")) {
                                // 写入内容
                                Files.write(Paths.get(gitIgnore.getPath().replace("\\", "/")),
                                        toAppend.getBytes(),
                                        StandardOpenOption.APPEND);
                                System.out.println("Appended to .gitignore.");
                            } else {
                                System.out.println("gitignore contains autoversion.record.bin and autoversion.map.bin, skip adding.");
                            }
                        } else {
                            if (gitIgnore != null) {
                                // 如果文件无效，在磁盘上删除
                                gitIgnore.delete(this);
                            }
                            // 创建新的 .gitignore 文件
                            System.out.println("Created .gitignore.");
                            createGitIgnore(project);
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


    private void createGitIgnore(Project project) {
        AtomicReference<VirtualFile> gitIgnore = new AtomicReference<>();
        try {
            // 使用 WriteCommandAction 保证写入安全
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    VirtualFile baseDir = project.getBaseDir();
                    // 创建 .gitignore 文件
                    gitIgnore.set(baseDir.findOrCreateChildData(this, ".gitignore"));

                    // 添加内容到 .gitignore 文件
                    String content = "\nautoversion.record.bin\nautoversion.map.bin\n";
                    gitIgnore.get().setBinaryContent(content.getBytes());

                    // 刷新 VirtualFile 系统，确保写入到磁盘
                    gitIgnore.get().refresh(false, false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // 刷新项目目录，确保 IntelliJ IDEA 能够检测到新的文件
            VirtualFileManager.getInstance().syncRefresh();
            System.out.println("Created .gitignore in project root.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = historyWindowContent(project, toolWindow);
        // 显示内容
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    // 显示选中的版本的文件及其内容
    private void showVersionDetails(JPanel panel, int versionIndex, Project project, ToolWindow toolWindow){
        Map<String, FileChange> versionContents = VersionStorage.getVersion(versionIndex);

        // 找到当前小版本对应的大版本和小版本
        int majorVersion = 0;
        int minorVersion = 0;

        // 遍历大版本映射表，查找对应的大版本和小版本
        for (int major : VersionStorage.majorToMinorVersionMap.keySet()) {
            int startMinorVersion = VersionStorage.majorToMinorVersionMap.get(major);
            if (versionIndex >= startMinorVersion) {
                majorVersion = major;
                minorVersion = versionIndex - startMinorVersion;
            } else {
                break;
            }
        }

        panel.removeAll(); // 清除旧内容

        JTextArea textArea = new JTextArea(20, 50);
        textArea.append("历史版本: Version " + majorVersion + "." + minorVersion + " 的内容是:\n\n");
        for (Map.Entry<String, FileChange> entry : versionContents.entrySet()) {
            FileChange fileChange = entry.getValue();
            textArea.append("----------------------------------------------\n");
            textArea.append("文件: " + fileChange.getFilePath() + "\n");
            textArea.append("操作: " + fileChange.getChangeType() + "\n");
            textArea.append("内容:\n\n[===文件开始===]\n" +  fileChange.getFileContent(versionIndex)+ "\n[===文件结束===]\n\n");
        }

        // 添加“返回”按钮
        JButton backButton = new JButton("◀ 返回版本历史列表");
        backButton.addActionListener(e -> {
            // 点击按钮后返回历史版本列表
            panel.removeAll(); // 清空当前内容
            panel.add(historyWindowContent(project, toolWindow)); // 显示历史版本列表
        });

        // 添加“恢复版本”按钮
        JButton restoreButton = new JButton("↑ 回滚到此版本");

        int finalMajorVersion = majorVersion;
        int finalMinorVersion = minorVersion;

        restoreButton.addActionListener(e -> {


            // 显示确认对话框，包含大版本和小版本信息
            int confirm = JOptionPane.showConfirmDialog(panel,
                    "确定要回滚到 Version " + finalMajorVersion + "." + finalMinorVersion + " 版本吗?\n这将丢弃当前的工作,\n同时丢弃回滚目标后面的版本!\n\n确认后请等待操作结果出现后\n再进行后续操作!",
                    "双重确认", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                // 暂时关闭文件系统监听器和文档监听器
                pauseAllListeners(project);

                // 关闭所有打开的编辑器
                FileEditorManager editorManager = FileEditorManager.getInstance(project);
                FileEditor[] editors =  editorManager.getAllEditors();
                for (FileEditor editor : editors) {
                    editorManager.closeFile(editor.getFile());
                }

                // 回滚Git信息
                // 首先切换到主分支，然后逐个commit大版本回滚到VfinalMajorVersion.0
                ProcessBuilder processBuilder1 = new ProcessBuilder("git", "checkout", "main");
                processBuilder1.directory(new File(project.getBasePath()));
                try {
                    Process process = processBuilder1.start();
                    process.waitFor();
                } catch (IOException | InterruptedException e1) {
                    e1.printStackTrace();
                }

                // 回滚main分支到VfinalMajorVersion.0
                for (int i = VersionStorage.majorToMinorVersionMap.size(); i > finalMajorVersion; i--) {
                    ProcessBuilder processBuilder2 = new ProcessBuilder("git", "reset", "--hard", "HEAD~1");
                    processBuilder2.directory(new File(project.getBasePath()));
                    try {
                        Process process = processBuilder2.start();
                        process.waitFor();
                    } catch (IOException | InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }

                // 删除回滚到的版本对应的大版本后面的所有Vx分支
                for (int i = finalMajorVersion; i < VersionStorage.majorToMinorVersionMap.size(); i++) {
                    ProcessBuilder processBuilder3 = new ProcessBuilder("git", "branch", "-D", "V" + i);
                    processBuilder3.directory(new File(project.getBasePath()));
                    try {
                        Process process = processBuilder3.start();
                        process.waitFor();
                    } catch (IOException | InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }

                System.out.println("回滚到 Version " + (versionIndex + 1) + " 版本");
                // 首先从根目录递归检索删除当前项目中的文件，然后依照版本从前到后逐步恢复选中版本的文件，可以避免留下当前版本中存在但回滚目标版本中不存在的文件
                try {
                    Files.walk(Paths.get(project.getBasePath())).forEach(path -> {
                        File file = path.toFile();

                        String fileName = file.getName();

                        // 排除不需要删除的文件
                        if (file.isFile() && !fileName.equals("autoversion.record.bin") &&
                                !fileName.equals("autoversion.map.bin") &&
                                !fileName.equals(".gitignore") &&
                                !fileName.equals(".gitattributes") &&
                                !filePathContainsGitFolder(path)) {
                            try {
                                Files.deleteIfExists(path); // 删除文件
                            } catch (IOException e2) {
                                e2.printStackTrace();
                            }
                        }
                    });
                } catch (IOException e3) {
                    throw new RuntimeException(e3);
                }

                int selectedVersion = versionIndex;
                Map<String, FileChange> versionFiles;

                // 从第一个版本开始恢复到选中的版本
                for (int i = 0; i <= versionIndex; i++){
                    versionFiles = VersionStorage.getVersion(i);
                    for (Map.Entry<String, FileChange> entry : versionFiles.entrySet()) {
                        FileChange fileChange = entry.getValue();
                        String filePath = fileChange.getFilePath();
                        switch (fileChange.getChangeType()) {
                            case DELETE:
                                VersionStorage.deleteFile(filePath);
                                break;
                            case ADD, MODIFY:
                                VersionStorage.restoreFileToDirectory(filePath, fileChange.getFileContent(versionIndex));
                                break;
                        }
                    }
                }

                // 丢弃回滚目标后面的大版本
                for (int i = VersionStorage.majorToMinorVersionMap.size(); i > finalMajorVersion; i--) {
                    VersionStorage.majorToMinorVersionMap.remove(i);
                }

                // 丢弃回滚目标后面的小版本
                for (int i = VersionStorage.getProjectVersions().size() - 1; i > selectedVersion; i--) {
                    VersionStorage.getProjectVersions().remove(i);
                }

                // 保存版本映射表
                VersionStorage.saveMapToDisk();

                // 将版本数据写入版本历史文件
                VersionStorage.saveVersionsToDisk();

                // 显示成功信息
                JOptionPane.showMessageDialog(panel, "已回滚到 Version " + finalMajorVersion + "." + finalMinorVersion + " 版本!\n请刷新文件系统!", "回滚成功", JOptionPane.CLOSED_OPTION);

                // 从磁盘刷新一下项目目录
                project.getBaseDir().refresh(false, true);


                // 清空当前面板内容并重新加载历史版本列表
                panel.removeAll(); // 清空面板
                panel.add(historyWindowContent(project, toolWindow)); // 显示历史版本列表

                // 重新绘制面板
                panel.revalidate(); // 通知 Swing 重新布局
                panel.repaint(); // 重新绘制面板


                // 重新启用文件系统监听器和文档监听器
                enableAllListeners(project);
            }
        });

        // 添加恢复和返回按钮到界面
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 2));
        buttonPanel.add(backButton);
        buttonPanel.add(restoreButton);

        panel.setLayout(new BorderLayout());
        panel.add(buttonPanel, BorderLayout.BEFORE_FIRST_LINE);
        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);

        panel.revalidate(); // 刷新UI
        panel.repaint();
    }

    public void pauseAllListeners(Project project) {
        MyProjectComponent myProjectComponent = MyProjectComponent.getInstance(project);
        myProjectComponent.fileSystemListener.pauseListening();
        for (DocumentListener documentListener : myProjectComponent.documentListeners) {
            documentListener.pauseListening();
        }
        System.out.println("Pause listeners.");
    }

    public void enableAllListeners(Project project) {
        MyProjectComponent myProjectComponent = MyProjectComponent.getInstance(project);
        myProjectComponent.fileSystemListener.enableListening();
        for (DocumentListener documentListener : myProjectComponent.documentListeners) {
            documentListener.enableListening();
        }
        System.out.println("Enable listeners.");
    }

    // 判断是否属于 .git 文件夹中的文件
    private boolean filePathContainsGitFolder(Path path) {
        return path.toString().contains("/.git/") || path.toString().contains("\\.git\\");
    }
}

