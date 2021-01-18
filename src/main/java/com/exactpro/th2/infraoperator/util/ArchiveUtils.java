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

import java.io.*;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class ArchiveUtils {

    private static final GZIPBase64Encoder ENCODER = new GZIPBase64Encoder();
    private static final GZIPBase64Decoder DECODER = new GZIPBase64Decoder();


    private ArchiveUtils() {
        throw new AssertionError();
    }


    public static GZIPBase64Encoder getGZIPBase64Encoder() {
        return ENCODER;
    }


    public static GZIPBase64Decoder getGZIPBase64Decoder() {
        return DECODER;
    }


    public static class GZIPBase64Encoder {

        private GZIPBase64Encoder() {
        }

        public byte[] encodeString(String value) throws IOException {

            var baos = new ByteArrayOutputStream();
            var gziposs = new GZIPOutputStream(baos);

            try (baos; gziposs) {
                gziposs.write(value.getBytes());
                gziposs.finish();
                return Base64.getEncoder().encode(baos.toByteArray());
            }
        }
    }


    public static class GZIPBase64Decoder {

        private GZIPBase64Decoder() {
        }

        public byte[] decodeString(String value) throws IOException {

            byte[] decodedValue = Base64.getDecoder().decode(value.strip());

            var bais = new ByteArrayInputStream(decodedValue);
            var gzipis = new GZIPInputStream(bais);
            var buffGzis = new BufferedInputStream(gzipis);

            try (bais; gzipis; buffGzis) {
                return buffGzis.readAllBytes();
            }
        }
    }

}
