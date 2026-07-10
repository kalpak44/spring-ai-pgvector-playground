package online.pavelusanli.model.common;

public enum SyncEventType {
    SYNC_STARTED,
    DOC_SKIPPED,
    DOC_FETCHED,
    DOC_CHUNKED,
    DOC_REMOVED,
    SYNC_COMPLETE,
    SYNC_ERROR
}