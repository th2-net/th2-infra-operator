package com.exactpro.th2.infraoperator.operator;

import com.exactpro.th2.infraoperator.util.ExtendedSettingsUtils;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ExtendedSettingsUtilsTest {

    private static final String COMPONENT_ALIAS = "component";
    private static final String EXTENDED_SETTINGS_ALIAS = "extendedSettings";
    private static final String EXTERNAL_BOX_ALIAS = "externalBox";
    private static final String SERVICE_ALIAS = "service";
    private static final String ENABLED_ALIAS = "enabled";

    @Test
    void testNull() {
        Map<String, Object> values = new HashMap<>();
        Map<String, Object> component = new HashMap<>();
        component.put(EXTENDED_SETTINGS_ALIAS, null);
        values.put(COMPONENT_ALIAS, component);
        assertDoesNotThrow(() -> ExtendedSettingsUtils.convertAllBooleans(values, Boolean::valueOf));
    }

    @Test
    void testServiceEmptyMap() {
        Map<String, Object> values = new HashMap<>();
        Map<String, Object> component = new HashMap<>();
        component.put(EXTENDED_SETTINGS_ALIAS, Collections.emptyMap());
        values.put(COMPONENT_ALIAS, component);
        assertDoesNotThrow(() -> ExtendedSettingsUtils.convertAllBooleans(values, Boolean::valueOf));
    }

    @Test
    void testNonMapElement() {

        Map<String, Object> service = Collections.emptyMap();
        List<Object> notMap = Collections.singletonList(service);
        Map<String, Object> extendedSettings = Collections.singletonMap(SERVICE_ALIAS, notMap);
        Map<String, Object> values = new HashMap<>();
        Map<String, Object> component = new HashMap<>();
        component.put(EXTENDED_SETTINGS_ALIAS, extendedSettings);
        values.put(COMPONENT_ALIAS, component);
        assertThrows(ClassCastException.class, () -> ExtendedSettingsUtils.convertAllBooleans(values, Boolean::valueOf));
    }

    @Test
    void testEnabledTrue() {
        Map<String, Object> service = new HashMap<>();
        service.put(ENABLED_ALIAS, "true");
        Map<String, Object> externalBox = new HashMap<>();
        externalBox.put(ENABLED_ALIAS, "true");
        Map<String, Object> extendedSettings = Map.of(SERVICE_ALIAS, service, EXTERNAL_BOX_ALIAS, externalBox);
        Map<String, Object> component = Map.of(EXTENDED_SETTINGS_ALIAS, extendedSettings);
        Map<String, Object> values = Map.of(COMPONENT_ALIAS, component);

        assertDoesNotThrow(() -> ExtendedSettingsUtils.convertAllBooleans(values, Boolean::valueOf));

        assertTrue(service.get(ENABLED_ALIAS) instanceof Boolean);
        assertTrue((Boolean) service.get(ENABLED_ALIAS));
        assertTrue(externalBox.get(ENABLED_ALIAS) instanceof Boolean);
        assertTrue((Boolean) externalBox.get(ENABLED_ALIAS));
    }

    @Test
    void testServiceEnabledFalse() {
        Map<String, Object> service = new HashMap<>();
        service.put(ENABLED_ALIAS, "false");
        Map<String, Object> externalBox = new HashMap<>();
        externalBox.put(ENABLED_ALIAS, "false");
        Map<String, Object> extendedSettings = Map.of(SERVICE_ALIAS, service, EXTERNAL_BOX_ALIAS, externalBox);
        Map<String, Object> component = Map.of(EXTENDED_SETTINGS_ALIAS, extendedSettings);
        Map<String, Object> values = Map.of(COMPONENT_ALIAS, component);

        assertDoesNotThrow(() -> ExtendedSettingsUtils.convertAllBooleans(values, Boolean::valueOf));

        assertTrue(service.get(ENABLED_ALIAS) instanceof Boolean);
        assertFalse((Boolean) service.get(ENABLED_ALIAS));
        assertTrue(externalBox.get(ENABLED_ALIAS) instanceof Boolean);
        assertFalse((Boolean) externalBox.get(ENABLED_ALIAS));
    }
}
