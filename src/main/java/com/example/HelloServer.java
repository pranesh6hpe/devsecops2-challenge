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
import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


/**
 * Fancy weather mini-app:
 *   • GET /           → single-page UI (HTML + CSS + JS)
 *   • GET /weather?city=London → JSON from Open-Meteo
 *   • GET /metrics    → Prometheus metrics
 */
public class HelloServer {

    private static final PrometheusMeterRegistry REGISTRY =
            new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private static final ObjectMapper MAPPER =
            new ObjectMapper().findAndRegisterModules(); // handles LocalDate

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
        System.out.println("🚀  http://localhost:8080");
        server.join();
    }

    /* ─────────────────────────────  SERVLETS  ─────────────────────────── */

    public static class IndexServlet extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/html");
            resp.getWriter().write("""
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Weather Now</title>
  <style>
    :root { font-family: system-ui, sans-serif; }
    body  { margin:0; padding:0; height:100vh;
            display:flex; align-items:center; justify-content:center;
            background: linear-gradient(135deg,#4facfe 0%,#00f2fe 100%); }
    .card { background:#fff; padding:2rem 3rem; border-radius:1.2rem;
            box-shadow:0 8px 24px rgba(0,0,0,.15); width:320px; text-align:center; }
    .city-input { width:100%; padding:.6rem 1rem; font-size:1rem;
                  border:1px solid #ccc; border-radius:.5rem; }
    button { margin-top:1rem; padding:.6rem 1.4rem; font-size:1rem;
             border:none; border-radius:.5rem; background:#4facfe; color:#fff;
             cursor:pointer; transition:background .2s; }
    button:hover { background:#3a8de0; }
    .result { margin-top:1.4rem; font-size:1.1rem; }
    .temp   { font-size:2.6rem; font-weight:700; }
    footer  { position:fixed; bottom:.5rem; right:.7rem; font-size:.8rem; color:#eee; }
    a { color:#fff; }
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
  if(!city) return;
  const out = document.getElementById('output');
  out.textContent = 'Fetching…';
  try {
    const res = await fetch(`/weather?city=`+encodeURIComponent(city));
    if(!res.ok) throw new Error(res.status);
    const j = await res.json();
    out.innerHTML = `
      <div>${j.city} — ${j.date}</div>
      <div class="temp">${j.temperatureC.toFixed(1)} °C</div>
      <div>${j.description}</div>`;
  } catch(e){ out.textContent = 'Error: '+e; }
}
</script>
</body>
</html>
""");
        }
    }

    /** Prometheus /metrics */
    public static class MetricsServlet extends HttpServlet {
        private final transient PrometheusMeterRegistry registry;
        MetricsServlet(PrometheusMeterRegistry registry){this.registry=registry;}
        @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException{
            resp.setContentType("text/plain; version=0.0.4; charset=utf-8");
            resp.getWriter().write(registry.scrape());
        }
    }

    /** /weather?city=London → JSON */
    public static class WeatherServlet extends HttpServlet {
        private static final HttpClient CLIENT = HttpClient.newHttpClient();

        @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException{
            String city = req.getParameter("city");
            if(city==null||city.isBlank()){resp.sendError(400,"city param missing");return;}
            Weather w = fetchWeather(city.trim());
            resp.setContentType("application/json");
            resp.getWriter().write(MAPPER.writeValueAsString(w));
        }

        /* hit Open-Meteo geocoding then forecast */
        private Weather fetchWeather(String city) throws IOException{
            try {
                // 1) geocode
                String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                + URLEncoder.encode(city, StandardCharsets.UTF_8)
                + "&language=en";
                JsonNode geo = getJson(geoUrl);
                JsonNode loc = geo.path("results").get(0);
                double lat = loc.path("latitude").asDouble();
                double lon = loc.path("longitude").asDouble();

                // 2) forecast
                String api = "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&current_weather=true";
                JsonNode cur = getJson(String.format(api,lat,lon)).path("current_weather");
                double temp = cur.path("temperature").asDouble();
                double wind = cur.path("windspeed").asDouble();

                String desc = "Wind "+wind+" km/h";
                return new Weather(LocalDate.now(), loc.path("name").asText(), desc, temp);

            }catch(Exception e){
                throw new IOException("Cannot fetch weather for "+city,e);
            }
        }

        private JsonNode getJson(String url) throws IOException, InterruptedException{
            HttpResponse<String> r = CLIENT.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return MAPPER.readTree(r.body());
        }
    }

    /* DTO */
    public record Weather(LocalDate date,String city,String description,double temperatureC){}
}
