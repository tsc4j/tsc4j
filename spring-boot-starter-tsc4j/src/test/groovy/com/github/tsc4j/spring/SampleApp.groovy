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

package com.github.tsc4j.spring

import com.github.tsc4j.api.ReloadableConfig
import com.typesafe.config.Config
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

import javax.annotation.PostConstruct

@Slf4j
@SpringBootApplication
class SampleApp {
    @Autowired
    Config config

    @Autowired
    ReloadableConfig reloadableConfig

    void main(String... args) {
        SpringApplication.run(SampleApp, args)
    }

    @PostConstruct
    void init() {
        log.info("Using reloadable config: {}", reloadableConfig)
        log.info("Using config:            {}", config)
        log.info("configs are the same:    {}", config == reloadableConfig.getSync())
    }
}
