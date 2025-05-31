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
 *   • GET /weather?city=…   → JSON from Open-Meteo
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
        new JettyServerThreadPoolMetrics(
                server.getThreadPool(),
                List.of(Tag.of("app", "hello"))
        ).bindTo(REGISTRY);

        ServletContextHandler handler = new ServletContextHandler();
        handler.addServlet(IndexServlet.class, "/");
        handler.addServlet(new ServletHolder(new MetricsServlet(REGISTRY)), "/metrics");
        handler.addServlet(WeatherServlet.class, "/weather");

        server.setHandler(handler);
        server.start();
        LOG.info("🚀  http://localhost:8080");
        server.join();
    }

    /* ──────────────────────────────  SERVLETS  ───────────────────────────── */

    /** Serves the single-page UI */
    public static class IndexServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {

            resp.setContentType("text/html");
            try (var writer = resp.getWriter()) {

               writer.write("""
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Weather Now</title>
  <style>
    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
    }

    html, body {
      height: 100%;
      font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
      background: linear-gradient(135deg, #1e3c72, #2a5298);
      animation: gradient 10s ease infinite;
      background-size: 400% 400%;
    }

    @keyframes gradient {
      0% { background-position: 0% 50%; }
      50% { background-position: 100% 50%; }
      100% { background-position: 0% 50%; }
    }

    body {
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 1rem;
    }

    .card {
      background: rgba(255, 255, 255, 0.1);
      border-radius: 1.5rem;
      padding: 2rem 2.5rem;
      max-width: 350px;
      width: 100%;
      text-align: center;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
      backdrop-filter: blur(15px);
      color: #fff;
    }

    .card h2 {
      font-size: 2rem;
      margin-bottom: 1.2rem;
    }

    .city-input {
      width: 100%;
      padding: 0.7rem 1rem;
      font-size: 1rem;
      border: none;
      border-radius: 0.7rem;
      margin-bottom: 1rem;
      outline: none;
      background: rgba(255, 255, 255, 0.2);
      color: #fff;
      transition: background 0.3s ease;
    }

    .city-input::placeholder {
      color: #ddd;
    }

    .city-input:focus {
      background: rgba(255, 255, 255, 0.3);
    }

    button {
      background-color: #00b4d8;
      border: none;
      border-radius: 0.7rem;
      padding: 0.7rem 1.5rem;
      font-size: 1rem;
      color: #fff;
      cursor: pointer;
      transition: background-color 0.3s ease;
    }

    button:hover {
      background-color: #0077b6;
    }

    .result {
      margin-top: 1.5rem;
      font-size: 1.1rem;
    }

    .temp {
      font-size: 2.8rem;
      font-weight: 700;
      margin: 0.5rem 0;
    }

    footer {
      position: fixed;
      bottom: 0.8rem;
      right: 1rem;
      font-size: 0.8rem;
      color: #ccc;
    }

    footer a {
      color: #ffffffaa;
      text-decoration: none;
    }

    footer a:hover {
      text-decoration: underline;
    }
  </style>
</head>
<body>
  <div class="card">
    <h2>🌤️ Weather Now</h2>
    <input type="text" id="city" class="city-input" placeholder="Enter city e.g. Paris">
    <button onclick="getWeather()">Get Weather</button>
    <div id="output" class="result"></div>
  </div>

  <footer><a href="/metrics" target="_blank">Prometheus metrics</a></footer>

  <script>
    async function getWeather() {
      const city = document.getElementById('city').value.trim();
      const out = document.getElementById('output');
      if (!city) {
        out.textContent = 'Please enter a city.';
        return;
      }
      out.textContent = 'Fetching…';
      try {
        const res = await fetch('/weather?city=' + encodeURIComponent(city));
        if (!res.ok) throw new Error(res.status);
        const j = await res.json();
        out.innerHTML = `
          <div>${j.city} — ${j.date}</div>
          <div class="temp">${j.temperatureC.toFixed(1)} °C</div>
          <div>${j.description}</div>`;
      } catch (e) {
        out.textContent = 'Error: ' + e.message;
      }
    }
  </script>
</body>
</html>
""");


            } catch (IOException ioe) {
                LOG.error("Unable to render index page", ioe);
                try {
                    resp.sendError(500, "Internal error");
                } catch (IOException ioe2) {
                    LOG.error("Unable to send 500 response", ioe2);
                }
            }
        }
    }

    /** Prometheus /metrics endpoint */
    public static class MetricsServlet extends HttpServlet {
        private final transient PrometheusMeterRegistry registry;

        MetricsServlet(PrometheusMeterRegistry registry) {
            this.registry = registry;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {

            resp.setContentType("text/plain; version=0.0.4; charset=utf-8");
            try (var writer = resp.getWriter()) {
                writer.write(registry.scrape());
            } catch (IOException ioe) {
                LOG.error("Unable to write metrics", ioe);
                try {
                    resp.sendError(500, "Internal error");
                } catch (IOException ioe2) {
                    LOG.error("Unable to send 500 response", ioe2);
                }
            }
        }
    }

    /** /weather?city=London → JSON endpoint */
    public static class WeatherServlet extends HttpServlet {
        private static final HttpClient CLIENT = HttpClient.newHttpClient();

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {

            String city = req.getParameter("city");
            if (city == null || city.isBlank()) {
                try {
                    resp.sendError(400, "city param missing");
                } catch (IOException ioe) {
                    LOG.error("Unable to send 400 response", ioe);
                }
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
                try {
                    resp.sendError(502, "Unable to fetch weather");
                } catch (IOException ioe2) {
                    LOG.error("Unable to send 502 response", ioe2);
                }
            }
        }

        /* Hit Open-Meteo geocoding then forecast */
        private Weather fetchWeather(String city) throws IOException {
            try {
                /* 1) geocode */
                String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                        + URLEncoder.encode(city, StandardCharsets.UTF_8)
                        + "&language=en";
                JsonNode geo = getJson(geoUrl);
                JsonNode loc = geo.path("results").get(0);
                double lat = loc.path("latitude").asDouble();
                double lon = loc.path("longitude").asDouble();

                /* 2) forecast */
                String api = "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&current_weather=true";
                JsonNode cur = getJson(String.format(api, lat, lon))
                               .path("current_weather");
                double temp = cur.path("temperature").asDouble();
                double wind = cur.path("windspeed").asDouble();

                String desc = "Wind " + wind + " km/h";
                return new Weather(LocalDate.now(),
                                   loc.path("name").asText(),
                                   desc,
                                   temp);

            } catch (Exception e) {  // includes empty JSON array, etc.
                throw new IOException("Cannot fetch weather for " + city, e);
            }
        }

        /** Separated for easy stubbing in tests */
        protected JsonNode getJson(String url) throws IOException {
            try {
                HttpResponse<String> r = CLIENT.send(
                        HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                return MAPPER.readTree(r.body());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();          // Sonar rule
                throw new IOException("Interrupted", ie);
            }
        }
    }

    /* Simple DTO for JSON serialisation */
    public record Weather(LocalDate date,
                          String     city,
                          String     description,
                          double     temperatureC) {}
}
