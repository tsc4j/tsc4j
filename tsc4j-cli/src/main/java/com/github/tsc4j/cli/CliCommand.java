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

package com.github.tsc4j.cli;

import com.github.tsc4j.api.Ordered;

import java.util.concurrent.Callable;

/**
 * Cli command interface.
 */
public interface CliCommand extends Callable<Integer>, Ordered, Comparable<CliCommand> {
    /**
     * Returns command name.
     *
     * @return command name.
     */
    String getName();


    /**
     * Returns command group name.
     *
     * @return group name
     */
    String getGroup();
}
