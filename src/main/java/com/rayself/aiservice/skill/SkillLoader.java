package com.rayself.aiservice.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SkillLoader {

    private final Path skillsDir;
    private final Map<String, Map<String, Object>> skills = new HashMap<>();

    public SkillLoader(Path skillsDir) {
        this.skillsDir = skillsDir;
        loadAll();
    }

    private void loadAll() {
        if (!Files.exists(skillsDir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(skillsDir)) {
            paths.filter(p -> p.getFileName().toString().equals("SKILL.md"))
                    .sorted()
                    .forEach(this::loadSkill);
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan skills directory", e);
        }
    }

    private void loadSkill(Path file) {
        try {
            String text = Files.readString(file);

            ParseResult result = parseFrontmatter(text);

            Map<String, String> meta = result.meta;
            String body = result.body;

            String name = meta.getOrDefault("name", file.getParent().getFileName().toString());

            Map<String, Object> skill = new HashMap<>();
            skill.put("meta", meta);
            skill.put("body", body);
            skill.put("path", file.toString());

            skills.put(name, skill);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class ParseResult {
        Map<String, String> meta;
        String body;

        ParseResult(Map<String, String> meta, String body) {
            this.meta = meta;
            this.body = body;
        }
    }

    /**
     * Parse YAML frontmatter between --- delimiters.
     */
    private ParseResult parseFrontmatter(String text) {

        Pattern pattern = Pattern.compile("^---\\n(.*?)\\n---\\n(.*)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        if (!matcher.find()) {
            return new ParseResult(new HashMap<>(), text);
        }

        Map<String, String> meta = new HashMap<>();

        String[] lines = matcher.group(1).trim().split("\\n");

        for (String line : lines) {
            int idx = line.indexOf(":");
            if (idx > 0) {
                String key = line.substring(0, idx).trim();
                String val = line.substring(idx + 1).trim();
                meta.put(key, val);
            }
        }

        String body = matcher.group(2).trim();

        return new ParseResult(meta, body);
    }

    /**
     * Layer 1: short descriptions for system prompt
     */
    public String getDescriptions() {

        if (skills.isEmpty()) {
            return "(no skills available)";
        }

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, Map<String, Object>> entry : skills.entrySet()) {

            String name = entry.getKey();
            Map<String, Object> skill = entry.getValue();

            Map<String, String> meta = (Map<String, String>) skill.get("meta");

            String desc = meta.getOrDefault("description", "No description");
            String tags = meta.getOrDefault("tags", "");

            String line = "  - " + name + ": " + desc;

            if (!tags.isEmpty()) {
                line += " [" + tags + "]";
            }

            sb.append(line).append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * Layer 2: full skill body returned in tool_result
     */
    public String getContent(String name) {

        Map<String, Object> skill = skills.get(name);

        if (skill == null) {
            return "Error: Unknown skill '" + name + "'. Available: " +
                    String.join(", ", skills.keySet());
        }

        String body = (String) skill.get("body");

        return "<skill name=\"" + name + "\">\n" +
                body +
                "\n</skill>";
    }
    // Static instance similar to Python's global SKILL_LOADER
    private static final Path SKILLS_DIR = Paths.get(System.getProperty("user.dir") + System.getProperty("file.separator") + "skills"); // WORKDIR needs to be defined
    public static final SkillLoader SKILL_LOADER = new SkillLoader(SKILLS_DIR);

    public static void main(String[] args) {
        System.out.println(SKILL_LOADER.getDescriptions());
    }
}


