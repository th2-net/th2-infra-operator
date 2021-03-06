/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.infraoperator.spec.helmrelease;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Version;
import lombok.SneakyThrows;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static com.exactpro.th2.infraoperator.util.JsonUtils.YAML_READER;

@SuppressWarnings("unchecked")
@JsonIgnoreProperties(ignoreUnknown = true)
@Group("helm.fluxcd.io")
@Version("v1")
@Kind("HelmRelease")
public class HelmRelease extends CustomResource<InstantiableMap, InstantiableMap> implements Namespaced {

    public static final int NAME_LENGTH_LIMIT = 26;

    private MergePutter mergePutter = new MergePutter();

    @SneakyThrows
    public static HelmRelease of(File customResourceFile) {
        return YAML_READER.readValue(customResourceFile, HelmRelease.class);
    }

    public static HelmRelease of(String customResourcePath) {
        return of(new File(customResourcePath));
    }

    public static HelmRelease of(Path customResourcePath) {
        return of(customResourcePath.toFile());
    }

    @JsonIgnore
    public Map<String, Object> getValuesSection() {
        var valuesAlias = "values";

        var values = (Map<String, Object>) getSpec().get(valuesAlias);

        if (Objects.isNull(values)) {
            var newValues = new LinkedHashMap<String, Object>();
            getSpec().put(valuesAlias, newValues);
            return newValues;
        }

        return values;
    }

    public void removeSpecProp(String key) {
        getSpec().remove(key);
    }

    public void putSpecProp(String key, Object value) {
        mergePutter.putValue(getSpec(), key, value);
    }

    public void putSpecProp(String key, Map<String, Object> values) {
        mergeSpecProp(0, key, values);
    }

    public void putSpecProp(Map<String, Object> values) {
        mergeSpecProp(0, values);
    }

    public void mergeSpecProp(String key, Map<String, Object> values) {
        mergeSpecProp(Integer.MAX_VALUE, key, values);
    }

    public void mergeSpecProp(Map<String, Object> values) {
        mergeSpecProp(Integer.MAX_VALUE, values);
    }

    public void mergeSpecProp(int depth, String key, Map<String, Object> values) {
        mergePutter.putValue(depth, getSpec(), key, values);
    }

    public void mergeSpecProp(int depth, Map<String, Object> values) {
        mergePutter.putValue(depth, getSpec(), values);
    }

    public void removeValue(String key) {
        getValuesSection().remove(key);
    }

    public void putValue(String key, Object value) {
        mergePutter.putValue(getValuesSection(), key, value);
    }

    public void putValue(String key, Map<String, Object> values) {
        mergeValue(0, key, values);
    }

    public void putValue(Map<String, Object> values) {
        mergeValue(0, values);
    }

    public void mergeValue(String key, Map<String, Object> values) {
        mergeValue(Integer.MAX_VALUE, key, values);
    }

    public void mergeValue(Map<String, Object> values) {
        mergeValue(Integer.MAX_VALUE, values);
    }

    public void mergeValue(int depth, String key, Map<String, Object> values) {
        mergePutter.putValue(depth, getValuesSection(), key, values);
    }

    public void mergeValue(int depth, Map<String, Object> values) {
        mergePutter.putValue(depth, getValuesSection(), values);
    }

    public String toString() {
        return "HelmRelease(super=" + super.toString() + ")";
    }

    private static class MergePutter {

        public void putValue(Map<String, Object> target, String key, Object value) {
            target.put(key, value);
        }

        public void putValue(int depth, Map<String, Object> target, String key, Map<String, Object> values) {

            var targetValue = target.get(key);

            if (Objects.nonNull(targetValue) && targetValue instanceof Map && depth > 0) {
                mergePut(depth, (Map<String, Object>) targetValue, values);
                return;
            }

            target.put(key, mutable(values));
        }

        public void putValue(int depth, Map<String, Object> target, Map<String, Object> values) {
            mergePut(depth, target, values);
        }

        private void mergePut(int depth, Map<String, Object> firstMap, Map<String, Object> secondMap) {
            mergePutRec(depth, 0, firstMap, secondMap);
        }

        private void mergePutRec(int depth, int currentDepth, Map<String, Object> firstMap,
                                 Map<String, Object> secondMap) {
            if (firstMap.isEmpty()) {
                firstMap.putAll(secondMap);
                return;
            }

            var forIteration = new LinkedHashMap<>(firstMap);

            for (var firstEntry : forIteration.entrySet()) {
                var firstKey = firstEntry.getKey();
                var firstValue = firstEntry.getValue();

                for (var secEntry : secondMap.entrySet()) {
                    var secKey = secEntry.getKey();
                    var secValue = secEntry.getValue();

                    if (firstKey.equals(secKey)) {
                        if (firstValue instanceof Map && secValue instanceof Map && depth > currentDepth) {
                            mergePutRec(depth, ++currentDepth, (Map<String, Object>) firstValue,
                                    (Map<String, Object>) secValue);
                        } else {
                            firstMap.put(secKey, secValue);
                        }
                    } else if (!firstMap.containsKey(secKey)) {
                        firstMap.put(secKey, secValue);
                    }
                }
            }
        }

        private Map<String, Object> mutable(Map<String, Object> map) {
            return new LinkedHashMap<>(map);
        }
    }
}
