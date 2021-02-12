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

package com.github.tsc4j.micronaut2

import groovy.transform.ToString
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.runtime.context.scope.Refreshable

@ToString(includePackage = false, includeNames = true)
@Refreshable
@ConfigurationProperties("myapp.internal")
class MyMicronautBean {
    String a
    String b
    Set<String> list
}