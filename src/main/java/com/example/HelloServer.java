package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jetty.JettyServerThreadPoolMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Simple Jetty server that now exposes:
 *   • GET /          → colourful welcome page
 *   • GET /weather   → today’s weather JSON (powered by OpenWeatherMap)
 *   • GET /metrics   → Prometheus metrics
 */
public class HelloServer {

    private static final PrometheusMeterRegistry REGISTRY =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    /** fetch once per process start to keep the demo lightweight */
    private static Weather cachedWeather;

    public static void main(String[] args) throws Exception {
        // --- Metrics binders -------------------------------------------------
        new JvmThreadMetrics().bindTo(REGISTRY);

        Server server = new Server(8080);
        new JettyServerThreadPoolMetrics(server.getThreadPool(), Tag.of("app", "hello")).bindTo(REGISTRY);

        // --- Jetty servlet wiring -------------------------------------------
        ServletContextHandler handler = new ServletContextHandler();
        handler.addServlet(HelloServlet.class, "/");
        handler.addServlet(new ServletHolder(new MetricsServlet(REGISTRY)), "/metrics");
        handler.addServlet(WeatherServlet.class, "/weather");

        server.setHandler(handler);
        server.start();
        System.out.printf("🚀  Server started on http://localhost:%d%n", 8080);
        server.join();
    }

    /* ------------------ inner servlets ----------------------------------- */

    public static class HelloServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/html");

            String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><title>Hello Server</title></head>
                <body style="font-family:Arial;">
                  <h1>Hello, World! 🌞</h1>
                  <p>Try <code>/weather</code> for today’s weather,<br/>
                     or <code>/metrics</code> for Prometheus output.</p>
                </body>
                </html>
                """;

            resp.getWriter().write(html);
        }
    }

    /** Returns Prometheus metrics */
    public static class MetricsServlet extends HttpServlet {
        private final transient PrometheusMeterRegistry registry;
        public MetricsServlet(PrometheusMeterRegistry registry) { this.registry = registry; }
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/plain; version=0.0.4; charset=utf-8");
            resp.getWriter().write(registry.scrape());
        }
    }

    /** Simple JSON endpoint */
    public static class WeatherServlet extends HttpServlet {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private static final HttpClient  CLIENT = HttpClient.newHttpClient();

        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            if (cachedWeather == null || !cachedWeather.date.equals(LocalDate.now())) {
                cachedWeather = fetchWeather();
            }
            resp.setContentType("application/json");
            resp.getWriter().write(MAPPER.writeValueAsString(cachedWeather));
        }

        private Weather fetchWeather() throws IOException {
            String apiKey = System.getenv().getOrDefault("OPENWEATHER_KEY", "");
            String city   = System.getenv().getOrDefault("CITY", "London");

            if (apiKey.isEmpty()) {
                return new Weather(LocalDate.now(), city, "API-key-missing", 0);
            }

            String url = String.format(
                    "https://api.openweathermap.org/data/2.5/weather?q=%s&units=metric&appid=%s",
                    city, apiKey);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response =
                        CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                JsonNode json = MAPPER.readTree(response.body());
                String    desc = json.path("weather").get(0).path("description").asText();
                double    temp = json.path("main").path("temp").asDouble();

                return new Weather(LocalDate.now(), city, desc, temp);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }
    }

    /* ------------------ tiny DTO class ----------------------------------- */
    public record Weather(LocalDate date, String city, String description, double temperatureC) { }
}
