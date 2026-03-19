package com.rayself.aiservice.task;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TaskManager {

    private static Path tasksDir = Paths.get(".tasks").toAbsolutePath();
    ;
    private static int nextId = 0;

    public TaskManager() {
        // Default to a .tasks directory in the current working directory, similar to Python's WORKDIR
        tasksDir = Paths.get(".tasks").toAbsolutePath();
        init();
    }

    public TaskManager(String tasksDirPath) {
        tasksDir = Paths.get(tasksDirPath).toAbsolutePath();
        init();
    }

    private static void init() {
        try {
            if (!Files.exists(tasksDir)) {
                Files.createDirectories(tasksDir);
            }
            nextId = maxId() + 1;
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize TaskManager", e);
        }
    }

    private static int maxId() {
        try (Stream<Path> files = Files.list(tasksDir)) {
            return files.filter(p -> p.getFileName().toString().matches("task_\\d+\\.json"))
                    .map(p -> {
                        String name = p.getFileName().toString();
                        String idPart = name.substring(5, name.lastIndexOf('.'));
                        return Integer.parseInt(idPart);
                    })
                    .max(Integer::compareTo)
                    .orElse(0);
        } catch (IOException e) {
            return 0;
        }
    }

    public static Task load(int taskId) {
        Path path = tasksDir.resolve("task_" + taskId + ".json");
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Task " + taskId + " not found");
        }
        try {
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            return JSON.parseObject(content, Task.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load task " + taskId, e);
        }
    }

    public static void save(Task task) {
        Path path = tasksDir.resolve("task_" + task.getId() + ".json");
        try {
            String json = JSON.toJSONString(task, SerializerFeature.PrettyFormat);
            Files.write(path, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save task " + task.getId(), e);
        }
    }

    public static String create(String subject, String description) {
        Task task = new Task();
        task.setId(nextId++);
        task.setSubject(subject);
        task.setDescription(description == null ? "" : description);
        task.setStatus("pending");
        save(task);
        return JSON.toJSONString(task, SerializerFeature.PrettyFormat);
    }

    public static String get(int taskId) {
        return JSON.toJSONString(load(taskId), SerializerFeature.PrettyFormat);
    }

    public static String update(int taskId, String status, List<Integer> addBlockedBy, List<Integer> addBlocks) {
        Task task = load(taskId);

        if (status != null) {
            if (!Arrays.asList("pending", "in_progress", "completed").contains(status)) {
                throw new IllegalArgumentException("Invalid status: " + status);
            }
            task.setStatus(status);
            if ("completed".equals(status)) {
                clearDependency(taskId);
            }
        }

        if (addBlockedBy != null && !addBlockedBy.isEmpty()) {
            Set<Integer> currentBlockedBy = new HashSet<>(task.getBlockedBy());
            currentBlockedBy.addAll(addBlockedBy);
            task.setBlockedBy(new ArrayList<>(currentBlockedBy));
        }

        if (addBlocks != null && !addBlocks.isEmpty()) {
            Set<Integer> currentBlocks = new HashSet<>(task.getBlocks());
            currentBlocks.addAll(addBlocks);
            task.setBlocks(new ArrayList<>(currentBlocks));

            // Bidirectional update
            for (Integer blockedId : addBlocks) {
                try {
                    Task blockedTask = load(blockedId);
                    if (!blockedTask.getBlockedBy().contains(taskId)) {
                        blockedTask.getBlockedBy().add(taskId);
                        save(blockedTask);
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore if blocked task not found
                }
            }
        }

        save(task);
        return JSON.toJSONString(task, SerializerFeature.PrettyFormat);
    }

    public static void clearDependency(int completedId) {
        try (Stream<Path> files = Files.list(tasksDir)) {
            files.filter(p -> p.getFileName().toString().matches("task_\\d+\\.json"))
                    .forEach(p -> {
                        try {
                            String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                            Task t = JSON.parseObject(content, Task.class);
                            if (t.getBlockedBy() != null && t.getBlockedBy().contains(completedId)) {
                                t.getBlockedBy().remove(Integer.valueOf(completedId));
                                save(t);
                            }
                        } catch (IOException e) {
                            // Log or ignore
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear dependencies", e);
        }
    }

    public static String listAll() {
        List<Task> tasks = new ArrayList<>();
        try (Stream<Path> files = Files.list(tasksDir)) {
            List<Path> sortedFiles = files
                    .filter(p -> p.getFileName().toString().matches("task_\\d+\\.json"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toList());

            for (Path f : sortedFiles) {
                String content = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                tasks.add(JSON.parseObject(content, Task.class));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to list tasks", e);
        }

        if (tasks.isEmpty()) {
            return "No tasks.";
        }

        StringBuilder sb = new StringBuilder();
        for (Task t : tasks) {
            String marker;
            switch (t.getStatus()) {
                case "pending":
                    marker = "[ ]";
                    break;
                case "in_progress":
                    marker = "[>]";
                    break;
                case "completed":
                    marker = "[x]";
                    break;
                default:
                    marker = "[?]";
            }
            String blocked = (t.getBlockedBy() != null && !t.getBlockedBy().isEmpty()) ? " (blocked by: " + t.getBlockedBy() + ")" : "";
            sb.append(String.format("%s #%d: %s%s\n", marker, t.getId(), t.getSubject(), blocked));
        }
        // Remove last newline if exists, though join usually doesn't add one at the end if we used join.
        // Python's join doesn't add a trailing newline, but here I'm appending \n.
        // Let's use String.join logic or trim.
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }
}
