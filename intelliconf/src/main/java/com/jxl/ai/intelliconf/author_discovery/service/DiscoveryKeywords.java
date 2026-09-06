package com.jxl.ai.intelliconf.author_discovery.service;

import java.util.*;

/** Keep topic phrases intact; isolated generic words cause unrelated results. */
public final class DiscoveryKeywords {
    private DiscoveryKeywords() {}
    public static List<String> normalize(List<String> values) {
        Set<String> phrases = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) continue;
            String topic = value.toLowerCase(Locale.ROOT)
                    .replaceFirst("^.*?international conference (?:on|in)\\s+", "");
            for (String part : topic.split("[,;；，|\\n]+")) {
                String phrase = part.trim().replaceAll("\\s+", " ");
                if (phrase.length() >= 3 && phrase.length() <= 120) phrases.add(phrase);
            }
        }
        String context = String.join(" ", phrases);
        if (context.contains("education") || context.contains("教育")) {
            if (context.contains("intelligen") || context.contains("ai") || context.contains("智能")) {
                phrases.addAll(List.of("artificial intelligence in education", "intelligent tutoring systems", "educational data mining", "learning analytics"));
            }
        }
        phrases.removeAll(Set.of("ai", "education", "intelligent", "research", "conference"));
        return phrases.stream().limit(12).toList();
    }
}
