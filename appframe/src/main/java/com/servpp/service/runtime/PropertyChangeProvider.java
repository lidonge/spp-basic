package com.servpp.service.runtime;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** Optional model tracking extension. Repository identity/revision evidence is a separate core contract. */
public interface PropertyChangeProvider {
    Object createState();
    void modified(Object participant, String property);
    void loading(Runnable action);
    Map<PropertyChanges.State, PropertyChanges.Checkpoint> checkpoint(Collection<?> participants);
    void restoreCheckpoint(Map<PropertyChanges.State, PropertyChanges.Checkpoint> states);
    Runnable acknowledgement(Object participant);
    void clean(Object participant);
    void copy(Object source, Object target);
    void project(Object source, Object target);
    void restore(Object target, Set<String> paths);
    Set<String> paths(Object value, Object baseline);
    Set<String> paths(Object value);
    void mapped(Object source, String from, Object target, String to, Runnable assignment);
}
