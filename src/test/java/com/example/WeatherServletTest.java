package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/** Covers ➊ happy-path, ➋ 400 branch, ➌ 502 branch */
class WeatherServletTest {

    /* ───── ➊ OK 200 ─────────────────────────────────────────────── */
    @Test
    void returnsJsonOnSuccess() throws Exception {
        // stub servlet that skips real HTTP calls
        var servlet = new HelloServer.WeatherServlet() {
            @Override
            protected JsonNode getJson(String any) {          // ← protected
                // two different responses: geocoding & forecast
                if (any.contains("geocoding")) {
                    return new ObjectMapper().readTree("""
                        { "results":[{ "name":"Paris",
                                       "latitude":48.8,
                                       "longitude":2.3 }] }""");
                }
                return new ObjectMapper().readTree("""
                        { "current_weather":
                          { "temperature":20.1, "windspeed":3.0 } }""");
            }
        };

        var req = mock(HttpServletRequest.class);
        when(req.getParameter("city")).thenReturn("Paris");

        var out  = new StringWriter();
        var resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(out));

        servlet.doGet(req, resp);

        assertTrue(out.toString().contains("\"city\":\"Paris\""));
    }

    /* ───── ➋ city param missing → 400 ───────────────────────────── */
    @Test
    void returns400WhenParamMissing() throws Exception {
        var servlet = new HelloServer.WeatherServlet();
        var req  = mock(HttpServletRequest.class);
        var resp = mock(HttpServletResponse.class);

        servlet.doGet(req, resp);

        verify(resp).sendError(eq(400), anyString());
    }

    /* ───── ➌ Upstream failure → 502 ─────────────────────────────── */
    @Test
    void returns502OnUpstreamFailure() throws Exception {
        var servlet = new HelloServer.WeatherServlet() {
            @Override
            protected JsonNode getJson(String any) throws IOException { // ← protected
                throw new IOException("boom");
            }
        };

        var req = mock(HttpServletRequest.class);
        when(req.getParameter("city")).thenReturn("Nowhere");

        var resp = mock(HttpServletResponse.class);
        servlet.doGet(req, resp);

        verify(resp).sendError(eq(502), anyString());
    }
}
