package com.github.wangtk311.plugintest.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VersionStorage {
    // 存储项目的历史版本
    private static final List<Map<String, String>> projectVersions = new ArrayList<>();

    // 保存当前项目状态作为一个版本
    public static void saveProjectVersion(Map<String, String> fileContents) {
        projectVersions.add(new HashMap<>(fileContents));
        System.out.println("保存了一个新版本，总版本数：" + projectVersions.size());  // 添加日志确认保存
    }

    // 获取所有项目的历史版本
    public static List<Map<String, String>> getProjectVersions() {
        return projectVersions;
    }

    // 获取指定版本的内容
    public static Map<String, String> getVersion(int versionIndex) {
        if (versionIndex >= 0 && versionIndex < projectVersions.size()) {
            return projectVersions.get(versionIndex);
        }
        return new HashMap<>();
    }
}
