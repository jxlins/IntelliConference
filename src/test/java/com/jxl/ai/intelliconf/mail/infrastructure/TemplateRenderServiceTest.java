package com.jxl.ai.intelliconf.mail.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateRenderServiceTest {

    @Test
    void renderReplacesVariablesAndKeepsMissingPlaceholder() {
        TemplateRenderService service = new TemplateRenderService();

        String rendered = service.render(
                "Dear {{name}}, {{conferenceName}} received {{paperTitle}}. {{missing}}",
                Map.of("name", "Alice", "conferenceName", "CSE 2026", "paperTitle", "AI Paper")
        );

        assertEquals("Dear Alice, CSE 2026 received AI Paper. {{missing}}", rendered);
    }
}
