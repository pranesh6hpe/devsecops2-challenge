package com.example;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class HelloServer extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html");

        String html = String.format("""
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
                        background: linear-gradient(135deg, #ff9a9e 0%%, #fad0c4 99%%, #fad0c4 100%%);
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
                    p {
                        font-size: 1.5em;
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
                        to { transform: translateY(-20px); }
                    }
                </style>
            </head>
            <body>
                <h1>Hello, World! 🌍</h1>
                <p>Your IP: %s</p>
                <div class="emoji">😎🎉🚀</div>
            </body>
            </html>
            """, req.getRemoteAddr());

        resp.getWriter().write(html);

        System.out.println("👋 Received funky request from: " + req.getRemoteAddr());
    }
}
