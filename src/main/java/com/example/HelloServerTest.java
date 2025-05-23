package com.example;

import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class HelloServerTest {
    @Test
    void returnsHtml() throws Exception {
        var servlet = new HelloServer.HelloServlet();
        var req  = mock(HttpServletRequest.class);
        var resp = mock(HttpServletResponse.class);

        var writer = new java.io.PrintWriter(System.out);
        when(resp.getWriter()).thenReturn(writer);

        servlet.doGet(req, resp);
        verify(resp).setContentType("text/html");
    }
}
