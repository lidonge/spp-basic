package io.seata.tm.api;

import java.util.ArrayList;
import java.util.List;

public class GlobalTransaction {
    private final List<String> events = new ArrayList<>();

    public void begin(int timeoutMillis, String name) {
        events.add("begin:" + timeoutMillis + ":" + name);
    }

    public void commit() {
        events.add("commit");
    }

    public void rollback() {
        events.add("rollback");
    }

    public List<String> events() {
        return List.copyOf(events);
    }
}
