package com.rayself.aiservice.infrastructure.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileUtils {

    private static final Path WORKDIR = Paths.get(System.getProperty("user.dir"));

    /**
     * Gets the active charset of the Windows console by executing the 'chcp' command.
     * This is the most reliable way to determine the encoding for command output.
     * @return The detected Charset, or the default JVM charset as a fallback.
     */
    private static Charset getWindowsConsoleCharset() {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "chcp");
            Process p = pb.start();
            
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.US_ASCII))) {
                output = reader.readLine();
            }
            p.waitFor();

            if (output != null) {
                // The output is like "Active code page: 936"
                Pattern pattern = Pattern.compile("\\d+");
                Matcher matcher = pattern.matcher(output);
                if (matcher.find()) {
                    String cp = matcher.group(0);
                    return Charset.forName("cp" + cp);
                }
            }
        } catch (IOException | InterruptedException | UnsupportedCharsetException e) {
            // Fallback in case of any error
            System.err.println("Failed to determine console charset, falling back to default. Error: " + e.getMessage());
        }
        return Charset.defaultCharset();
    }

    public static Path safePath(String p) throws IOException {
        Path path = WORKDIR.resolve(p).normalize();
        if (!path.startsWith(WORKDIR)) {
            throw new SecurityException("Path escapes workspace: " + p);
        }
        return path;
    }

    public static String runBash(String command) {
        List<String> dangerous = Arrays.asList("rm -rf /", "sudo", "shutdown", "reboot", "> /dev/");
        if (dangerous.stream().anyMatch(command::contains)) {
            return "Error: Dangerous command blocked";
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            String os = System.getProperty("os.name").toLowerCase();
            boolean isWindows = os.contains("win");

            if (isWindows) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("/bin/bash", "-c", command);
            }
            processBuilder.directory(WORKDIR.toFile());
            Process process = processBuilder.start();

            // Determine the correct charset for reading the process output
            Charset charset = isWindows ? getWindowsConsoleCharset() : StandardCharsets.UTF_8;

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroy();
                return "Error: Timeout (120s)";
            }

            String result = output.toString().trim();
            return result.length() > 50000 ? result.substring(0, 50000) : (result.isEmpty() ? "(no output)" : result);
        } catch (IOException | InterruptedException e) {
            return "Error: " + e.getMessage();
        }
    }

    public static String runRead(String path, Integer limit) {
        try {
            Path safePath = safePath(path);
            if (!Files.exists(safePath)) {
                return "Error: File not found at " + path;
            }
            String content = Files.readString(safePath, StandardCharsets.UTF_8);
            String[] lines = content.split("\n");
            if (limit != null && limit < lines.length) {
                return String.join("\n", Arrays.copyOfRange(lines, 0, limit)) +
                       "\n... (" + (lines.length - limit) + " more lines)";
            }
            return content.length() > 50000 ? content.substring(0, 50000) : content;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static String runWrite(String path, String content) {
        try {
            Path safePath = safePath(path);
            Files.createDirectories(safePath.getParent());
            Files.writeString(safePath, content, StandardCharsets.UTF_8);
            return "Wrote " + content.getBytes(StandardCharsets.UTF_8).length + " bytes to " + path;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static String runEdit(String path, String oldText, String newText) {
        try {
            Path safePath = safePath(path);
            if (!Files.exists(safePath)) {
                return "Error: File not found at " + path;
            }
            String content = Files.readString(safePath, StandardCharsets.UTF_8);
            if (!content.contains(oldText)) {
                return "Error: Text not found in " + path;
            }
            String newContent = content.replaceFirst(oldText, newText);
            Files.writeString(safePath, newContent, StandardCharsets.UTF_8);
            return "Edited " + path;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println(runBash("dir"));
        System.out.println(runWrite(".\\test\\test1.txt", "abc"));
    }
}