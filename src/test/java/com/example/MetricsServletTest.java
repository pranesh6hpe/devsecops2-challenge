package com.example;

import io.micrometer.core.instrument.Counter;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

/**
 * Ensure that /metrics returns at least one line of Prometheus output.
 */
class MetricsServletTest {

    @Test
    void exposesPrometheusFormat() throws Exception {
        // 1) Create a fresh registry
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(
                io.micrometer.prometheus.PrometheusConfig.DEFAULT);

        // 2) Register a dummy counter so that scrape() is never empty
        Counter.builder("dummy_metric_total")
               .description("A dummy counter to ensure there is at least one metric")
               .register(registry)
               .increment();

        // 3) Instantiate the servlet with our registry
        var servlet = new HelloServer.MetricsServlet(registry);

        // 4) Prepare a mock HttpServletResponse that writes into a StringWriter
        var out  = new StringWriter();
        var resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(out));

        // 5) Call doGet(...) and capture the output
        servlet.doGet(null, resp);

        // 6) Verify that the servlet set the correct content type
        verify(resp).setContentType("text/plain; version=0.0.4; charset=utf-8");

        // 7) The scrape() result must not be blank
        String result = out.toString();
        assertFalse(result.isBlank(), "Expected Prometheus output to be non-empty");

        
    }
}
