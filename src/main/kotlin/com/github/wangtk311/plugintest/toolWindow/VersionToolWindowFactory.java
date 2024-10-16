package com.github.wangtk311.plugintest.toolWindow;

import com.github.wangtk311.plugintest.VersionStorage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public class VersionToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel();
        JLabel label = new JLabel("项目历史版本");

        // 获取所有历史版本
        List<Map<String, String>> projectVersions = VersionStorage.getProjectVersions();

        // 创建UI列表以显示所有版本
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (int i = 0; i < projectVersions.size(); i++) {
            listModel.addElement("版本 " + (i + 1));
        }

        JList<String> versionList = new JBList<>(listModel);
        versionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedIndex = versionList.getSelectedIndex();
                if (selectedIndex >= 0) {
                    // 显示选中的版本的详细内容
                    showVersionDetails(panel, selectedIndex);
                }
            }
        });

        panel.add(label);
        panel.add(new JScrollPane(versionList));

        // 显示内容
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    // 显示选中的版本的文件及其内容
    private void showVersionDetails(JPanel panel, int versionIndex) {
        Map<String, String> versionContents = VersionStorage.getVersion(versionIndex);

        panel.removeAll(); // 清除旧内容
        JLabel detailsLabel = new JLabel("版本 " + (versionIndex + 1) + " 的文件内容：");

        JTextArea textArea = new JTextArea(20, 50);
        for (Map.Entry<String, String> entry : versionContents.entrySet()) {
            textArea.append("文件: " + entry.getKey() + "\n");
            textArea.append("内容:\n" + entry.getValue() + "\n\n");
        }

        panel.add(detailsLabel);
        panel.add(new JScrollPane(textArea));
        panel.revalidate(); // 刷新UI
        panel.repaint();
    }
}
