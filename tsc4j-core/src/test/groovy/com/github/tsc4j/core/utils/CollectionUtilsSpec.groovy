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

import groovy.util.logging.Slf4j
import spock.lang.Specification

@Slf4j
class CollectionUtilsSpec extends Specification {
    def "newSet() should return mutable set"() {
        given:
        def set = CollectionUtils.newSet()

        expect:
        set instanceof LinkedHashSet

        // make sure it's mutable
        set.isEmpty()
        set.add("foo") == true
        set.add("foo") == false
        set.contains("foo") == true
    }

    def "toSet() should produce expected result"() {
        given:
        def validItems = items.findAll { it != null }

        when:
        def set = CollectionUtils.toSet(items)

        then: "check collection interface"
        set instanceof LinkedHashSet
        set == new HashSet(validItems)
        set.add('foo') == true // make sure it's mutable

        when: "check array varargs interface"
        set = CollectionUtils.toSet(items.toArray(new String[0]))

        then:
        set instanceof LinkedHashSet
        set == new HashSet(validItems)
        set.add('foo') == true // make sure it's mutable

        where:
        items << [
            // collection interface
            [],
            ['a', 'a'],
            ['a', 'b', 'b', 'a']
        ]
    }

    def "toImmutableSet() should produce expected result"() {
        given:
        def validItems = items.findAll { it != null }

        when:
        def set = CollectionUtils.toImmutableSet(items)

        then: "check collection interface"
        set == new HashSet(validItems)

        when: "make sure it's immutable"
        set.add('foo')

        then:
        thrown(UnsupportedOperationException)

        when: "check array varargs interface"
        set = CollectionUtils.toImmutableSet(items.toArray(new String[0]))

        then:
        set == new HashSet(validItems)

        when: "make sure it's immutable"
        set.add('foo')

        then:
        thrown(UnsupportedOperationException)

        where:
        items << [
            // collection interface
            [],
            ['a', 'a'],
            ['a', 'b', 'b', 'a']
        ]
    }

    def "newMap() should return mutable map"() {
        given:
        def map = CollectionUtils.newMap()

        expect:
        map instanceof LinkedHashMap

        // make sure it's mutable
        map.isEmpty()
        map.put("a", "b") == null
        map.get("a") == "b"
    }

    def "toList() should produce expected result"() {
        given:
        def validItems = items.findAll { it != null }

        when:
        def list = CollectionUtils.toList(items)

        then: "check collection interface"
        list instanceof ArrayList
        list == validItems.toList()
        list.add('foo') == true // make sure it's mutable

        when: "check array varargs interface"
        list = CollectionUtils.toList(items.toArray(new String[0]))

        then:
        list instanceof ArrayList
        list == validItems.toList()
        list.add('foo') == true // make sure it's mutable

        where:
        items << [
            [],
            [].toSet(),
            ['a', 'a'],
            ['a', 'a'].toSet(),
            ['a', 'b', 'b', 'a'],
            ['a', 'b', 'b', 'a'].toSet(),
        ]
    }

    def "toImmutableList() should produce expected result"() {
        given:
        def validItems = items.findAll { it != null }

        when:
        def list = CollectionUtils.toImmutableList(items)

        then: "check collection interface"
        list == validItems.toList()

        when:
        list.add('foo') == true // make sure it's mutable

        then:
        thrown(UnsupportedOperationException)

        when: "check array varargs interface"
        list = CollectionUtils.toImmutableList(items.toArray(new String[0]))

        then:
        list == validItems.toList()

        when:
        list.add('foo') == true // make sure it's mutable

        then:
        thrown(UnsupportedOperationException)

        where:
        items << [
            [],
            [null].toSet(),
            ['a', null, 'a'],
            ['a', null, 'a'].toSet(),
            ['a', 'b', null, 'b', 'a'],
            ['a', 'b', null, 'b', 'a'].toSet(),
        ]
    }

    def "toUniqList() should produce expected result"() {
        given:
        def validItems = items.findAll { it != null }

        when:
        def list = CollectionUtils.toUniqList(items)

        then: "check collection interface"
        list instanceof ArrayList
        list == new LinkedHashSet(validItems).toList()
        list.add('foo') == true // make sure it's mutable

        when: "check array varargs interface"
        list = CollectionUtils.toUniqList(items.toArray(new String[0]))

        then:
        list instanceof ArrayList
        list == new LinkedHashSet(validItems).toList()
        list.add('foo') == true // make sure it's mutable

        where:
        items << [
            [],
            [null].toSet(),
            ['a', null, 'a'],
            ['a', null, 'a'].toSet(),
            ['a', null, 'b', 'b', 'a'],
            ['a', 'b', null, 'b', 'a'].toSet(),
        ]
    }

    def "toImmutableUniqList() should produce expected result"() {
        given:
        def validItems = items.findAll { it != null }

        when:
        def list = CollectionUtils.toImmutableUniqList(items)

        then: "check collection interface"
        list == new LinkedHashSet(validItems).toList()

        when: "make sure it's immutable"
        list.add('foo')

        then:
        thrown(UnsupportedOperationException)

        when: "check array varargs interface"
        list = CollectionUtils.toImmutableUniqList(items.toArray(new String[0]))

        then:
        list == new LinkedHashSet(validItems).toList()

        when: "make sure it's immutable"
        list.add('foo')

        then:
        thrown(UnsupportedOperationException)

        where:
        items << [
            [null],
            [].toSet(),
            ['a', null, 'a'],
            ['a', null, 'a'].toSet(),
            ['a', null, 'b', 'b', 'a'],
            ['a', null, 'b', 'b', 'a'].toSet(),
        ]
    }

    def "partition() should throw in case of invalid arguments"() {
        when:
        def list = CollectionUtils.partition(coll, maxItems)

        then:
        thrown(RuntimeException)

        where:
        coll | maxItems
        null | 1
        []   | 0
        []   | -1
    }

    def "partition() should return expected result"() {
        when:
        def result = CollectionUtils.partition(list, max)

        then:
        result == expected

        where:
        list                                                                         | max | expected
        []                                                                           | 1   | []
        [1]                                                                          | 10  | [[1]]
        [1, 2]                                                                       | 10  | [[1, 2]]
        [1, null, null, 2]                                                           | 10  | [[1, 2]]
        [1, 2, 3]                                                                    | 1   | [[1], [2], [3]]
        [1, 2, 3, 4]                                                                 | 3   | [[1, 2, 3], [4]]
        [1, 2, 3, 4, 5, 6, 7, 8]                                                     | 3   | [[1, 2, 3], [4, 5, 6], [7, 8]]
        [1, 2, 3, 4, 5, 6, 7, 8, 9]                                                  | 3   | [[1, 2, 3], [4, 5, 6], [7, 8, 9]]
        [1, 2, 3, 4, 5, 6, 7, 8, 9] as Set                                           | 3   | [[1, 2, 3], [4, 5, 6], [7, 8, 9]]
        [null, null, 1, null, 2, 3, null, null, 4, 5, null, 6, 7, null, 8, 9] as Set | 3   | [[1, 2, 3], [4, 5, 6], [7, 8, 9]]
    }
}
