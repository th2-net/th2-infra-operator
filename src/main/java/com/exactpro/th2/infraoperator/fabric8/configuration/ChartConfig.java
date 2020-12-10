/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.infraoperator.fabric8.configuration;

import com.exactpro.th2.infraoperator.fabric8.util.JsonUtils;
import com.exactpro.th2.infraoperator.fabric8.util.Strings;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartConfig implements Cloneable {

    private String git;
    private String ref;
    private String path;

    ChartConfig() {
    }

    ChartConfig(String git, String ref, String path) {
        this.git = git;
        this.ref = ref;
        this.path = path;
    }

    public String getGit() {
        return git;
    }

    public String getRef() {
        return ref;
    }

    public String getPath() {
        return path;
    }

    public static ChartConfigBuilder builder() {
        return new ChartConfigBuilder();
    }

    public ChartConfig overrideWith(ChartConfig chartConfig) {

        try {
            ChartConfig overriddenConfig = (ChartConfig) super.clone();

            if (!Strings.isNullOrEmpty(chartConfig.getGit()))
                overriddenConfig.git = chartConfig.getGit();

            if (!Strings.isNullOrEmpty(chartConfig.getRef()))
                overriddenConfig.ref = chartConfig.getRef();

            if (!Strings.isNullOrEmpty(chartConfig.getPath()))
                overriddenConfig.path = chartConfig.getPath();

            return overriddenConfig;
        } catch (CloneNotSupportedException e) {
            throw new InternalError("Exception cloning " + ChartConfig.class.getSimpleName(), e);
        }
    }

    public Map<String, Object> toMap() {
        try {
            return JsonUtils.writeValueAsDeepMap(this);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Exception converting object", e);
        }
    }

    @Override
    public String toString() {
        return "ChartConfig{" + "git='" + git + '\'' + ", ref='" + ref + '\'' + ", path='" + path + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChartConfig)) return false;
        ChartConfig that = (ChartConfig) o;
        return Objects.equals(getGit(), that.getGit()) &&
            Objects.equals(getRef(), that.getRef()) &&
            Objects.equals(getPath(), that.getPath());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getGit(), getRef(), getPath());
    }

    public static class ChartConfigBuilder {

        private String git;
        private String ref;
        private String path;

        ChartConfigBuilder() {
        }

        public ChartConfigBuilder git(String git) {
            this.git = git;
            return this;
        }

        public ChartConfigBuilder ref(String ref) {
            this.ref = ref;
            return this;
        }

        public ChartConfigBuilder path(String path) {
            this.path = path;
            return this;
        }

        public ChartConfig build() {
            return new ChartConfig(git, ref, path);
        }
    }
}
