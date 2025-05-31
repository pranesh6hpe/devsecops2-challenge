package com.example;

import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

class HelloServerTest {

    @Test
    void indexReturnsHtml() throws Exception {
        var servlet = new HelloServer.IndexServlet();
        var resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        servlet.doGet(null, resp);

        verify(resp).setContentType("text/html");
    }

    @Test
    void weatherMissingParamGets400() throws Exception {
        var servlet = new HelloServer.WeatherServlet();
        var req  = mock(HttpServletRequest.class);
        var resp = mock(HttpServletResponse.class);

        servlet.doGet(req, resp);

        verify(resp).sendError(eq(400), anyString());
    }
}
