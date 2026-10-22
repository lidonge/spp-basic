package com.servpp.service.numeric;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/** PHY integer representations reject fractional input instead of silently truncating it. */
public final class ExactIntegerDeserializer extends JsonDeserializer<BigInteger> {
    @Override public BigInteger deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT && parser.currentToken() != JsonToken.VALUE_NUMBER_FLOAT
                && parser.currentToken() != JsonToken.VALUE_STRING)
            throw JsonMappingException.from(parser, "INTEGER_INPUT_REQUIRED");
        try { return new BigDecimal(parser.getText()).toBigIntegerExact(); }
        catch (NumberFormatException | ArithmeticException failure) {
            throw JsonMappingException.from(parser, "INTEGER_INPUT_REQUIRED", failure);
        }
    }
}
