package com.example;

import io.micrometer.core.instrument.binder.jetty.JettyServerThreadPoolMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class HelloServer {

    /**
     * The Prometheus registry is shared application-wide.
     * No longer transient—this is not a servlet instance field.
     */
    private static final PrometheusMeterRegistry REGISTRY =
        new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);

        // Bind JVM & Jetty thread-pool metrics into the registry
        new JvmThreadMetrics().bindTo(REGISTRY);
        new JettyServerThreadPoolMetrics(
            server.getThreadPool(), "jetty", null
        ).bindTo(REGISTRY);

        ServletContextHandler handler = new ServletContextHandler();
        handler.addServlet(HelloServlet.class, "/");
        handler.addServlet(new ServletHolder(new MetricsServlet(REGISTRY)), "/metrics");

        server.setHandler(handler);
        server.start();
        System.out.println("🚀 Server started on http://localhost:8080");
        server.join();
    }

    public static class HelloServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            resp.setContentType("text/html");

            String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Hello Server</title>
                    <style>
                        body {
                            margin: 0;
                            padding: 0;
                            font-family: 'Comic Sans MS', cursive, sans-serif;
                            background: linear-gradient(135deg, #ff9a9e 0%, #fad0c4 99%);
                            display: flex;
                            flex-direction: column;
                            align-items: center;
                            justify-content: center;
                            height: 100vh;
                            color: #fff;
                            text-align: center;
                        }
                        h1 {
                            font-size: 4em;
                            margin-bottom: 0.3em;
                            text-shadow: 2px 2px 5px #000;
                        }
                        button {
                            font-size: 1.5em;
                            padding: 0.5em 1em;
                            border: none;
                            border-radius: 10px;
                            background-color: #ff6f91;
                            color: white;
                            cursor: pointer;
                            box-shadow: 0 4px 6px rgba(0,0,0,0.3);
                            transition: transform 0.2s, background-color 0.2s;
                        }
                        button:hover {
                            transform: scale(1.1);
                            background-color: #ff3e6c;
                        }
                        #datetime {
                            margin-top: 20px;
                            font-size: 1.3em;
                            background: rgba(255, 255, 255, 0.2);
                            padding: 0.5em 1em;
                            border-radius: 12px;
                            box-shadow: 0 4px 6px rgba(0,0,0,0.3);
                        }
                        .emoji {
                            font-size: 3em;
                            margin-top: 20px;
                            animation: bounce 1s infinite alternate;
                        }
                        @keyframes bounce {
                            from { transform: translateY(0); }
                            to   { transform: translateY(-20px); }
                        }
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

            resp.getWriter().write(html);
            System.out.println("👋 Served funky page to a visitor.");
        }
    }

    /** Dumps Prometheus metrics in the Prometheus text format */
    public static class MetricsServlet extends HttpServlet {
        // HttpServlet is Serializable, so mark this non-serializable field transient
        private final transient PrometheusMeterRegistry registry;

        public MetricsServlet(PrometheusMeterRegistry registry) {
            this.registry = registry;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/plain");
            resp.getWriter().write(registry.scrape());
        }
    }
}
