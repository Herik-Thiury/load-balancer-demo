package com.example.loadbalancer;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<String> home() {
        String instanceId = getInstanceId();
        String cor = corDaInstancia(instanceId);
        String hostname = getHostname();
        String hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        String html = """
            <!DOCTYPE html>
            <html lang="pt-br">
            <head><meta charset="UTF-8"><title>Load Balancer Demo</title></head>
            <body style="background: %s; font-family: sans-serif; text-align: center; padding-top: 80px; margin: 0;">
              <h1 style="font-size: 64px; color: white;">Respondido por: %s</h1>
              <p style="font-size: 24px; color: white;">Hostname: %s</p>
              <p style="font-size: 24px; color: white;">Horario: %s</p>
            </body>
            </html>
            """.formatted(cor, instanceId, hostname, hora);

        return ResponseEntity.ok()
                .header("X-Served-By", instanceId)
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    private String getInstanceId() {
        String id = System.getenv("INSTANCE_ID");
        if (id == null || id.isBlank()) {
            return "app-local";
        }
        return id;
    }

    private String corDaInstancia(String instanceId) {
        return switch (instanceId) {
            case "app-01" -> "#185FA5"; 
            case "app-02" -> "#3B6D11"; 
            case "app-03" -> "#994700"; 
            default -> "#444441";       
        };
    }

    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "desconhecido";
        }
    }
}