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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Map;
import java.util.Objects;

@JsonDeserialize(builder = ChartConfig.ChartConfigBuilder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartConfig implements Cloneable {

    private String git;
    private String ref;
    private String path;

    private String repository;
    private String name;
    private String version;

    ChartConfig() {
    }

    public ChartConfig(String git, String ref, String path, String repository, String name, String version) {
        this.git = git;
        this.ref = ref;
        this.path = path;
        this.repository = repository;
        this.name = name;
        this.version = version;
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

    public String getRepository() {
        return repository;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public static ChartConfigBuilder builder() {
        return new ChartConfigBuilder();
    }

    public ChartConfig overrideWith(ChartConfig chartConfig) {
        try {
            ChartConfig overriddenConfig = (ChartConfig) super.clone();

            // if chartConfig is nonempty and valid it will replace existing config
            // if chartConfig is empty nothing will change
            if (isValid(chartConfig)) {
                if (!isEmpty(chartConfig)) {
                    overriddenConfig.git = chartConfig.getGit();
                    overriddenConfig.ref = chartConfig.getRef();
                    overriddenConfig.path = chartConfig.getPath();
                    overriddenConfig.repository = chartConfig.getRepository();
                    overriddenConfig.name = chartConfig.getName();
                    overriddenConfig.version = chartConfig.getVersion();
                    return overriddenConfig;
                }
            }
            // if chartConfig is not valid it will be combined with existing config
            else {
                if (!Strings.isNullOrEmpty(chartConfig.getGit()))
                    overriddenConfig.git = chartConfig.getGit();

                if (!Strings.isNullOrEmpty(chartConfig.getRef()))
                    overriddenConfig.ref = chartConfig.getRef();

                if (!Strings.isNullOrEmpty(chartConfig.getPath()))
                    overriddenConfig.path = chartConfig.getPath();

                if (!Strings.isNullOrEmpty(chartConfig.getRepository()))
                    overriddenConfig.repository = chartConfig.getRepository();

                if (!Strings.isNullOrEmpty(chartConfig.getName()))
                    overriddenConfig.name = chartConfig.getName();

                if (!Strings.isNullOrEmpty(chartConfig.getVersion()))
                    overriddenConfig.version = chartConfig.getVersion();
            }
            if (!isValid(overriddenConfig))
                throw new IllegalArgumentException("Exception overriding " + ChartConfig.class.getSimpleName());

            return overriddenConfig;
        } catch (CloneNotSupportedException e) {
            throw new InternalError("Exception cloning " + ChartConfig.class.getSimpleName(), e);
        }
    }

    private static boolean isValid(ChartConfig config) {
        // check if ALL fields are null or empty
        if (isEmpty(config))
            return true;

        // check if NONE of GIT fields are null or empty
        if (!Strings.isNullOrEmpty(config.getGit()) && !Strings.isNullOrEmpty(config.getRef())
            && !Strings.isNullOrEmpty(config.getPath()))

            // check if ALL of HELM fields are null or empty
            return Strings.isNullOrEmpty(config.getRepository()) && Strings.isNullOrEmpty(config.getName())
                && Strings.isNullOrEmpty(config.getVersion());

        // check if ALL of GIT fields are null or empty
        if (Strings.isNullOrEmpty(config.getGit()) && Strings.isNullOrEmpty(config.getRef())
            && Strings.isNullOrEmpty(config.getPath()))

            // check if NONE of HELM fields are null or empty
            return !Strings.isNullOrEmpty(config.getRepository()) && !Strings.isNullOrEmpty(config.getName())
                && !Strings.isNullOrEmpty(config.getVersion());

        return false;
    }

    private static boolean isEmpty(ChartConfig config) {
        return Strings.isNullOrEmpty(config.getGit()) && Strings.isNullOrEmpty(config.getRef())
            && Strings.isNullOrEmpty(config.getPath()) && Strings.isNullOrEmpty(config.getRepository())
            && Strings.isNullOrEmpty(config.getName()) && Strings.isNullOrEmpty(config.getVersion());
    }

    public Map<String, Object> toMap() {
        try {
            return JsonUtils.writeValueAsDeepMap(this);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Exception converting object", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChartConfig)) return false;
        ChartConfig that = (ChartConfig) o;
        return Objects.equals(getGit(), that.getGit()) &&
            Objects.equals(getRef(), that.getRef()) &&
            Objects.equals(getPath(), that.getPath()) &&
            Objects.equals(getRepository(), that.getRepository()) &&
            Objects.equals(getName(), that.getName()) &&
            Objects.equals(getVersion(), that.getVersion());
    }

    public static class ChartConfigBuilder {

        private String git;
        private String ref;
        private String path;
        private String repository;
        private String name;
        private String version;

        ChartConfigBuilder() {
        }

        public ChartConfigBuilder withGit(String git) {
            this.git = git;
            return this;
        }

        public ChartConfigBuilder withRef(String ref) {
            this.ref = ref;
            return this;
        }

        public ChartConfigBuilder withPath(String path) {
            this.path = path;
            return this;
        }

        public ChartConfigBuilder withRepository(String repository) {
            this.repository = repository;
            return this;
        }

        public ChartConfigBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public ChartConfigBuilder withVersion(String version) {
            this.version = version;
            return this;
        }

        public ChartConfig build() {
            return new ChartConfig(git, ref, path, repository, name, version);
        }
    }
}
