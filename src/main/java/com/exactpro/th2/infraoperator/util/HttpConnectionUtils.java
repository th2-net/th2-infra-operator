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

package com.exactpro.th2.infraoperator.util;

import com.exactpro.th2.infraoperator.model.docker.Image;
import com.exactpro.th2.infraoperator.model.docker.NexusAuth;
import lombok.SneakyThrows;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static com.exactpro.th2.infraoperator.util.HttpConnectionUtils.HttpMethods.GET;

public final class HttpConnectionUtils {

    private HttpConnectionUtils() {
        throw new AssertionError();
    }


    @SneakyThrows
    public static HttpURLConnection getNexusConnection(Image image, NexusAuth nexusAuth) {

        HttpURLConnection connection = (HttpURLConnection) toManifestURL(image, nexusAuth).openConnection();

        connection.setRequestMethod(GET.method);
        connection.setRequestProperty("Authorization", "Basic " + nexusAuth.getEncoded());

        return connection;
    }

    private static URL toManifestURL(Image image, NexusAuth nexusAuth) throws MalformedURLException {
        return new URL(String.format("https://%s/v2/%s/manifests/%s", nexusAuth.getRegistry(), image.getName(), image.getTag()));
    }

    public enum HttpMethods {

        PUT("PUT"),

        GET("GET");

        public final String method;

        HttpMethods(String method) {
            this.method = method;
        }
    }
}
