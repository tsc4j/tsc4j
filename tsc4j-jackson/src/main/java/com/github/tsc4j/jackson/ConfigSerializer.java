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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.typesafe.config.Config;

import java.io.IOException;

/**
 * Jackson serializer that can serialize {@link Config} instance.
 */
final class ConfigSerializer extends StdSerializer<Config> {
    private static final long serialVersionUID = 1L;

    ConfigSerializer() {
        super(Config.class);
    }

    @Override
    public void serialize(Config config, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeObject(config.root());
    }
}
