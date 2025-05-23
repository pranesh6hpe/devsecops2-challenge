package com.example;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.jetty.JettyServerThreadPoolMetrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Minimal Jetty server that serves:
 *   /          – colourful HTML page
 *   /metrics   – Prometheus text metrics scraped by Prometheus
 */
public class HelloServer {

    private static final Logger log = LoggerFactory.getLogger(HelloServer.class);

    /** 
     * The Prometheus registry is shared application-wide.
     * Marked transient so it's not picked up by servlet serialization.
     */
    private static transient final PrometheusMeterRegistry REGISTRY =
        new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);

        // Bind JVM + Jetty thread-pool metrics
        new JvmThreadMetrics().bindTo(REGISTRY);
        new JettyServerThreadPoolMetrics(
            server.getThreadPool(),
            Tags.of("component", "jetty")
        ).bindTo(REGISTRY);

        // Mount our two servlets
        ServletContextHandler ctx = new ServletContextHandler();
        ctx.addServlet(HelloServlet.class, "/");
        ctx.addServlet(new ServletHolder(new MetricsServlet(REGISTRY)), "/metrics");
        server.setHandler(ctx);

        server.start();
        log.info("🚀  Server started on http://localhost:8080  (metrics at /metrics)");
        server.join();
    }

    /** Serves the colourful HTML landing page */
    public static class HelloServlet extends HttpServlet {
        private static final Logger log = LoggerFactory.getLogger(HelloServlet.class);

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
            resp.setContentType("text/html");
            String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Hello Server</title>
                    <style>
                        /* … all your CSS here … */
                        body { margin: 0; padding: 0; /* etc. */ }
                        /* rest of your styles… */
                    </style>
                    <script>
                        function showDateTime() {
                            const now = new Date();
                            document.getElementById('datetime').textContent =
                                'Current Date & Time: ' + now.toLocaleString();
                        }
                    </script>
                </head>
                <body>
                    <h1>Hello, World! 🌍</h1>
                    <button onclick="showDateTime()">Show Date & Time</button>
                    <div id="datetime"></div>
                    <div class="emoji">😎🎉🚀</div>
                </body>
                </html>
                """;

            try {
                resp.getWriter().write(html);
                log.info("👋  Served funky page to a visitor: {}", req.getRemoteAddr());
            } catch (IOException e) {
                log.error("Failed to write HTML response", e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    /** Dumps Prometheus metrics in the Prometheus text format */
    public static class MetricsServlet extends HttpServlet {
        private static final Logger log = LoggerFactory.getLogger(MetricsServlet.class);
        private final PrometheusMeterRegistry registry;

        public MetricsServlet(PrometheusMeterRegistry registry) {
            this.registry = registry;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/plain; version=0.0.4; charset=utf-8");

            try {
                resp.getWriter().write(registry.scrape());
                log.debug("🔍  Served metrics to {}", req.getRemoteAddr());
            } catch (IOException e) {
                log.error("Failed to write metrics response", e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }
}
