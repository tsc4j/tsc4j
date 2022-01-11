/*
 * Copyright 2017 - 2022 tsc4j project
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

package com.github.tsc4j.core.utils

import spock.lang.Specification

import java.util.stream.Collectors

class StreamUtilsSpec extends Specification {
    def "nonNull() should produce expected result"() {
        given:
        def list = ['a', 'b', null, 'b', 'A']
        def listStream = list.stream()

        when:
        def streamA = StreamUtils.nonNull(list)
        def streamB = StreamUtils.nonNull(listStream)

        then:
        streamA.collect(Collectors.toList()) == list.findAll { it != null }
        streamB.collect(Collectors.toList()) == list.findAll { it != null }
    }

    def "trimmedNonEmpty() should produce expected result"() {
        given:
        def list = [null, '', '  ', 'a', '     ', ' b ', null, 'b ', ' A']
        def listStream = list.stream()

        when:
        def streamA = StreamUtils.trimmedNonEmpty(list)
        def streamB = StreamUtils.trimmedNonEmpty(listStream)

        then:
        streamA.collect(Collectors.toList()) == ['a', 'b', 'b', 'A']
        streamB.collect(Collectors.toList()) == ['a', 'b', 'b', 'A']
    }
}
