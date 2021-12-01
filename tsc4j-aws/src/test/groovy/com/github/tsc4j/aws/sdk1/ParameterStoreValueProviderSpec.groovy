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

import com.typesafe.config.ConfigFactory
import groovy.util.logging.Slf4j
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Slf4j
@Stepwise
class ParameterStoreValueProviderSpec extends Specification {
    @Shared
    def paramNames = []

    @Shared
    def provider = ParameterStoreValueProvider.builder()
                                              .setRegion("us-west-2")
                                              .build()

    @Ignore
    def "should list all parameters"() {
        when:
        def names = provider.names()

        def sb = new StringBuilder("received aws SSM parameter names:\n")
        names.each { sb.append("  $it\n") }
        log.info(sb.toString())

        then:
        names.size() > 30

        when: "assign param names"
        paramNames = names

        then:
        true
    }

    @Ignore
    def "should work"() {
        when:

        def ts = System.currentTimeMillis()
        def results = provider.get(paramNames)
        def duration = System.currentTimeMillis() - ts

        log.info("results: {} (duration: {} msec)", results.size(), duration)
        results.each { k, v -> log.info("  $k : ${v.unwrapped()}") }

        then:
        true
    }

    def "withConfig() should configure parameters to expected values"() {
        given:
        def cfgMap = [
            // common AWS configuration parameters
            "access-key-id"       : 'foo',
            "secret-access-key"   : 'bar',
            "region"              : 'us-east-5',
            "endpoint"            : "http://localhost:4567/",
            "gzip"                : false,
            "timeout"             : '67s',
            "max-connections"     : 13,
            "max-error-retry"     : 7,
            "s3-path-style-access": true,

            // value provider specific configuration parameters
            "decrypt"             : false,
        ]
        def config = ConfigFactory.parseMap(cfgMap)

        and:
        def builder = ParameterStoreValueProvider.builder()

        expect: "default values"
        with(builder.getAwsConfig()) {
            getEndpoint() == null
            getAccessKeyId() == null
            getSecretAccessKey() == null
            getRegion() == null
            getEndpoint() == null
            isGzip() == true
            getTimeout().toSeconds() == 10
            getMaxConnections() == 100
            getMaxErrorRetry() == 0
            getS3PathStyleAccess() == null
        }
        builder.isDecrypt() == true

        when:
        builder.withConfig(config)

        then: "settings should be applied"
        with(builder.getAwsConfig()) {
            getEndpoint() == cfgMap.endpoint
            getAccessKeyId() == cfgMap.'access-key-id'
            getSecretAccessKey() == cfgMap.'secret-access-key'
            getRegion() == cfgMap.region
            isGzip() == cfgMap.gzip
            getTimeout().toSeconds() == 67
            getMaxConnections() == 13
            getMaxErrorRetry() == 7
            getS3PathStyleAccess() == true
        }

        builder.isDecrypt() == false
    }
}
