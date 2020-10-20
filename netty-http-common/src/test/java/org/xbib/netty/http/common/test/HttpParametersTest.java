package org.xbib.netty.http.common.test;

import org.junit.jupiter.api.Test;
import org.xbib.netty.http.common.HttpParameters;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpParametersTest {

    @Test
    void testSimpleParameter() throws MalformedInputException, UnmappableCharacterException {
        HttpParameters httpParameters = new HttpParameters();
        httpParameters.addRaw("a", "b");
        String query = httpParameters.getAsQueryString(false);
        assertEquals("a=b", query);
    }

    @Test
    void testSimpleParameters() throws MalformedInputException, UnmappableCharacterException {
        HttpParameters httpParameters = new HttpParameters();
        httpParameters.addRaw("a", "b");
        httpParameters.addRaw("c", "d");
        String query = httpParameters.getAsQueryString(false);
        assertEquals("a=b&c=d", query);
    }

    @Test
    void testMultiParameters() throws MalformedInputException, UnmappableCharacterException {
        HttpParameters httpParameters = new HttpParameters();
        httpParameters.addRaw("a", "b");
        httpParameters.addRaw("a", "c");
        httpParameters.addRaw("a", "d");
        String query = httpParameters.getAsQueryString(false);
        assertEquals("a=b&a=c&a=d", query);
    }

    @Test
    void testUtf8() throws MalformedInputException, UnmappableCharacterException {
        HttpParameters httpParameters = new HttpParameters("text/plain; charset=utf-8");
        httpParameters.addRaw("Hello", "Jörg");
        String query = httpParameters.getAsQueryString(false);
        assertEquals("Hello=Jörg", query);
    }
}
