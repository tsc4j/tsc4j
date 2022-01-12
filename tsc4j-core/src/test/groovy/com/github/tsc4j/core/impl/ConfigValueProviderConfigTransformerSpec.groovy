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

package com.github.tsc4j.core.impl

import com.github.tsc4j.core.AbstractConfigValueProvider
import com.github.tsc4j.core.ConfigTransformer
import com.github.tsc4j.core.ConfigValueProvider
import com.github.tsc4j.core.Tsc4j
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import com.typesafe.config.ConfigValueType
import groovy.util.logging.Slf4j
import lombok.NonNull
import spock.lang.Specification
import spock.lang.Unroll

@Slf4j
@Unroll
class ConfigValueProviderConfigTransformerSpec extends Specification {
    static def configTemplate = '''
    {
        foo: bar
        a.scalar: "%s"
        b.obj: "%s"
        a.list: [ "%s" ]
    }
    '''

    // ConfigValue value provider mocks
    def cfgValueProviderA = new MockConfigValueProvider(type: 'foo', allowMissing: true)
    def cfgValueProviderB = new MockConfigValueProvider(name: 'some-name-b', type: 'foo', allowMissing: false)
    def cfgValueProviderC = new MockConfigValueProvider(type: 'bar', allowMissing: true)
    def cfgValueProviderD = new MockConfigValueProvider(type: 'some-type', allowMissing: true)

    // config transformer
    ConfigTransformer transformer = ConfigValueProviderConfigTransformer
        .builder()
        .withProviders([cfgValueProviderA, cfgValueProviderB, cfgValueProviderC, cfgValueProviderD])
        .build()

    def setup() {
        cfgValueProviderA.reset()
        cfgValueProviderB.reset()
        cfgValueProviderC.reset()
        cfgValueProviderD.reset()
    }

    def cleanup() {
        transformer?.close()
    }

    def "builder should throw ISE on build() if there are no providers set."() {
        when:
        ConfigValueProviderConfigTransformer.builder().build()

        then:
        def exception = thrown(IllegalStateException)
        exception.getMessage().contains("No value providers")
    }

    def "builder should create instance with expected properties"() {
        given:
        def name = "foo"
        def providerA = Mock(ConfigValueProvider)
        def providerB = Mock(ConfigValueProvider)

        when:
        def transformer = ConfigValueProviderConfigTransformer
            .builder()
            .withProviders(null, providerB, null, providerB, null, providerA, providerA)
            .setName(name)
            .build()

        then:
        transformer != null
        with(transformer) {
            getType() == 'values'
            getName() == name
            providers == [providerB, providerA] // should retain correct order
        }
    }

    def "closing transformer should close delegated config value providers"() {
        given:
        def providerA = Mock(ConfigValueProvider)
        def providerB = Mock(ConfigValueProvider)

        and:
        def cfgValueProviders = [null, providerA, null, providerB, null]
        def transformer = ConfigValueProviderConfigTransformer.builder()
                                                              .withProviders(cfgValueProviders)
                                                              .build()
        when:
        transformer.close()

        then:
        1 * providerA.close()
        1 * providerB.close()

        noExceptionThrown()
    }

    def "should throw if there is config value transformer missing in a magic value"() {
        given:
        def providerA = new MockConfigValueProvider(type: 'typeA')
        def providerB = new MockConfigValueProvider(type: 'typeB')
        def providerC = new MockConfigValueProvider(name: providerName, type: providerType)

        and:
        def transformer = ConfigValueProviderConfigTransformer.builder()
                                                              .withProviders([providerA, providerB, providerC])
                                                              .build()

        and:
        def config = ConfigFactory.parseString("""
           foo: bar
           a: \"%{typeA://x/y}\"
           b: \"%{typeB://z/w}\"
           c: \"$varSpec\"
        """)

        when:
        def result = transformer.transform(config)

        then:
        def ex = thrown(IllegalStateException)
        ex.getMessage().contains('typeC')
        result == null

        where:
        providerType | providerName | varSpec
        'foo'        | ''           | '%{typeC://a/b}'
        'typeC'      | 'some-name'  | '%{typeC:my-cool-name://a/b}'
    }

    def "should NOT throw if there is config value transformer missing in a magic value and configured this way"() {
        given:
        def providerA = Mock(ConfigValueProvider)
        def providerB = Mock(ConfigValueProvider)
        def providerC = Mock(ConfigValueProvider)

        and:
        def transformer = ConfigValueProviderConfigTransformer.builder()
                                                              .withProviders([providerA, providerB, providerC])
                                                              .setAllowErrors(true)
                                                              .build()

        and:
        def config = ConfigFactory.parseString("""
           foo: bar
           a: \"%{typeA://x/y}\"
           b: \"%{typeB://z/w}\"
           c: \"$varSpec\"
        """)

        when:
        def result = transformer.transform(config)

        then:
        providerA.getType() >> 'typeA'
        providerA.get(_) >> [:]
        providerB.getType() >> 'typeB'
        providerB.get(_) >> [:]

        providerC.getType() >> providerType
        providerC.getName() >> providerName

        noExceptionThrown()

        where:
        providerType | providerName | varSpec
        'foo'        | ''           | '%{typeC://a/b}'
        'typeC'      | 'some-name'  | '%{typeC:my-cool-name://a/b}'
    }

    def "should correctly replace simple string with a boolean value: =>#newRawValue<="() {
        given:
        def cfgPath = "some.cfg.path"

        def valueProviderValueName = 'foo/bar'
        def cfgValueReference = "%{some-type://${valueProviderValueName}}"
        def origConfig = ConfigFactory.parseString("""
            ${cfgPath}: "${cfgValueReference}"
        """)

        and:
        def newConfigValue = ConfigValueFactory.fromAnyRef(newRawValue)

        expect:
        origConfig.root().size() == 1
        origConfig.hasPath(cfgPath)
        with(origConfig.getValue(cfgPath)) {
            valueType() == ConfigValueType.STRING
            unwrapped() == cfgValueReference
        }

        when:
        cfgValueProviderD.values = [(valueProviderValueName): newConfigValue]
        def config = transformer.transform(origConfig)
        log.info("transformed config:\n{}", Tsc4j.render(config, true))

        then:
        config != origConfig
        config.getBoolean(cfgPath) == newRawValue.toString().toLowerCase().toBoolean()

        where:
        newRawValue << [false, true, 'true', 'false']
    }

    def "should correctly replace simple string with a numeric value: =>#newRawValue<="() {
        given:
        def cfgPath = "some.cfg.path"

        def valueProviderValueName = 'foo/bar'
        def cfgValueReference = "%{some-type://${valueProviderValueName}}"
        def origConfig = ConfigFactory.parseString("""
            ${cfgPath}: "${cfgValueReference}"
        """)

        and:
        def newConfigValue = ConfigValueFactory.fromAnyRef(newRawValue)

        expect:
        origConfig.root().size() == 1
        origConfig.hasPath(cfgPath)
        with(origConfig.getValue(cfgPath)) {
            valueType() == ConfigValueType.STRING
            unwrapped() == cfgValueReference
        }

        when:
        cfgValueProviderD.values = [(valueProviderValueName): newConfigValue]

        def config = transformer.transform(origConfig)
        log.info("transformed config:\n{}", Tsc4j.render(config, true))

        then:
        config != origConfig
        config.getLong(cfgPath) == newRawValue.toString().toLowerCase().toLong()

        where:
        newRawValue << [
            -100,
            0,
            '-0',
            '+0',
            100,
            +100,
            -9223372036854775808,
            9223372036854775807,
            '-9223372036854775808',
            '9223372036854775807',

            '+9223372036854775807',
            ' -9223372036854775808',
            ' 9223372036854775807',
            ' +9223372036854775807',
            ' -9223372036854775808    ',
            ' 9223372036854775807  ',
            ' +9223372036854775807  ',
        ]
    }

    def "should correctly replace simple string with another string value: =>#newRawValue<="() {
        given:
        def cfgPath = "some.cfg.path"
        def cfgValueReference = '%{some-type://foo/bar}'
        def origConfig = ConfigFactory.parseString("""
            ${cfgPath}: "${cfgValueReference}"
        """)

        and:
        def newConfigValue = ConfigValueFactory.fromAnyRef(newRawValue)

        expect:
        origConfig.root().size() == 1
        origConfig.hasPath(cfgPath)
        with(origConfig.getValue(cfgPath)) {
            valueType() == ConfigValueType.STRING
            unwrapped() == cfgValueReference
        }

        when:
        cfgValueProviderD.values = ['foo/bar': newConfigValue]

        def config = transformer.transform(origConfig)
        log.info("transformed config:\n{}", Tsc4j.render(config, true))

        then:
        config != origConfig
        with(config) {
            root().size() == 1
            getValue(cfgPath).valueType() == ConfigValueType.STRING
            getValue(cfgPath).unwrapped() == newRawValue
        }

        where:
        newRawValue << [
            'simple-string',
            'simple-string  ',
            ' simple-string',
            ' simple-string   ',
            ' Emojified 🔥👽🤖 string 🎅   ',

            // lists
            //['foo', 'bar', 'Emojified 🔥👽🤖 string 🎅   '] | ConfigValueType.LIST

            // objects
            //[foo: ' Emojified 🔥👽🤖 string 🎅   ']         | ConfigValueType.OBJECT
        ]
    }

    def "should correctly replace config variable: '#str'"() {
        given:
        def cfgStr = configTemplate.replaceAll('%s', str)
        def config = ConfigFactory.parseString(cfgStr).resolve()

        cfgValueProviderA.values = [(valName): ConfigValueFactory.fromAnyRef(expected)]

        when:
        log.info("transforming config: {}", Tsc4j.render(config, true))
        def result = transformer.transform(config)
        log.info("transformed config: {}", Tsc4j.render(result, true))

        then:
        !result.isEmpty()
        result != config

        with(result) {
            getString('foo') == 'bar'
            getString('a.scalar') == expected
            getString('b.obj') == expected
            getStringList('a.list') == [expected]
        }

        where:
        str            | valName | expected

        // new format
        '%{foo://x.y}' | 'x.y'   | 'some-super-val'
    }

    def "should correctly replace simple string with a list: =>#newRawValue<="() {
        given:
        def cfgPath = "some.cfg.path"
        def cfgValueReference = '%{some-type://foo/bar}'
        def origConfig = ConfigFactory.parseString("""
            ${cfgPath}: "${cfgValueReference}"
        """)

        and:
        def newConfigValue = ConfigValueFactory.fromAnyRef(newRawValue)

        expect:
        origConfig.root().size() == 1
        origConfig.hasPath(cfgPath)
        with(origConfig.getValue(cfgPath)) {
            valueType() == ConfigValueType.STRING
            unwrapped() == cfgValueReference
        }

        when:

        cfgValueProviderD.values = ['foo/bar': newConfigValue]

        def config = transformer.transform(origConfig)
        log.info("transformed config:\n{}", Tsc4j.render(config, true))

        then:
        config != origConfig
        with(config) {
            root().size() == 1
            getValue(cfgPath).valueType() == ConfigValueType.LIST
            getValue(cfgPath).unwrapped() == newRawValue
        }

        where:
        newRawValue << [
            ['foo', 'bar', 'Emojified 🔥👽🤖 string 🎅   '],
            [' Emojified 🔥👽🤖 string 🎅   ', 1, null, true, 'ĆŽŠĐčćžđš', []]
        ]
    }

    def "should correctly replace simple string with an object: =>#newRawValue<="() {
        given:
        def cfgPath = "some.cfg.path"
        def cfgValueReference = '%{some-type://foo/bar}'
        def origConfig = ConfigFactory.parseString("""
            ${cfgPath}: "${cfgValueReference}"
        """)

        and:
        def newConfigValue = ConfigValueFactory.fromAnyRef(newRawValue)

        expect:
        origConfig.root().size() == 1
        origConfig.hasPath(cfgPath)
        with(origConfig.getValue(cfgPath)) {
            valueType() == ConfigValueType.STRING
            unwrapped() == cfgValueReference
        }

        when:
        cfgValueProviderD.values = ['foo/bar': newConfigValue]

        def config = transformer.transform(origConfig)
        log.info("transformed config:\n{}", Tsc4j.render(config, true))

        then:

        config != origConfig
        with(config) {
            root().size() == 1
            getValue(cfgPath).valueType() == ConfigValueType.OBJECT
            getValue(cfgPath).unwrapped() == newRawValue
        }

        where:
        newRawValue << [
            [foo: ' Emojified 🔥👽🤖 string 🎅   čćžđš ČĆŽĐŠ value'],
            [' Emojified 🔥👽🤖 string 🎅  key ': [1, 2, 3, 5]]
        ]
    }

    def "should correctly replace simple string containig two config value references: =>#rawValueA/#rawValueB/#rawValueC<="() {
        given: "configure existing config magic variables"
        // these %s will be replaced with content of varXXX variable values
        def valueReferenceTemplate = 'ČĆŽŠĐ %%{foo://%s} 👽🤖%%{foo:some-name-b://%s}🤖👽 🔥 %%{bar://%s}🔥 '

        def varA = 'some/var'
        def varB = 'bo.jo'
        def varC = 'čćžđš/ČŽĆŠĐ'

        def cfgValueReference = sprintf(valueReferenceTemplate, varA, varB, varC)
        log.info("existing config value: '{}'", cfgValueReference)

        and: "compute new config values for value providers"
        def newConfigValueA = ConfigValueFactory.fromAnyRef(rawValueA)
        def newConfigValueB = ConfigValueFactory.fromAnyRef(rawValueB)
        def newConfigValueC = ConfigValueFactory.fromAnyRef(rawValueC)

        and: "compute expected value"
        def expectedValue = sprintf(valueReferenceTemplate,
            Tsc4j.stringify(newConfigValueA),
            Tsc4j.stringify(newConfigValueB),
            Tsc4j.stringify(newConfigValueC))
            .replaceAll('%\\{[^/]+', '')
            .replaceAll('(?<!\\})\\}', '')
            .replace('//', '')

        and: "setup original config"
        def cfgPath = "some.cfg.path"
        def origConfig = ConfigFactory.parseString("""
            ${cfgPath}: "${cfgValueReference}"
        """)

        expect:
        origConfig.root().size() == 1
        origConfig.hasPath(cfgPath)
        with(origConfig.getValue(cfgPath)) {
            valueType() == ConfigValueType.STRING
            unwrapped() == cfgValueReference
        }

        when:
        cfgValueProviderA.values = [(varA): newConfigValueA]
        cfgValueProviderB.values = [(varB): newConfigValueB]
        cfgValueProviderC.values = [(varC): newConfigValueC]

        log.info("transforming config: {}", Tsc4j.render(origConfig, true))
        def config = transformer.transform(origConfig)
        log.info("transformed config:\n{}", Tsc4j.render(config, true))

        then:
        config != origConfig
        with(config) {
            root().size() == 1
            getValue(cfgPath).valueType() == ConfigValueType.STRING
            getValue(cfgPath).unwrapped() == expectedValue
        }

        where:
        rawValueA | rawValueB | rawValueC
        true      | 'čćžšđ'   | '🙈'
        'True '   | '我心永恆'    | '🙉'

        // some provider returns list of strings
        'True '   | '我心永恆'    | ['a', 'b']
        // some provider returns an object
        'True '   | '我心永恆'    | ['a': 'b']
    }

    // mock config value provider, can be configured what to return
    static class MockConfigValueProvider extends AbstractConfigValueProvider {
        Map<String, ConfigValue> values = [:]

        MockConfigValueProvider(Map args) {
            super(
                args.name ?: '',
                args.type ?: 'unknown-type',
                args.aliases ?: [],
                args.allowMissing ?: false,
                false
            )
        }

        MockConfigValueProvider reset() {
            values = [:]
            this
        }

        @Override
        protected Map<String, ConfigValue> doGet(@NonNull List<String> names) {
            def res = [:]
            names.each {
                if (values.containsKey(it)) {
                    res.put(it, values.get(it))
                }
            }
            res
        }
    }
}
