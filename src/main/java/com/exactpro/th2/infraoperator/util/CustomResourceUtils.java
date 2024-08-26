/*
 * Copyright 2020-2024 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.infraoperator.spec.Th2CustomResource;
import com.exactpro.th2.infraoperator.spec.helmrelease.HelmRelease;
import io.fabric8.kubernetes.api.model.HasMetadata;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractName;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractNamespace;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.extractType;

public class CustomResourceUtils {

    private static final Logger logger = LoggerFactory.getLogger(CustomResourceUtils.class);

    public static final long RESYNC_TIME = 180000;

    public static final String GIT_COMMIT_HASH = "th2.exactpro.com/git-commit-hash";

    private static final int SHORT_HASH_LENGTH = 8;

    private CustomResourceUtils() {
    }

    public static String annotationFor(String namespace, String kind, String resourceName) {
        return String.format("%s:%s/%s", namespace, kind, resourceName);
    }

    public static String annotationFor(String namespace, String kind, String resourceName, String commitHash) {
        return String.format("%s:%s/%s(commit-%s)", namespace, kind, resourceName, commitHash);
    }

    public static String annotationFor(HasMetadata resource) {
        return annotationFor(
                resource.getMetadata().getNamespace(),
                resource.getKind(),
                resource.getMetadata().getName(),
                extractShortCommitHash(resource)
        );
    }

    @Nullable
    public static HelmRelease search(List<HelmRelease> helmReleases, Th2CustomResource resource) {
        String resFullName = extractHashedFullName(resource);
        return helmReleases.stream()
                .filter(hr -> {
                    var owner = extractOwnerFullName(hr);
                    return Objects.nonNull(owner) && owner.equals(resFullName);
                }).findFirst()
                .orElse(null);
    }

    public static String extractHashedName(Th2CustomResource customResource) {
        return hashNameIfNeeded(extractName(customResource));
    }

    private static String extractHashedFullName(Th2CustomResource customResource) {
        return concatFullName(extractNamespace(customResource), extractHashedName(customResource));
    }

    @Nullable
    private static String extractOwnerFullName(HelmRelease helmRelease) {
        var ownerReferences = helmRelease.getMetadata().getOwnerReferences();
        if (!ownerReferences.isEmpty()) {
            return concatFullName(extractNamespace(helmRelease), ownerReferences.get(0).getName());
        } else {
            logger.warn("[{}<{}>] doesn't have owner resource", extractType(helmRelease), extractFullName(helmRelease));
            return null;
        }
    }

    private static String extractFullName(HasMetadata obj) {
        return concatFullName(extractNamespace(obj), extractName(obj));
    }

    private static String concatFullName(String namespace, String name) {
        return namespace + "." + name;
    }

    private static String hashNameIfNeeded(String resName) {
        if (resName.length() >= HelmRelease.NAME_LENGTH_LIMIT) {
            return digest(resName);
        }
        return resName;
    }

    static String digest(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, HelmRelease.NAME_LENGTH_LIMIT);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String extractShortCommitHash(HasMetadata resource) {
        try {
            return fullCommitHash(resource).substring(0, SHORT_HASH_LENGTH);
        } catch (NullPointerException e) {
            return "not related to specific commit, using default value";
        }
    }

    public static String fullCommitHash(HasMetadata resource) {
        return resource.getMetadata().getAnnotations().get(GIT_COMMIT_HASH);
    }

    public static String stamp(HasMetadata resource) {
        return ExtractUtils.fullSourceHash(resource) + ":" + fullCommitHash(resource);
    }
}
