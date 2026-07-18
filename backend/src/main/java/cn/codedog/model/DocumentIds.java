package cn.codedog.model;

import java.util.UUID;

public final class DocumentIds {
    private DocumentIds() {}

    public static String newId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
