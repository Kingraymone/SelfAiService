package com.rayself.aiservice.app;

import com.alibaba.fastjson.JSONObject;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ToolAppService {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    /**
     * 获取系统相关的命令解释器
     */
    public static List<String> getShellCommand(String command) {
        List<String> cmdList = new ArrayList<>();

        if (IS_WINDOWS) {
            // Windows使用cmd.exe
            cmdList.add("cmd.exe");
            cmdList.add("/c");
            cmdList.add(command);
        } else {
            // Linux/Unix/Mac使用/bin/sh
            cmdList.add("/bin/sh");
            cmdList.add("-c");
            cmdList.add(command);
        }

        return cmdList;
    }

    @Tool
    public static String executeCommand(JSONObject jsonObject) {
        String command = jsonObject.getString("command");
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        try {
            Process process = Runtime.getRuntime().exec(getShellCommand(command).toArray(new String[0]));

            // 读取正常输出流
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            // 读取错误流
            Thread errorReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            outputReader.start();
            errorReader.start();

            int exitCode = process.waitFor();
            outputReader.join();
            errorReader.join();

            if (exitCode != 0) {
                System.err.println("Command failed with exit code: " + exitCode);
                System.err.println("Error: " + error.toString());
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return output.toString();
    }
}
