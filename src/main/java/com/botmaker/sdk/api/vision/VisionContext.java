package com.botmaker.sdk.api.vision;

import java.util.ArrayList;
import java.util.List;

/**
 * Global, thread-local context for vision operations. Provides access to the most recent
 * {@link MatchResult} from any {@link ImageFinder}, {@link ImageClicker}, or {@link ImageWaiter}
 * method call, enabling fluent coding patterns while preserving rich match information.
 *
 * <p>Every find/click/wait call in the vision API automatically updates the last match result
 * for the current thread. This lets you write fluent code like:
 *
 * <pre>{@code
 * if (ImageClicker.click(button)) {
 *     MatchResult last = VisionContext.getLastMatch();
 *     System.out.println("Clicked at " + last.getCenter() + " with confidence " + last.getConfidence());
 * }
 * }</pre>
 *
 * <p>For methods that return multiple results (like {@link ImageFinder#findAll}), the list
 * of results is also available:
 * <pre>{@code
 * int count = ImageFinder.findAll(template);
 * List<MatchResult> results = VisionContext.getLastMatchList();
 * }</pre>
 *
 * <p>The context is {@link ThreadLocal}, so it is automatically isolated per thread — safe for
 * concurrent bot execution. Use {@link #clearLastMatch()} to reset the context for the
 * current thread (e.g., at the start of a bot action).
 */
public final class VisionContext {

    private static final ThreadLocal<MatchResult> lastMatch = new ThreadLocal<>();
    private static final ThreadLocal<List<MatchResult>> lastMatchList = new ThreadLocal<>();

    private VisionContext() {}

    /**
     * Returns the most recent match result for the current thread, or {@link MatchResult#notFound()}
     * if no vision operation has been performed yet on this thread.
     *
     * @return the last match result, never null
     */
    public static MatchResult getLastMatch() {
        MatchResult result = lastMatch.get();
        return result != null ? result : MatchResult.notFound();
    }

    /**
     * Returns the most recent list of match results for the current thread, or an empty list
     * if no vision operation that returns multiple results has been performed yet.
     *
     * @return the last match result list, never null
     */
    public static List<MatchResult> getLastMatchList() {
        List<MatchResult> result = lastMatchList.get();
        return result != null ? result : new ArrayList<>();
    }

    /**
     * Returns whether the last match for the current thread was successful.
     * Equivalent to {@code getLastMatch().isFound()}.
     *
     * @return true if the last vision operation found a match
     */
    public static boolean lastMatchFound() {
        return getLastMatch().isFound();
    }

    /**
     * Clears the last match result and match list for the current thread.
     * Useful at the start of a bot action to ensure a clean state.
     */
    public static void clearLastMatch() {
        lastMatch.remove();
        lastMatchList.remove();
    }

    /**
     * Invokes the {@code action} with the last match result if it exists (i.e., the last vision
     * operation found a match). Does nothing if the last match was not found.
     *
     * @param action the consumer to invoke with the match result
     * @return true if a match existed and the action was invoked, false otherwise
     */
    public static boolean ifLastMatch(java.util.function.Consumer<MatchResult> action) {
        MatchResult result = getLastMatch();
        if (result.isFound()) {
            action.accept(result);
            return true;
        }
        return false;
    }

    /**
     * Internal method: updates the last match result for the current thread.
     * Called by vision API methods after each operation.
     *
     * @param result the match result to store
     */
    static void setLastMatch(MatchResult result) {
        lastMatch.set(result);
        lastMatchList.remove();
    }

    /**
     * Internal method: updates the last match result list for the current thread.
     * Called by vision API methods that return multiple results.
     *
     * @param results the list of match results to store
     */
    static void setLastMatchList(List<MatchResult> results) {
        lastMatchList.set(results);
        if (!results.isEmpty()) {
            lastMatch.set(results.get(0));
        } else {
            lastMatch.set(MatchResult.notFound());
        }
    }
}
