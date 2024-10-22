package com.github.wangtk311.plugintest.services;

import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import com.github.difflib.patch.AbstractDelta;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FileChange implements Serializable {  // 实现 Serializable 接口
    @Serial
    private static final long serialVersionUID = 1L;

    public enum ChangeType {
        ADD, DELETE, MODIFY
    }

    private String filePath;
    private Patch<String> EachFilePatch; // 如果文件是新增或修改的，保存其内容
    private ChangeType changeType;

    public FileChange(String filePath, Patch<String> EachFilePatch, ChangeType changeType) {
        this.filePath = filePath;
        this.EachFilePatch = EachFilePatch;
        this.changeType = changeType;
    }

    public String getFilePath() {
        return filePath;
    }

    public List<Map<String, FileChange>> getProjectVersions() {

        List<Map<String, FileChange>>  projectVersions=VersionStorage.getProjectVersions();

        return projectVersions;
    }

    public List<Patch<String>> getEachFilePatches(List<Map<String, FileChange>> listOfMaps, String filePath,int end) {
        List<Patch<String>> patches = new ArrayList<>();
        System.out.println("getEachFilePatches---listOfMaps: \n" + listOfMaps);
        int i=0;
        for (Map<String, FileChange> map : listOfMaps) {

            FileChange fileChange = map.get(filePath);
            if (fileChange != null) {


                patches.add(fileChange.getEachFilePatch());
            }

            if(i==end)
                break;
            i++;
        }

        return patches;
    }
    public Patch<String> getEachFilePatch() {

        return EachFilePatch;
    }

    private static void printPatchDetails(Patch<String> patch) {
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            System.out.println("Delta Type: " + delta.getType());
            System.out.println("Source Position: " + delta.getSource().getPosition());
            System.out.println("Source Lines: " + delta.getSource().getLines());
            System.out.println("Target Position: " + delta.getTarget().getPosition());
            System.out.println("Target Lines: " + delta.getTarget().getLines());
            System.out.println("=================================");
        }
    }



    public static List<String> applyPatches( List<Patch<String>> patches)  {
        List<String> currentContent = new ArrayList<>();
        for (int i = 0; i < patches.size(); i++) {
            Patch<String> patch = patches.get(i);

            System.out.println("-----------------------------");
            try {

                // 应用每个补丁并更新 currentContent
                currentContent = patch.applyTo(currentContent);
                System.out.println("currentContent---------------------------:\n" + currentContent);
            } catch (PatchFailedException e) {
                System.err.println("Patch failed to apply: " + e.getMessage());
                e.printStackTrace();
                break;  // 如果应用失败，可以选择中断或继续处理其他补丁
            }
        }


        return currentContent;
    }

    public String getFileContent(int end)  {
        System.out.println("getFileContent---filePath: \n" + filePath);
        List<Patch<String>> patches = getEachFilePatches(getProjectVersions(), filePath,end);

        List<String> fileContent = applyPatches(patches);
        String FileContent = String.join("\n", fileContent);
        System.out.println("getFileContent---FileContent: \n" + FileContent);

        return FileContent;
    }

    public ChangeType getChangeType() {
        return changeType;
    }
}
