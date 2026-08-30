package com.cartracker.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Loads a gitignored .env file (KEY=VALUE) into the Spring environment at the
 * highest precedence, so `java -jar` works with zero manual `export`/`set`.
 * <p>
 * Without this, Spring only reads OS env vars + system properties; it does NOT
 * read .env. The native Postgres on host :5432 would then be hit instead of the
 * Docker container on :15432, causing "password authentication failed".
 * <p>
 * .env location: DOTENV_PATH override, else $user.dir/.env, else classpath:.env.
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment env,
                                     SpringApplication application) {
    Path dotEnv = resolveDotEnv();
    if (dotEnv == null || !Files.exists(dotEnv)) {
      return;
    }
    Map<String, Object> props = parse(dotEnv);
    if (!props.isEmpty()) {
      env.getPropertySources().addFirst(new MapPropertySource(
              "dotEnv",
              props
          )
      );
    }
  }

  private Path resolveDotEnv() {
    String override = System.getenv("DOTENV_PATH");
    if (override != null && !override.isBlank()) {
      return Path.of(override);
    }
    String userDir = System.getProperty("user.dir");
    if (userDir != null) {
      return Path.of(
          userDir,
          ".env"
      );
    }
    return null;
  }

  private Map<String, Object> parse(Path dotEnv) {
    Map<String, Object> props = new LinkedHashMap<>();
    try {
      List<String> lines = Files.readAllLines(dotEnv);
      for (String raw : lines) {
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        int eq = line.indexOf('=');
        if (eq < 0) {
          continue;
        }
        String key = line.substring(
            0,
            eq
        ).trim();
        String value = stripQuotes(line.substring(eq + 1).trim());
        if (!key.isEmpty()) {
          props.put(
              key,
              value
          );
        }
      }
    } catch (IOException ex) {
      return Map.of();
    }
    return props;
  }

  private String stripQuotes(String s) {
    if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\""))
        || (s.startsWith("'") && s.endsWith("'"))) {
      return s.substring(
          1,
          s.length() - 1
      );
    }
    return s;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
