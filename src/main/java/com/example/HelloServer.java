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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Fancy weather mini-app:
 *   • GET /                 → single-page UI (HTML + CSS + JS)
 *   • GET /weather?city=... → JSON from Open-Meteo
 *   • GET /metrics          → Prometheus metrics
 */
public class HelloServer {

    private static final Logger LOG = LoggerFactory.getLogger(HelloServer.class);

    private static final PrometheusMeterRegistry REGISTRY =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private static final ObjectMapper MAPPER =
            new ObjectMapper().findAndRegisterModules();   // handles LocalDate

    public static void main(String[] args) throws Exception {
        new JvmThreadMetrics().bindTo(REGISTRY);

        Server server = new Server(8080);
        new JettyServerThreadPoolMetrics(server.getThreadPool(),
                List.of(Tag.of("app", "hello"))).bindTo(REGISTRY);

        ServletContextHandler handler = new ServletContextHandler();
        handler.addServlet(IndexServlet.class, "/");
        handler.addServlet(new ServletHolder(new MetricsServlet(REGISTRY)), "/metrics");
        handler.addServlet(WeatherServlet.class, "/weather");

        server.setHandler(handler);
        server.start();
        LOG.info("🚀  http://localhost:8080");
        server.join();
    }

    /* ─────────────────────────────  SERVLETS  ─────────────────────────── */

    public static class IndexServlet extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/html");
            try (var writer = resp.getWriter()) {
                writer.write("""
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Weather Now</title>
  <style>… (elided CSS) …</style>
</head>
<body>
  <div class="card">… (elided HTML) …</div>
  <footer><a href="/metrics" target="_blank">Prometheus metrics</a></footer>
<script>… (elided JS) …</script>
</body>
</html>
""");
            } catch (IOException ioe) {
                LOG.error("Unable to render index page", ioe);
                resp.sendError(500, "Internal error");
            }
        }
    }

    /** Prometheus /metrics */
    public static class MetricsServlet extends HttpServlet {
        private final transient PrometheusMeterRegistry registry;
        MetricsServlet(PrometheusMeterRegistry registry){ this.registry = registry; }

        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/plain; version=0.0.4; charset=utf-8");
            try (var writer = resp.getWriter()) {
                writer.write(registry.scrape());
            } catch (IOException ioe) {
                LOG.error("Unable to write metrics", ioe);
                resp.sendError(500, "Internal error");
            }
        }
    }

    /** /weather?city=London → JSON */
    public static class WeatherServlet extends HttpServlet {
        private static final HttpClient CLIENT = HttpClient.newHttpClient();

        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String city = req.getParameter("city");
            if (city == null || city.isBlank()) {
                resp.sendError(400, "city param missing");
                return;
            }

            try {
                Weather w = fetchWeather(city.trim());
                resp.setContentType("application/json");
                try (var writer = resp.getWriter()) {
                    writer.write(MAPPER.writeValueAsString(w));
                }
            } catch (IOException ioe) {
                LOG.error("Error fetching weather for {}", city, ioe);
                resp.sendError(502, "Unable to fetch weather");
            }
        }

        /* Hit Open-Meteo geocoding then forecast */
        private Weather fetchWeather(String city) throws IOException {
            try {
                // 1) geocode
                String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                        + URLEncoder.encode(city, StandardCharsets.UTF_8)
                        + "&language=en";
                JsonNode geo = getJson(geoUrl);
                JsonNode loc = geo.path("results").get(0);
                double lat  = loc.path("latitude").asDouble();
                double lon  = loc.path("longitude").asDouble();

                // 2) forecast
                String api = "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&current_weather=true";
                JsonNode cur = getJson(String.format(api, lat, lon)).path("current_weather");
                double temp = cur.path("temperature").asDouble();
                double wind = cur.path("windspeed").asDouble();

                String desc = "Wind " + wind + " km/h";
                return new Weather(LocalDate.now(), loc.path("name").asText(), desc, temp);

            } catch (Exception e) {          // includes JSON array empty, etc.
                throw new IOException("Cannot fetch weather for " + city, e);
            }
        }

        private JsonNode getJson(String url) throws IOException {
            try {
                HttpResponse<String> r = CLIENT.send(
                        HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                return MAPPER.readTree(r.body());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();          // Sonar rule: re-interrupt
                throw new IOException("Interrupted", ie);
            }
        }
    }

    /* DTO */
    public record Weather(LocalDate date, String city, String description, double temperatureC) {}
}
