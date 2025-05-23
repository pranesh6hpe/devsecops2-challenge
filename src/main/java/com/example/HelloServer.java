package com.example;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class HelloServer {
    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);
        ServletContextHandler handler = new ServletContextHandler();
        handler.addServlet(HelloServlet.class, "/");
        server.setHandler(handler);
        System.out.println("Server started on http://localhost:8080");
        server.start();
        server.join();
    }

    public static class HelloServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/html");

            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>Funky Hello</title>
                        <style>
                            body {
                                margin: 0;
                                padding: 0;
                                display: flex;
                                justify-content: center;
                                align-items: center;
                                height: 100vh;
                                background: linear-gradient(135deg, #f6d365 0%, #fda085 100%);
                                font-family: 'Comic Sans MS', cursive, sans-serif;
                                animation: backgroundShift 10s infinite alternate;
                            }

                            @keyframes backgroundShift {
                                0% { background-position: 0% 50%; }
                                100% { background-position: 100% 50%; }
                            }

                            .box {
                                padding: 30px 50px;
                                background: white;
                                border-radius: 15px;
                                box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
                                text-align: center;
                                animation: float 3s ease-in-out infinite;
                            }

                            @keyframes float {
                                0% { transform: translateY(0); }
                                50% { transform: translateY(-10px); }
                                100% { transform: translateY(0); }
                            }

                            h1 {
                                font-size: 2.5em;
                                color: #ff4081;
                            }

                            p {
                                color: #333;
                            }
                        </style>
                    </head>
                    <body>
                        <div class="box">
                            <h1>🌈 Hello, World! 🎉</h1>
                            <p>Welcome to the Funky Java Servlet UI 🚀</p>
                        </div>
                    </body>
                    </html>
                    """;

            resp.getWriter().write(html);
            System.out.println("Funky request received from: " + req.getRemoteAddr());
        }
    }
}
