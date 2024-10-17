package com.github.wangtk311.plugintest.toolWindow;

import com.github.wangtk311.plugintest.services.FileChange;
import com.github.wangtk311.plugintest.services.VersionStorage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class VersionToolWindowFactory implements ToolWindowFactory {

    private JPanel historyWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel();
        JLabel label = new JLabel("已记录的项目历史版本\n\n", SwingConstants.CENTER);

        // 获取所有历史版本
        List<Map<String, FileChange>> projectVersions = VersionStorage.getProjectVersions();

        // 创建UI列表以显示所有版本
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (int i = 0; i < projectVersions.size(); i++) {
            listModel.addElement("【 Version " + (i + 1) + " 】版本, 共保存了 " + projectVersions.get(i).size() + " 个文件的修改");
        }

        JList<String> versionList = new JBList<>(listModel);
        versionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedIndex = versionList.getSelectedIndex();
                if (selectedIndex >= 0) {
                    // 显示选中的版本的详细内容
                    showVersionDetails(panel, selectedIndex, project, toolWindow);
                }
            }
        });

        panel.setLayout(new BorderLayout());
        panel.add(label, BorderLayout.NORTH);
        panel.add(new JScrollPane(versionList), BorderLayout.CENTER);

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

        panel.removeAll(); // 清除旧内容

        JTextArea textArea = new JTextArea(20, 50);
        textArea.append("历史版本: Version " + (versionIndex + 1) + " 的内容是:\n\n");
        for (Map.Entry<String, FileChange> entry : versionContents.entrySet()) {
            FileChange fileChange = entry.getValue();
            textArea.append("----------------------------------------------\n");
            textArea.append("文件: " + fileChange.getFilePath() + "\n");
            textArea.append("操作: " + fileChange.getChangeType() + "\n");
            textArea.append("内容:\n\n===文件开始===\n" + (fileChange.getFileContent() == null ? "无内容" : fileChange.getFileContent()) + "\n===文件结束===\n\n");
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
        restoreButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(panel, "确定要回滚到 Version " + (versionIndex + 1) + " 版本吗?", "双重确认", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                int selectedVersion = versionIndex;
                Map<String, FileChange> versionFiles = VersionStorage.getVersion(selectedVersion);

                for (Map.Entry<String, FileChange> entry : versionFiles.entrySet()) {
                    FileChange fileChange = entry.getValue();
                    String filePath = fileChange.getFilePath();
                    switch (fileChange.getChangeType()) {
                        case ADD:
                            // 如果文件是新增的，在回滚时删除
                            VersionStorage.deleteFile(filePath);
                            break;
                        case DELETE:
                            // 如果文件是删除的，在回滚时重新创建并写入内容
                            VersionStorage.restoreFileToDirectory(filePath, fileChange.getFileContent());
                            break;
                        case MODIFY:
                            // 如果文件是修改的，在回滚时恢复内容
                            VersionStorage.restoreFileToDirectory(filePath, fileChange.getFileContent());
                            break;
                    }
                }
                JOptionPane.showConfirmDialog(panel, "已回滚到 Version " + (selectedVersion + 1) + " 版本!", "回滚成功", JOptionPane.CLOSED_OPTION);
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
}
