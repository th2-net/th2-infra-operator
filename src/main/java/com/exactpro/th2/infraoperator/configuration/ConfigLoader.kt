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

package com.exactpro.th2.infraoperator.configuration

import com.exactpro.th2.infraoperator.util.JsonUtils.YAML_MAPPER
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.apache.commons.text.StringSubstitutor
import org.apache.commons.text.lookup.StringLookupFactory
import java.io.FileInputStream

object ConfigLoader {

    const val CONFIG_FILE_SYSTEM_PROPERTY = "infra.operator.config"
    const val CONFIG_FILE_NAME = "/var/th2/config/infra-operator.yml"

    private val logger = KotlinLogging.logger { }

    @JvmStatic val config: OperatorConfig

    init {
        config = loadConfiguration()
    }

    @JvmStatic
    fun loadConfiguration(): OperatorConfig {
        val path: String = System.getProperty(
            CONFIG_FILE_SYSTEM_PROPERTY,
            CONFIG_FILE_NAME
        )

        try {
            FileInputStream(path)
                .use { inputStream ->
                    val stringSubstitute =
                        StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup())
                    val content = stringSubstitute.replace(String(inputStream.readAllBytes()))
                    return YAML_MAPPER.readValue(content)
                }
        } catch (e: UnrecognizedPropertyException) {
            logger.error(
                "Bad configuration: unknown property(\"{}\") specified in configuration file", e.propertyName
            )
            throw e
        } catch (e: JsonParseException) {
            logger.error("Bad configuration: exception while parsing configuration file")
            throw e
        }
    }
}
