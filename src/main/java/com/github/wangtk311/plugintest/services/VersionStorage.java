package com.github.wangtk311.plugintest.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class VersionStorage {

    // 保存所有版本记录
    private static List<Map<String, FileChange>> projectVersions = new ArrayList<>();

    // 获取项目的所有历史版本
    public static List<Map<String, FileChange>> getProjectVersions() {
        return projectVersions;
    }

    // 获取某个版本的内容
    public static Map<String, FileChange> getVersion(int versionIndex) {
        return projectVersions.get(versionIndex);
    }

    // 保存当前项目的文件变化
    public static void saveVersion(Map<String, FileChange> fileChanges) {
        projectVersions.add(new HashMap<>(fileChanges)); // 保存文件变化的副本
    }

    // 还原文件到目录
    public static void restoreFileToDirectory(String filePath, String fileContent) {
        try {
            Path path = Paths.get(filePath);
            Files.createDirectories(path.getParent()); // 如果目录不存在，先创建
            Files.write(path, fileContent.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 删除指定路径的文件
    public static void deleteFile(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
