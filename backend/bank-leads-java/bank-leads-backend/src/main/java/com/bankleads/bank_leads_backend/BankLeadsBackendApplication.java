package com.bankleads.bank_leads_backend;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BankLeadsBackendApplication {

	public static void main(String[] args) {
		// Load environment variables from .env file if present.
		// (We intentionally avoid adding an external dotenv dependency; this keeps the build working.)
		loadEnvFileIfPresent();
		
		SpringApplication.run(BankLeadsBackendApplication.class, args);
	}

	private static void loadEnvFileIfPresent() {
		// Common locations during dev:
		// - project root: `.env`
		// - backend module: `backend/bank-leads-java/bank-leads-backend/.env`
		List<Path> candidates = List.of(
				Paths.get(".env"),
				Paths.get("backend/bank-leads-java/bank-leads-backend/.env")
		);

		for (Path candidate : candidates) {
			if (!Files.exists(candidate)) continue;

			try (BufferedReader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
				reader.lines().forEach(line -> {
					String trimmed = line.trim();
					if (trimmed.isEmpty() || trimmed.startsWith("#")) return;

					int idx = trimmed.indexOf('=');
					if (idx <= 0) return;

					String key = trimmed.substring(0, idx).trim();
					String value = trimmed.substring(idx + 1).trim();

					// Strip optional surrounding quotes: KEY="value" / KEY='value'
					if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
						value = value.substring(1, value.length() - 1);
					}

					if (!key.isEmpty()) {
						System.setProperty(key, value);
					}
				});
			} catch (IOException ignored) {
				// Ignore missing/unreadable env files (same behavior as ignoreIfMissing).
			}

			// Stop after the first env file found.
			return;
		}
	}
}
