package com.example;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.io.PrintWriter;

import static org.mockito.Mockito.*;

class MetricsServletTest {

    @Test
    void exposesPrometheusFormat() throws Exception {
        var servlet = new HelloServer.MetricsServlet(new PrometheusMeterRegistry(io.micrometer.prometheus.PrometheusConfig.DEFAULT));

        var out  = new StringWriter();
        var resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(out));

        servlet.doGet(null, resp);

        verify(resp).setContentType("text/plain; version=0.0.4; charset=utf-8");
        assert(out.toString().contains("# TYPE"));   // Prometheus header
    }
}
