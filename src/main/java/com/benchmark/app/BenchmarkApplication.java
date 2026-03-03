package com.benchmark.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class BenchmarkApplication {

    public static void main(String[] args) {
        loadEnvFile();
        SpringApplication.run(BenchmarkApplication.class, args);
    }

    /**
     * Carrega variáveis do .env (raiz do projeto) em System.getProperties()
     * para que placeholders em application.properties resolvam ao rodar com mvn spring-boot:run.
     */
    private static void loadEnvFile() {
        Path env = Paths.get(System.getProperty("user.dir", ".")).resolve(".env");
        if (!Files.isRegularFile(env)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(env)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int eq = line.indexOf('=');
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!key.isEmpty()) {
                    System.setProperty(key, value);
                }
            }
        } catch (Exception ignored) {
            // .env opcional; segue com defaults
        }
    }
}
