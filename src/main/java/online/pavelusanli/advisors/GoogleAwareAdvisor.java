package online.pavelusanli.advisors;

public final class GoogleAwareAdvisor {

    /**
     * Advisor context key. Set to {@code true} when the user has Google tools injected,
     * so downstream advisors (RAG, query expansion) skip their processing.
     */
    public static final String IS_TOOL_REQUEST = "IS_TOOL_REQUEST";

    /**
     * Advisor context key carrying the authenticated user's ID so advisors can
     * perform per-user lookups when needed.
     */
    public static final String USER_ID = "USER_ID";

    private GoogleAwareAdvisor() {}
}