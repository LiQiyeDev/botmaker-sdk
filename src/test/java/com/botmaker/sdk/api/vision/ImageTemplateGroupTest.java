package com.botmaker.sdk.api.vision;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link ImageTemplateGroup} — the immutable, non-empty multi-template value. No display or
 * OpenCV needed (templates are never loaded here).
 */
class ImageTemplateGroupTest {

    private static ImageTemplate t(String name) {
        return new ImageTemplate(name + ".png");
    }

    @Test
    void ofVarargsHoldsTemplatesInOrder() {
        ImageTemplateGroup g = ImageTemplateGroup.of(t("a"), t("b"), t("c"));
        assertEquals(3, g.templates().size());
        assertEquals("a", g.templates().get(0).getId());
        assertEquals(3, g.toArray().length);
    }

    @Test
    void ofListCopiesInput() {
        List<ImageTemplate> src = new ArrayList<>(List.of(t("a"), t("b")));
        ImageTemplateGroup g = ImageTemplateGroup.of(src);
        src.add(t("c")); // mutating the source must not affect the group
        assertEquals(2, g.templates().size());
    }

    @Test
    void templatesIsUnmodifiable() {
        ImageTemplateGroup g = ImageTemplateGroup.of(t("a"));
        assertThrows(UnsupportedOperationException.class, () -> g.templates().add(t("b")));
    }

    @Test
    void emptyIsRejected() {
        assertThrows(IllegalArgumentException.class, ImageTemplateGroup::of);
        assertThrows(IllegalArgumentException.class, () -> ImageTemplateGroup.of(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ImageTemplateGroup(null));
    }

    @Test
    void nullElementIsRejected() {
        assertThrows(NullPointerException.class,
                () -> ImageTemplateGroup.of(Arrays.asList(t("a"), null)));
    }
}
