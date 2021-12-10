/*
 * Copyright 2017 - 2021 tsc4j project
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
 */

package com.github.tsc4j.aws.sdk2

import com.github.tsc4j.core.Tsc4jImplUtils
import com.github.tsc4j.core.Tsc4jLoader
import com.github.tsc4j.core.impl.Tsc4jLoaderTestBaseSpec
import com.typesafe.config.ConfigFactory

class S3ConfigSourceLoaderSpec extends Tsc4jLoaderTestBaseSpec {
    @Override
    Tsc4jLoader loader() {
        return new S3ConfigSourceLoader()
    }

    @Override
    Class forClass() {
        return S3ConfigSource
    }

    @Override
    Class builderClass() {
        return S3ConfigSource.Builder
    }

    def "config source should be loaded by type name and aliases"() {
        given:
        def loader = loader()
        def names = [loader.name()] + loader.aliases()

        when:
        def csOpts = names.collect {
            def config = ConfigFactory.parseMap([
                impl : it,
                paths: ['s3://foo/bar']
            ])
            log.info("creating config source impl: $it")
            Tsc4jImplUtils.createConfigSource(config, 1)
        }

        then:
        csOpts.size() == names.size()
        csOpts.each { assert it.isPresent() }
        csOpts.each { assert it.get() instanceof S3ConfigSource }

        cleanup:
        csOpts?.collect { it.ifPresent({ it.close() }) }
    }
}
