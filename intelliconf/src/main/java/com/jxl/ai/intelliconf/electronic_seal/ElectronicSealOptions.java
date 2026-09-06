package com.jxl.ai.intelliconf.electronic_seal;

/**
 * Parameters used to render a visual seal layer on a PDF.
 */
public record ElectronicSealOptions(
        String name,
        String centerText,
        String page,
        String position,
        Float x,
        Float y,
        float size,
        float opacity
) {
}
