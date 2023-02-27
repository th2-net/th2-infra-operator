/*
 * Copyright 2020-2022 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.infraoperator.spec.box.Th2Box;
import com.exactpro.th2.infraoperator.spec.corebox.Th2CoreBox;
import com.exactpro.th2.infraoperator.spec.mstore.Th2Mstore;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.ANTECEDENT_LABEL_KEY_ALIAS;
import static com.exactpro.th2.infraoperator.operator.HelmReleaseTh2Op.COMMIT_HASH_LABEL_KEY_ALIAS;
import static com.exactpro.th2.infraoperator.util.ExtractUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtractUtilsTests {

    @Test
    void extractNeededAnnotationsTest() {
        final String antecedent = "antecedent";
        final String commitHash = "0123456789";
        var resAnnotations = Map.of(
                "keyOfNoUse", "valOfNoUse",
                ANTECEDENT_LABEL_KEY_ALIAS, antecedent,
                COMMIT_HASH_LABEL_KEY_ALIAS, commitHash
        );
        Th2Box res = new Th2Box();
        ObjectMeta metaData = new ObjectMeta();
        metaData.setAnnotations(resAnnotations);
        res.setMetadata(metaData);
        var expectedAnnotations = Map.of(
                ANTECEDENT_LABEL_KEY_ALIAS, antecedent,
                COMMIT_HASH_LABEL_KEY_ALIAS, commitHash
        );
        assertEquals(expectedAnnotations, ExtractUtils.extractNeededAnnotations(res,
                ANTECEDENT_LABEL_KEY_ALIAS, COMMIT_HASH_LABEL_KEY_ALIAS));
    }

    @Test
    void extractTypeTest() {
        Th2Box res = new Th2Box();
        assertEquals("Th2Box", extractType(res));
    }

    @Test
    void refreshTokenTest() {
        final String myToken = "myToken";
        Th2CoreBox res = new Th2CoreBox();
        ObjectMeta metaData = getMetaWith(REFRESH_TOKEN_ALIAS, myToken);
        res.setMetadata(metaData);
        assertEquals(myToken, refreshToken(res));
        metaData.setAnnotations(Collections.emptyMap());
        assertNotEquals(myToken, refreshToken(res));
        res.setMetadata(null);
        assertNull(refreshToken(res));
    }

    @Test
    void fullSourceHashTest() {
        final String myHash = "0123456789";
        Th2Mstore res = new Th2Mstore();
        ObjectMeta metaData = getMetaWith(KEY_SOURCE_HASH, myHash);
        res.setMetadata(metaData);
        assertEquals(myHash, fullSourceHash(res));
        res.getMetadata().setAnnotations(null);
        assertEquals("", fullSourceHash(res));
        res.setMetadata(null);
        assertEquals("", fullSourceHash(res));
    }

    @Test
    void shortSourceHashTest() {
        final String fullHash = "0123456789876543210";
        Th2Mstore res = new Th2Mstore();
        ObjectMeta metaData = getMetaWith(KEY_SOURCE_HASH, fullHash);
        res.setMetadata(metaData);
        final String expected = "[01234567]";
        assertEquals(expected, shortSourceHash(res));
        res.setMetadata(null);
        assertEquals("", shortSourceHash(res));
    }

    private ObjectMeta getMetaWith(String keyInAnnotationMap, String valInAnnotationMap) {
        ObjectMeta metaData = new ObjectMeta();
        metaData.setAnnotations(Map.of(
                "key1", "val1",
                "key2", "val2",
                keyInAnnotationMap, valInAnnotationMap
        ));
        return metaData;
    }
}
