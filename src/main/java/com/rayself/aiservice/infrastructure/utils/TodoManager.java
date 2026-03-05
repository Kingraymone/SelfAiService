package com.rayself.aiservice.infrastructure.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TodoManager {
    private List<TodoItem> items = new ArrayList<>();

    public String update(List<Map<String, Object>> newItems) {
        if (newItems.size() > 20) {
            throw new IllegalArgumentException("Max 20 todos allowed");
        }
        List<TodoItem> validated = new ArrayList<>();
        int inProgressCount = 0;
        for (int i = 0; i < newItems.size(); i++) {
            Map<String, Object> item = newItems.get(i);
            String text = (String) item.getOrDefault("text", "");
            if (text.trim().isEmpty()) {
                throw new IllegalArgumentException("Item " + (i + 1) + ": text required");
            }
            String status = ((String) item.getOrDefault("status", "pending")).toLowerCase();
            if (!status.equals("pending") && !status.equals("in_progress") && !status.equals("completed")) {
                throw new IllegalArgumentException("Item " + (i + 1) + ": invalid status '" + status + "'");
            }
            if (status.equals("in_progress")) {
                inProgressCount++;
            }
            String id = (String) item.getOrDefault("id", String.valueOf(i + 1));
            validated.add(new TodoItem(id, text.trim(), status));
        }
        if (inProgressCount > 1) {
            throw new IllegalArgumentException("Only one task can be in_progress at a time");
        }
        this.items = validated;
        return render();
    }

    public String render() {
        if (items.isEmpty()) {
            return "No todos.";
        }
        StringBuilder sb = new StringBuilder();
        for (TodoItem item : items) {
            String marker;
            switch (item.getStatus()) {
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
                    break;
            }
            sb.append(String.format("%s #%s: %s\n", marker, item.getId(), item.getText()));
        }
        long done = items.stream().filter(i -> i.getStatus().equals("completed")).count();
        sb.append(String.format("\n(%d/%d completed)", done, items.size()));
        return sb.toString();
    }

    private static class TodoItem {
        private final String id;
        private final String text;
        private final String status;

        public TodoItem(String id, String text, String status) {
            this.id = id;
            this.text = text;
            this.status = status;
        }

        public String getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public String getStatus() {
            return status;
        }
    }
}