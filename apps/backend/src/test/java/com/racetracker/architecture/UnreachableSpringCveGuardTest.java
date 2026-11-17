package com.racetracker.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CVE-2026-47884 (XsltView) and CVE-2026-47890 (Server-Sent Events with views) are accepted in
 * .trivyignore because the backend uses neither feature. This test fails as soon as either is
 * introduced, so the exception is revisited instead of silently becoming exploitable.
 */
class UnreachableSpringCveGuardTest {

    private static final List<String> FORBIDDEN = List.of(
        "XsltView",
        "XsltViewResolver",
        "SseEmitter",
        "ServerSentEvent",
        "ResponseBodyEmitter",
        "TEXT_EVENT_STREAM",
        "text/event-stream"
    );

    @Test
    void backendDoesNotUseXsltViewsOrServerSentEvents() throws IOException {
        Path sources = Path.of("src/main/java");
        assertThat(sources).isDirectory();
        try (Stream<Path> files = Files.walk(sources)) {
            List<String> offenders = files
                .filter(path -> path.toString().endsWith(".java"))
                .flatMap(path -> {
                    String content = read(path);
                    return FORBIDDEN.stream().filter(content::contains).map(token -> path + " uses " + token);
                })
                .toList();
            assertThat(offenders)
                .as("Remove the Spring CVE exceptions from .trivyignore only after upgrading to Spring Framework 7")
                .isEmpty();
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
