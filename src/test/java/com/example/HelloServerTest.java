package com.example;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class HelloServerTest {

    @Test
    void helloEndpointReturnsHtml() throws Exception {
        var servlet = new HelloServer.HelloServlet();
        var req = mock(HttpServletRequest.class);
        var resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        servlet.doGet(req, resp);
        verify(resp).setContentType("text/html");
    }

    @Test
    void weatherEndpointReturnsJson() throws Exception {
        var servlet = new HelloServer.WeatherServlet();
        var req = mock(HttpServletRequest.class);
        var resp = mock(HttpServletResponse.class);
        var sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));
        servlet.doGet(req, resp);
        verify(resp).setContentType("application/json");
        assertTrue(sw.toString().contains("\"city\""));
    }
}
