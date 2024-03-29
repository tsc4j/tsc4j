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

package com.github.tsc4j.jackson;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.NonNull;

import java.util.Collection;

final class JacksonTsc4jModule extends SimpleModule {
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unchecked")
    JacksonTsc4jModule(@NonNull Collection<JsonDeserializer> deserializers,
                       @NonNull Collection<JsonSerializer> serializers) {
        // register deserializers
        deserializers.forEach(e -> addDeserializer(e.handledType(), e));

        // register serializers
        serializers.forEach(this::addSerializer);
    }
}
