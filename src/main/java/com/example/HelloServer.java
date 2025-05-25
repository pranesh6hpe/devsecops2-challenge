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
import java.util.Collections;

/**
 * Jetty server exposing:
 *   • GET /            → welcome page
 *   • GET /weather     → today’s weather from Open-Meteo
 *   • GET /metrics     → Prometheus metrics
 */
public class HelloServer {

    private static final PrometheusMeterRegistry REGISTRY =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    /** cache for the current day to avoid hammering the free API */
    private static Weather cachedWeather;

    public static void main(String[] args) throws Exception {
        // ── Metrics binders ──────────────────────────────────────────────
        new JvmThreadMetrics().bindTo(REGISTRY);

        Server server = new Server(8080);

        // Jetty thread-pool metrics need Iterable<Tag>, so wrap Tag in a singleton list
        new JettyServerThreadPoolMetrics(
                server.getThreadPool(),
                Collections.singletonList(Tag.of("app", "hello"))
        ).bindTo(REGISTRY);

        // ── Servlet wiring ───────────────────────────────────────────────
        ServletContextHandler handler = new ServletContextHandler();
        handler.addServlet(HelloServlet.class, "/");
        handler.addServlet(new ServletHolder(new MetricsServlet(REGISTRY)), "/metrics");
        handler.addServlet(WeatherServlet.class, "/weather");

        server.setHandler(handler);
        server.start();
        System.out.println("🚀  Server listening on http://localhost:8080");
        server.join();
    }

    /* ------------ inner servlets ------------------------------------- */

    public static class HelloServlet extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/html");
            resp.getWriter().write("""
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <title>Hello Weather</title></head><body style="font-family:sans-serif">
                <h1>Hello, World! 🌤️</h1>
                <p>• <a href="/weather">/weather</a> – today’s weather (Open-Meteo)</p>
                <p>• <a href="/metrics">/metrics</a> – Prometheus metrics</p>
                </body></html>
                """);
        }
    }

    public static class MetricsServlet extends HttpServlet {
        private final transient PrometheusMeterRegistry registry;
        public MetricsServlet(PrometheusMeterRegistry registry) { this.registry = registry; }
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/plain; version=0.0.4; charset=utf-8");
            resp.getWriter().write(registry.scrape());
        }
    }

    public static class WeatherServlet extends HttpServlet {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private static final HttpClient  CLIENT = HttpClient.newHttpClient();

        // Default coordinates = London; override via env-vars in K8s/Helm
        private static final double LAT  = Double.parseDouble(System.getenv().getOrDefault("LAT",  "51.5074"));
        private static final double LON  = Double.parseDouble(System.getenv().getOrDefault("LON", "-0.1278"));
        private static final String CITY = System.getenv().getOrDefault("CITY", "London");

        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            if (cachedWeather == null || !cachedWeather.date.equals(LocalDate.now())) {
                cachedWeather = fetchWeather();
            }
            resp.setContentType("application/json");
            resp.getWriter().write(MAPPER.writeValueAsString(cachedWeather));
        }

        private Weather fetchWeather() throws IOException {
            String url = String.format(
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                  + "&current_weather=true&hourly=temperature_2m", LAT, LON);

            try {
                HttpResponse<String> response = CLIENT.send(
                        HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                JsonNode current = MAPPER.readTree(response.body()).path("current_weather");
                double   tempC   = current.path("temperature").asDouble();
                String   summary = "Temp " + tempC + " °C";

                return new Weather(LocalDate.now(), CITY, summary, tempC);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }
    }

    /* simple record for JSON serialisation */
    public record Weather(LocalDate date, String city, String description, double temperatureC) { }
}
