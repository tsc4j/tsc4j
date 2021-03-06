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

package com.github.tsc4j.spring.app

import com.github.tsc4j.spring.Tsc4jSpringContextRefresher
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.annotation.DirtiesContext
import spock.lang.Requires
import spock.lang.Unroll

@Slf4j
@Unroll
@DirtiesContext
@SpringBootTest(properties = [
    'tsc4j.spring.refresh.enabled=false'
])
@Requires({ sys.special == 'true' })
class Tsc4jSpringContextRefresherDisabledSpec extends SpringSpec {
    @Autowired
    ApplicationContext appCtx

    def "should not be available in the context if disabled"() {
        when:
        def indicator = appCtx.getBean(Tsc4jSpringContextRefresher)

        then:
        thrown(NoSuchBeanDefinitionException)
        indicator == null
    }
}
