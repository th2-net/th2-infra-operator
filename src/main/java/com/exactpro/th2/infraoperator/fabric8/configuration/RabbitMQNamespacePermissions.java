package com.exactpro.th2.infraoperator.fabric8.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RabbitMQNamespacePermissions {

    static final String DEFAULT_CONFIGURE_PERMISSION = "";
    static final String DEFAULT_READ_PERMISSION = ".*";
    static final String DEFAULT_WRITE_PERMISSION = ".*";

    private String configure;
    private String read;
    private String write;

    public RabbitMQNamespacePermissions() {
        configure = DEFAULT_CONFIGURE_PERMISSION;
        read = DEFAULT_READ_PERMISSION;
        write = DEFAULT_WRITE_PERMISSION;
    }

    public RabbitMQNamespacePermissions(String configure, String read, String write) {
        setConfigure(configure);
        setRead(read);
        setWrite(write);
    }

    public String getConfigure() {
        return configure;
    }

    public void setConfigure(String configure) {
        this.configure = (configure == null) ? "" : configure;
    }

    public String getRead() {
        return read;
    }

    public void setRead(String read) {
        this.read = (read == null) ? "" : read;
    }

    public String getWrite() {
        return write;
    }

    public void setWrite(String write) {
        this.write = (write == null) ? "" : write;
    }

    public static RabbitMQNamespacePermissionsBuilder builder() {
        return new RabbitMQNamespacePermissionsBuilder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RabbitMQNamespacePermissions)) return false;
        RabbitMQNamespacePermissions that = (RabbitMQNamespacePermissions) o;
        return Objects.equals(getConfigure(), that.getConfigure()) &&
            Objects.equals(getRead(), that.getRead()) &&
            Objects.equals(getWrite(), that.getWrite());
    }

    public static class RabbitMQNamespacePermissionsBuilder {

        private String configure;
        private String read;
        private String write;

        RabbitMQNamespacePermissionsBuilder() {
        }

        public RabbitMQNamespacePermissionsBuilder configure(String configure) {
            this.configure = configure;
            return this;
        }

        public RabbitMQNamespacePermissionsBuilder read(String read) {
            this.read = read;
            return this;
        }

        public RabbitMQNamespacePermissionsBuilder write(String write) {
            this.write = write;
            return this;
        }

        public RabbitMQNamespacePermissions build() {
            return new RabbitMQNamespacePermissions(configure, read, write);
        }
    }
}
