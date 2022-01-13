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

import com.amazonaws.services.simplesystemsmanagement.model.ParameterType
import com.github.tsc4j.core.impl.Stopwatch
import com.typesafe.config.ConfigFactory
import groovy.util.logging.Slf4j
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Slf4j
@Stepwise
class ParameterStoreValueProviderSpec extends Specification {
    static def parameters = [
        "/sdk1/psvp/a/x"  : [ParameterType.String, "x"],
        "/sdk1/psvp/a/y"  : [ParameterType.StringList, "a,b,c", ["a", "b", "c"]],
        "/sdk1/psvp/a/z"  : [ParameterType.SecureString, "val_a.z"],
        "/sdk1/psvp/b/c/d": [ParameterType.String, "42"]
    ]

    @Shared
    ParameterStoreValueProvider provider = null

    def cleanupSpec() {
        provider?.close()
    }

    def "withConfig() should configure parameters to expected values"() {
        given:
        def cfgMap = [
            // common AWS configuration parameters
            "access-key-id"       : 'foo',
            "secret-access-key"   : 'bar',
            "region"              : 'us-east-1',
            "endpoint"            : "http://localhost:4566/",
            "gzip"                : false,
            "timeout"             : '67s',
            "max-connections"     : 13,
            "max-error-retry"     : 7,
            "s3-path-style-access": true,

            // value provider specific configuration parameters
            "decrypt"             : true,
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

        builder.isDecrypt() == true

        when:
        provider = builder.build()
        log.info("created AWS SSM config value provider: {}", provider)

        then:
        provider != null
        provider instanceof ParameterStoreValueProvider
    }

    def "cleanup SSM and install parameters"() {
        when:
        def ssm = provider.ssmFacade.ssm
        AwsTestEnv.setupSSM(ssm, parameters)

        then:
        true
    }

    def "should list all parameters"() {
        when:
        def names = provider.names()

        def sb = new StringBuilder("received aws SSM parameter names:\n")
        names.each { sb.append("  $it\n") }
        log.info(sb.toString())

        then:
        names.size() == parameters.size()
        names.toSet() == parameters.keySet()
    }

    def "provider should fetch requested parameters"() {
        given:
        def paramNames = parameters.keySet()
        def sw = new Stopwatch()

        when: "fetch config values"
        def configValues = provider.get(paramNames)
        debugConfigValues(configValues, sw)

        then:
        configValues.size() == paramNames.size()
        configValues.each {
            def expectedType = parameters.get(it.getKey()).first()
                                         .toString()
                                         .toLowerCase()
                                         .replace('secure', '')
                                         .replace('stringlist', 'list')

            def expectedValue = parameters.get(it.getKey()).last()

            def value = it.value
            assert value.unwrapped() == expectedValue
            assert value.valueType().toString().toLowerCase() == expectedType.toLowerCase()
        }
    }

    def "provider should fetch requested parameters that don't start with /"() {
        given:
        def paramNames = parameters.keySet().collect { it.replaceAll('^/+', '') }
        def sw = new Stopwatch()

        when: "fetch config values"
        def configValues = provider.get(paramNames)
        debugConfigValues(configValues, sw)

        then:
        configValues.size() == paramNames.size()
        configValues.each {
            def key = '/' + it.key
            assert parameters.get(key) != null: "Non-existing test parameter: $key"

            def expectedType = parameters.get(key).first()
                                         .toString()
                                         .toLowerCase()
                                         .replace('secure', '')
                                         .replace('stringlist', 'list')

            def expectedValue = parameters.get(key).last()

            def value = it.value
            assert value.unwrapped() == expectedValue
            assert value.valueType().toString().toLowerCase() == expectedType.toLowerCase()
        }
    }

    def debugConfigValues(Map configValues, Stopwatch sw) {
        log.info("fetched config values: {} (duration: {} msec)", configValues.size(), sw)
        configValues.each { k, v -> log.info("  $k : ${v.unwrapped()}") }
    }
}
