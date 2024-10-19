package com.github.wangtk311.plugintest.toolWindow;

import com.github.wangtk311.plugintest.components.MyProjectComponent;
import com.github.wangtk311.plugintest.listeners.FileSystemListener;
import com.github.wangtk311.plugintest.services.FileChange;
import com.github.wangtk311.plugintest.services.VersionStorage;
import com.github.wangtk311.plugintest.listeners.DocumentListener;
import com.intellij.openapi.project.Project;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VersionToolWindowFactory implements ToolWindowFactory {

    private JPanel historyWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel();
        JLabel label = new JLabel("AutoVersion Mini 已存档的版本\n\n", SwingConstants.CENTER);
        JLabel label2 = new JLabel("-", SwingConstants.CENTER);
        JButton gitButton = new JButton("→☍ 将最新版本推送到Git");
        gitButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(panel, "确定要推送到Git吗?\n这将保存当前的版本作为一个大版本的提交,\n并保存一系列小版本的提交。\n该操作不可逆!", "双重确认", JOptionPane.YES_NO_OPTION);
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

                // 更新大版本号映射表
                VersionStorage.majorToMinorVersionMap.put(VersionStorage.majorToMinorVersionMap.size() + 1, latestMinorVersion);
                // 将版本数据写入版本映射文件
                VersionStorage.saveMapToDisk();

                //
                // 操作
                //

                JOptionPane.showMessageDialog(panel, "已成功推送到Git!", "推送成功", JOptionPane.CLOSED_OPTION);

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
            int confirm = JOptionPane.showConfirmDialog(panel, "确定要抹掉所有历史版本吗?\n这将同步保存当前状态作为一个历史版本。\n该操作不可逆!", "双重确认", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                // 暂时关闭文件系统监听器和文档监听器
                pauseAllListeners(project);

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
                        if (file.isFile() && !file.getName().equals("autoversion.record.bin")) {
                            try {
                                String filePath = file.getCanonicalPath();
                                // 替换所有的反斜杠为正斜杠
                                filePath = filePath.replace("\\", "/");
                                // 将文件的路径和 FileChange 实例放入 map, 保存文件内容
                                String fileContent = new String(Files.readAllBytes(path)); // 读取文件内容
                                fileChanges.put(filePath, new FileChange(filePath, fileContent, FileChange.ChangeType.ADD));
                            } catch (IOException e2) {
                                e2.printStackTrace();
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

                // 显示成功信息
                JOptionPane.showMessageDialog(panel, "已抹除所有历史版本!", "抹除成功", JOptionPane.CLOSED_OPTION);

                // 从磁盘刷新一下项目目录
                project.getBaseDir().refresh(false, true);

                // 重新启用文件系统监听器和文档监听器
                enableAllListeners(project);

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

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = historyWindowContent(project, toolWindow);
        // 显示内容
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    // 显示选中的版本的文件及其内容
    private void showVersionDetails(JPanel panel, int versionIndex, Project project, ToolWindow toolWindow) {
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
            textArea.append("内容:\n\n[===文件开始===]\n" + (fileChange.getFileContent() == null ? "[文件无内容]" : fileChange.getFileContent()) + "\n[===文件结束===]\n\n");
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
                    "确定要回滚到 Version " + finalMajorVersion + "." + finalMinorVersion + " 版本吗?\n这将丢弃当前的工作,\n同时丢弃回滚目标后面的版本!",
                    "双重确认", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                // 暂时关闭文件系统监听器和文档监听器
                pauseAllListeners(project);

                System.out.println("回滚到 Version " + (versionIndex + 1) + " 版本");
                // 首先从根目录递归检索删除当前项目中的每一个文件(除了autoversion.record.bin文件)，然后依照版本从前到后逐步恢复选中版本的文件，可以避免留下当前版本中存在但回滚目标版本中不存在的文件
                try {
                    Files.walk(Paths.get(project.getBasePath())).forEach(path -> {
                        File file = path.toFile();
                        if (file.isFile() && !file.getName().equals("autoversion.record.bin")) {
                            try {
                                Files.deleteIfExists(path);
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
                                VersionStorage.restoreFileToDirectory(filePath, fileChange.getFileContent());
                                break;
                        }
                    }
                }

                // 丢弃回滚目标后面的大版本
                for (int major : VersionStorage.majorToMinorVersionMap.keySet()) {
                    int startMinorVersion = VersionStorage.majorToMinorVersionMap.get(major);
                    if (startMinorVersion > selectedVersion) {
                        VersionStorage.majorToMinorVersionMap.remove(major);
                    }
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
                JOptionPane.showMessageDialog(panel, "已回滚到 Version " + finalMajorVersion + "." + finalMinorVersion + " 版本!", "回滚成功", JOptionPane.CLOSED_OPTION);

                // 从磁盘刷新一下项目目录
                project.getBaseDir().refresh(false, true);

                // 重新启用文件系统监听器和文档监听器
                enableAllListeners(project);

                // 清空当前面板内容并重新加载历史版本列表
                panel.removeAll(); // 清空面板
                panel.add(historyWindowContent(project, toolWindow)); // 显示历史版本列表

                // 重新绘制面板
                panel.revalidate(); // 通知 Swing 重新布局
                panel.repaint(); // 重新绘制面板
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
}

