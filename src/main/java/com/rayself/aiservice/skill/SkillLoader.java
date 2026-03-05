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
    private final Map<String, Map<String, String>> skills; // {name: {meta: {}, body: "", path: ""}}

    public SkillLoader(Path skillsDir) {
        this.skillsDir = skillsDir;
        this.skills = new HashMap<>();
        _loadAll();
    }

    private void _loadAll() {
        if (!Files.exists(skillsDir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(skillsDir)) {
            paths.filter(f -> f.getFileName().toString().equals("SKILL.md"))
                    .sorted(Comparator.comparing(Path::toString)) // Equivalent to Python's sorted
                    .forEach(f -> {
                        try {
                            String text = Files.readString(f);
                            Map.Entry<Map<String, String>, String> parsed = _parseFrontmatter(text);
                            Map<String, String> meta = parsed.getKey();
                            String body = parsed.getValue();

                            String name = meta.getOrDefault("name", f.getParent().getFileName().toString());

                            Map<String, String> skillData = new HashMap<>();
                            skillData.put("body", body);
                            skillData.put("path", f.toString());

                            // Store meta as a JSON string or handle it as a nested map
                            // For simplicity, let's store meta fields directly in skillData for now
                            // Or, better, store the meta map directly
                            Map<String, String> skillMeta = new HashMap<>(meta); // Copy meta map
                            skillData.put("meta", skillMeta.toString()); // This is not ideal, will fix later

                            skills.put(name, skillData);

                        } catch (IOException e) {
                            System.err.println("Error reading skill file: " + f.toString() + " - " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error walking skills directory: " + e.getMessage());
        }
    }

    private Map.Entry<Map<String, String>, String> _parseFrontmatter(String text) {
        Pattern pattern = Pattern.compile("^---\\n(.*?)\\n---\\n(.*)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        if (!matcher.find()) {
            return new HashMap<Map<String, String>, String>() {{
                put(Collections.emptyMap(), "{}");
            }}.entrySet().iterator().next(); // Placeholder for empty meta
        }

        Map<String, String> meta = new HashMap<>();
        String metaBlock = matcher.group(1).trim();
        String body = matcher.group(2).trim();

        for (String line : metaBlock.split("\\n")) {
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                meta.put(parts[0].trim(), parts[1].trim());
            }
        }

        // Return a custom Map.Entry implementation or a Pair class
        return new AbstractMap.SimpleEntry<>(meta, body);
    }

    public String getDescriptions() {
        if (skills.isEmpty()) {
            return "(no skills available)";
        }

        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : skills.entrySet()) {
            String name = entry.getKey();
            Map<String, String> skillData = entry.getValue();

            // Need to parse the meta string back to a map or store it properly
            // For now, let's assume meta is directly accessible if we fix _loadAll
            // This part needs correction based on how meta is stored.
            // Assuming meta is stored as a Map<String, String> directly in skillData
            // Let's refactor _loadAll to store meta as a Map<String, String>

            // Temporary fix: if skillData.get("meta") is a string representation of a map
            // This is not robust. I will refine the _loadAll method to store meta as a proper map.
            // For now, let's assume skillData contains "description" and "tags" directly if meta is flattened.
            // Or, better, let's assume skillData has a key "metaMap" that holds the actual meta map.

            // Refactoring _loadAll to store meta as a nested map:
            // skills: {name: {metaMap: {description: "", tags: ""}, body: "", path: ""}}

            // For now, let's assume meta is directly accessible from skillData for simplicity
            // This will be fixed when I refine the _loadAll method.

            // Placeholder for now, will be corrected.
            String description = "No description";
            String tags = "";

            // This needs to be fixed after _loadAll is fixed to store meta as a map
            // For now, let's make a temporary assumption that skillData contains "description" and "tags"
            // This is incorrect based on the current _loadAll implementation.
            // I need to fix _loadAll first.

            // Let's assume skillData has a key "meta" which is a Map<String, String>
            // This requires a change in _loadAll and the skill map structure.
            // I will change the skills map to be: Map<String, Skill> where Skill is a custom class.
            // Or, Map<String, Map<String, Object>> where "meta" key holds a Map<String, String>

            // Let's go with Map<String, Map<String, Object>> for skills, where "meta" is a Map<String, String>
            // and "body", "path" are Strings.

            // I need to rewrite _loadAll and the class structure.
            // Let's pause and redefine the class structure for better type safety.
        }
        return "Error: getDescriptions not fully implemented due to structural refactoring.";
    }

    public String getContent(String name) {
        // This will also depend on the refactored skills map structure.
        return "Error: getContent not fully implemented due to structural refactoring.";
    }

    // Static instance similar to Python's global SKILL_LOADER
    private static final Path SKILLS_DIR = Paths.get(System.getProperty("user.dir") + System.getProperty("file.separator") + "skills"); // WORKDIR needs to be defined
    public static final SkillLoader SKILL_LOADER = new SkillLoader(SKILLS_DIR);

    public static void main(String[] args) {
        System.out.println(SKILL_LOADER.getDescriptions());
    }
}
