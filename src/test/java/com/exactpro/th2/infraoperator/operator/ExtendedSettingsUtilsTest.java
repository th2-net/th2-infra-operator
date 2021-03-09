package com.exactpro.th2.infraoperator.operator;

import com.exactpro.th2.infraoperator.util.ExtendedSettingsUtils;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ExtendedSettingsUtilsTest {

    private static final String SERVICE_ALIAS = "service";
    private static final String ENABLED_ALIAS = "enabled";

    @Test
    void testServiceNull(){
        Map<String, Object> extendedSettings = Collections.singletonMap(SERVICE_ALIAS, null);
        assertDoesNotThrow(() -> ExtendedSettingsUtils.convertServiceEnabled(extendedSettings, Boolean::valueOf));
    }

    @Test
    void testServiceEmptyMap(){
        Map<String, Object> extendedSettings = Collections.singletonMap(SERVICE_ALIAS, Collections.emptyMap());
        assertDoesNotThrow(() -> ExtendedSettingsUtils.convertServiceEnabled(extendedSettings, Boolean::valueOf));
    }

    @Test
    void testServiceEnabledTrue(){
        Map<String, Object> service = new HashMap<>();
        service.put(ENABLED_ALIAS, "true");
        Map<String, Object> extendedSettings = Map.of(SERVICE_ALIAS, service);
        assertDoesNotThrow(() -> ExtendedSettingsUtils.convertServiceEnabled(extendedSettings, Boolean::valueOf));

        Map<String, Object> serviceModified = (Map<String, Object>)extendedSettings.get(SERVICE_ALIAS);
        assertTrue(serviceModified.get(ENABLED_ALIAS) instanceof Boolean);
        assertTrue((Boolean)serviceModified.get(ENABLED_ALIAS));
    }

    @Test
    void testServiceEnabledFalse(){
        Map<String, Object> service = new HashMap<>();
        service.put(ENABLED_ALIAS, "false");
        Map<String, Object> extendedSettings = Map.of(SERVICE_ALIAS, service);
        assertDoesNotThrow(() -> ExtendedSettingsUtils.convertServiceEnabled(extendedSettings, Boolean::valueOf));

        Map<String, Object> serviceModified = (Map<String, Object>)extendedSettings.get(SERVICE_ALIAS);
        assertTrue(serviceModified.get(ENABLED_ALIAS) instanceof Boolean);
        assertFalse((Boolean)serviceModified.get(ENABLED_ALIAS));
    }
}
