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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Map;
import java.util.Objects;

@JsonDeserialize(builder = ChartConfig.ChartConfigBuilder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChartConfig implements Cloneable {

    private String git;
    private String ref;
    private String path;

    private String repository;
    private String name;
    private String version;

    ChartConfig() {
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
            if (chartConfig.isValid() && !chartConfig.isEmpty())
                return (ChartConfig) chartConfig.clone();

            // if chartConfig is not valid it will be combined with existing config
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

            if (!overriddenConfig.isValid())
                throw new IllegalArgumentException("Exception overriding " + ChartConfig.class.getSimpleName());

            return overriddenConfig;
        } catch (CloneNotSupportedException e) {
            throw new InternalError("Exception cloning " + ChartConfig.class.getSimpleName(), e);
        }
    }


    private boolean isValid() {
        // check if ALL fields are null or empty
        if (this.isEmpty())
            return true;

        // check if NONE of GIT fields are null or empty
        if (!Strings.isNullOrEmpty(this.getGit()) && !Strings.isNullOrEmpty(this.getRef())
            && !Strings.isNullOrEmpty(this.getPath()))

            // check if ALL of HELM fields are null or empty
            return Strings.isNullOrEmpty(this.getRepository()) && Strings.isNullOrEmpty(this.getName())
                && Strings.isNullOrEmpty(this.getVersion());

        // check if ALL of GIT fields are null or empty
        if (Strings.isNullOrEmpty(this.getGit()) && Strings.isNullOrEmpty(this.getRef())
            && Strings.isNullOrEmpty(this.getPath()))

            // check if NONE of HELM fields are null or empty
            return !Strings.isNullOrEmpty(this.getRepository()) && !Strings.isNullOrEmpty(this.getName())
                && !Strings.isNullOrEmpty(this.getVersion());

        return false;
    }


    private boolean isEmpty() {
        return Strings.isNullOrEmpty(this.getGit()) && Strings.isNullOrEmpty(this.getRef())
            && Strings.isNullOrEmpty(this.getPath()) && Strings.isNullOrEmpty(this.getRepository())
            && Strings.isNullOrEmpty(this.getName()) && Strings.isNullOrEmpty(this.getVersion());
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
            ChartConfig config = new ChartConfig();
            config.git = git;
            config.ref = ref;
            config.path = path;
            config.repository = repository;
            config.name = name;
            config.version = version;
            return config;
        }
    }
}
