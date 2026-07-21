package com.taskmesh.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SharedMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(SharedMemoryStore.class);

    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    public void put(String caseId, String key, Object value) {
        String compositeKey = compositeKey(caseId, key);
        store.put(compositeKey, value);
        log.debug("Stored value for key={}", compositeKey);
    }

    public Object get(String caseId, String key) {
        String compositeKey = compositeKey(caseId, key);
        Object value = store.get(compositeKey);
        log.debug("Retrieved value for key={}", compositeKey);
        return value;
    }

    public void remove(String caseId) {
        String prefix = caseId + ":";
        store.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public boolean hasKey(String caseId, String key) {
        return store.containsKey(compositeKey(caseId, key));
    }

    public Optional<Object> getOptional(String caseId, String key) {
        return Optional.ofNullable(get(caseId, key));
    }

    private String compositeKey(String caseId, String key) {
        return caseId + ":" + key;
    }
}
