package com.jxl.ai.intelliconf.mail.infrastructure;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemplateRenderService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    /**
     * Missing variables are kept as-is so organizers can see unresolved placeholders.
     */
    public String render(String template, Map<String, Object> variables) {
        if (template == null) {
            return null;
        }
        Map<String, Object> safeVariables = variables == null ? Collections.emptyMap() : variables;
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = safeVariables.get(name);
            if (value == null) {
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(String.valueOf(value)));
            }
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
