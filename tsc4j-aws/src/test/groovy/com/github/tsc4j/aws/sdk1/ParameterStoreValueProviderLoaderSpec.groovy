/*
 * Copyright 2017 - 2019 tsc4j project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.tsc4j.aws.sdk1

import com.github.tsc4j.core.Tsc4jImplUtils
import com.github.tsc4j.core.Tsc4jLoader
import com.github.tsc4j.core.impl.Tsc4jLoaderTestBaseSpec
import com.typesafe.config.ConfigFactory

class ParameterStoreValueProviderLoaderSpec extends Tsc4jLoaderTestBaseSpec {
    @Override
    Tsc4jLoader loader() {
        new ParameterStoreValueProviderLoader()
    }

    @Override
    Class forClass() {
        ParameterStoreValueProvider
    }

    @Override
    Class builderClass() {
        ParameterStoreValueProvider.Builder
    }

    def "value provider should be loaded by type name and aliases"() {
        given:
        def loader = loader()
        def names = [loader.name()] + loader.aliases()

        when:
        def vpOpts = names.collect {
            def config = ConfigFactory.parseMap([
                impl: it
            ])
            log.info("creating value provider impl: $it")
            Tsc4jImplUtils.createValueProvider(config, 1)
        }

        then:
        vpOpts.size() == names.size()
        vpOpts.each { assert it.isPresent() }
        vpOpts.each { assert it.get() instanceof ParameterStoreValueProvider }

        cleanup:
        vpOpts?.collect { it.ifPresent({ it.close() }) }
    }
}
