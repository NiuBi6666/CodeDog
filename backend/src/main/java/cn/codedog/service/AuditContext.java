package cn.codedog.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class AuditContext {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
    private AuditContext() {}

    public static State begin(String method, String path) {
        State state = new State();
        state.requestId = UUID.randomUUID().toString();
        state.httpMethod = method;
        state.requestPath = path;
        CURRENT.set(state);
        return state;
    }
    public static State current() { return CURRENT.get(); }
    public static boolean active() { return CURRENT.get() != null; }
    public static void error(String message) {
        State state = CURRENT.get();
        if (state != null) state.errorMessage = message;
    }
    public static void clear() { CURRENT.remove(); }

    public static final class State {
        String requestId;
        String httpMethod;
        String requestPath;
        String action;
        String ownerUsername;
        String actorType;
        String actorId;
        String actorName;
        String module;
        String eventType;
        String eventCode;
        String targetType;
        String targetId;
        String errorMessage;
        Integer responseCount;
        final Map<String,Object> changes = new LinkedHashMap<>();
        final Map<String,Object> details = new LinkedHashMap<>();

        public String requestId() { return requestId; }
        public void owner(String value) { ownerUsername = value; }
        public void actor(String type, String id, String name) { actorType=type; actorId=id; actorName=name; }
        public void event(String moduleValue, String type, String code) {
            module=moduleValue; eventType=type; eventCode=code;
        }
        public void target(String type, String id) { targetType=type; targetId=id; }
        public void detail(String key, Object value) { if (value != null) details.put(key,value); }
        public void changes(Map<String,Object> value) { changes.clear(); changes.putAll(value); }
        public void action(String value) { action=value; }
        public void responseCount(int value) { responseCount=Math.max(value,0); }
    }
}
