package com.threeline.merchantwallet.common.context;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.function.Supplier;

public final class RequestContext {

    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_ACTOR = "actor";
    public static final String ANONYMOUS = "anonymous";

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
        MDC.put(MDC_REQUEST_ID, requestId);
    }

    public static String requestId() {
        return REQUEST_ID.get();
    }

    public static void setActor(String actor) {
        ACTOR.set(actor);
        MDC.put(MDC_ACTOR, actor);
    }

    public static String actor() {
        String actor = ACTOR.get();
        return actor != null ? actor : ANONYMOUS;
    }

    public static void clear() {
        REQUEST_ID.remove();
        ACTOR.remove();
        MDC.remove(MDC_REQUEST_ID);
        MDC.remove(MDC_ACTOR);
    }

    public static <T> T callAs(String actor, Supplier<T> work) {
        setRequestId("job-" + UUID.randomUUID());
        setActor(actor);
        try {
            return work.get();
        } finally {
            clear();
        }
    }
}
