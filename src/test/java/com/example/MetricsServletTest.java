package com.example;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class MetricsServletTest {

    @Test
    void exposesPrometheusFormat() throws Exception {
        var registry = new PrometheusMeterRegistry(io.micrometer.prometheus.PrometheusConfig.DEFAULT);
        var servlet  = new HelloServer.MetricsServlet(registry);

        var out  = new StringWriter();
        var resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(out));

        servlet.doGet(null, resp);

        verify(resp).setContentType("text/plain; version=0.0.4; charset=utf-8");

        // The scrape should not be empty (there is always the HELP/TYPE header block)
        assertFalse(out.toString().isBlank(), "Prometheus scrape must not be empty");
    }
}
