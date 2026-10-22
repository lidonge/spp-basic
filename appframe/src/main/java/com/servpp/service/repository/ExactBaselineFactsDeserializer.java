package com.servpp.service.repository;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

/** Untyped RPC baselines must retain the exact facts used for numeric change comparison. */
public final class ExactBaselineFactsDeserializer extends JsonDeserializer<Object> {
    private static final ObjectReader FACTS = new ObjectMapper().readerFor(Object.class)
            .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    @Override public Object deserialize(JsonParser parser,DeserializationContext context) throws IOException {
        return FACTS.readValue(parser);
    }
}
