package com.taskmesh.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SharedMemoryStoreTest {

    private SharedMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new SharedMemoryStore();
    }

    @Test
    void putThenGetReturnsSameValue() {
        store.put("case-1", "facts", "value-a");

        assertEquals("value-a", store.get("case-1", "facts"));
    }

    @Test
    void getMissingKeyReturnsNullAndOptionalEmpty() {
        assertNull(store.get("case-1", "missing"));
        assertTrue(store.getOptional("case-1", "missing").isEmpty());
    }

    @Test
    void hasKeyTrueAfterPutFalseForUnknown() {
        store.put("case-1", "facts", "data");

        assertTrue(store.hasKey("case-1", "facts"));
        assertFalse(store.hasKey("case-1", "unknown"));
    }

    @Test
    void removeDeletesAllKeysForCaseIdOnly() {
        store.put("case-1", "facts", "f1");
        store.put("case-1", "laws", "l1");
        store.put("case-2", "facts", "f2");

        store.remove("case-1");

        assertNull(store.get("case-1", "facts"));
        assertNull(store.get("case-1", "laws"));
        assertEquals("f2", store.get("case-2", "facts"));
    }

    @Test
    void keysAreIsolatedPerCaseId() {
        store.put("case-1", "facts", "alpha");
        store.put("case-2", "facts", "beta");

        assertEquals("alpha", store.get("case-1", "facts"));
        assertEquals("beta", store.get("case-2", "facts"));
    }

    @Test
    void getOptionalReturnsValueWhenPresent() {
        store.put("case-1", "risk", 42);

        Optional<Object> result = store.getOptional("case-1", "risk");

        assertTrue(result.isPresent());
        assertEquals(42, result.get());
    }
}
