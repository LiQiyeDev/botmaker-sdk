package com.botmaker.sdk.api.vision;

import java.util.List;

/**
 * An ordered, non-empty collection of {@link ImageTemplate}s treated as a single value.
 *
 * <p>Lets the finder/clicker act on several templates at once: {@code find}/{@code click} over a
 * group keep first-match semantics (the first template, in order, that clears the confidence
 * threshold wins — cheap short-circuit), while {@link ImageFinder#findBest(ImageTemplateGroup)} /
 * {@link ImageClicker#clickBest(ImageTemplateGroup)} evaluate every template and pick the single
 * highest-scoring match. A group also serves as the "good" or "bad" set for the compare API
 * ({@link ImageFinder#findCompare}).
 *
 * <p>Immutable: the backing list is copied and unmodifiable.
 */
public record ImageTemplateGroup(List<ImageTemplate> templates) {

    public ImageTemplateGroup {
        if (templates == null || templates.isEmpty()) {
            throw new IllegalArgumentException("ImageTemplateGroup requires at least one template");
        }
        templates = List.copyOf(templates); // rejects null elements, produces an unmodifiable copy
    }

    public static ImageTemplateGroup of(ImageTemplate... templates) {
        if (templates == null || templates.length == 0) {
            throw new IllegalArgumentException("ImageTemplateGroup requires at least one template");
        }
        return new ImageTemplateGroup(List.of(templates));
    }

    public static ImageTemplateGroup of(List<ImageTemplate> templates) {
        return new ImageTemplateGroup(templates);
    }

    /** The templates as an array, for delegating to the {@code ImageTemplate...} finder methods. */
    public ImageTemplate[] toArray() {
        return templates.toArray(new ImageTemplate[0]);
    }
}
