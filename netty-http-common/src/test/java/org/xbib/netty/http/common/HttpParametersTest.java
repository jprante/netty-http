package org.xbib.netty.http.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpParametersTest {

    @Test
    void testParameters() throws MalformedInputException, UnmappableCharacterException {
        HttpParameters httpParameters = new HttpParameters();
        httpParameters.add("a", "b");
        String query = httpParameters.getAsQueryString(false);
        assertEquals("a=b", query);
    }

    @Test
    void testUtf8() throws MalformedInputException, UnmappableCharacterException {
        HttpParameters httpParameters = new HttpParameters("text/plain; charset=utf-8");
        httpParameters.add("Hello", "Jörg");
        String query = httpParameters.getAsQueryString(false);
        assertEquals("Hello=Jörg", query);
    }
}
