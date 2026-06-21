package pokersolver.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pokersolver.utils.JsonUtil;
import tools.jackson.databind.JsonNode;

/**
 * End-to-end test against a real server on a random port, using the shortdeck dictionary (7MB)
 * to keep startup fast.
 */
class SolveApiIntegrationTest {

    static ApiServer server;
    static HttpClient client;
    static String base;

    @BeforeAll
    static void start() {
        Path resources = Path.of(Objects.requireNonNull(
                System.getProperty("solver.testResources"), "solver.testResources system property not set"));
        server = new ApiServer(resources).start(0);
        client = HttpClient.newHttpClient();
        base = String.format("http://localhost:%d/api/v1", server.port());
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    static final String SOLVE_REQUEST = """
            {
              "game": "shortdeck",
              "board": "Kd,Jd,Td,7s,8s",
              "rangeIp": "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,KQ,KJ,KT,QJ,QT,JT,98,97,87,86,76",
              "rangeOop": "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,KQ,KJ,KT,QJ,QT,JT,98,97,87,86,76",
              "pot": 10,
              "effectiveStack": 95,
              "iterations": 20,
              "progressInterval": 5
            }
            """;

    @Test
    void solveLifecycle() throws Exception {
        HttpResponse<String> created = client.send(
                HttpRequest.newBuilder(URI.create(base + "/solves"))
                        .POST(HttpRequest.BodyPublishers.ofString(SOLVE_REQUEST))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(202);
        JsonNode job = JsonUtil.MAPPER.readTree(created.body());
        String id = job.get("id").asString();
        assertThat(job.get("state").asString()).isEqualTo("RUNNING");

        JsonNode finished = awaitTerminal(id, Duration.ofMinutes(2));
        assertThat(finished.get("state").asString()).isEqualTo("COMPLETED");
        assertThat(finished.get("events").size()).isGreaterThanOrEqualTo(2);
        assertThat(finished.get("events").get(0).get("type").asString()).isEqualTo("progress");

        HttpResponse<String> strategy = client.send(
                HttpRequest.newBuilder(URI.create(base + "/solves/" + id + "/strategy"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(strategy.statusCode()).isEqualTo(200);
        JsonNode root = JsonUtil.MAPPER.readTree(strategy.body());
        assertThat(root.get("node_type").asString()).isEqualTo("action_node");
        assertThat(root.get("strategy").has("strategy")).isTrue();
        assertThat(root.get("childActions").size()).isGreaterThan(0);

        // Lazy navigation: fetching a child edge returns just that node, not the whole subtree.
        String childLabel = root.get("childActions").get(0).asString();
        HttpResponse<String> childNode = client.send(
                HttpRequest.newBuilder(URI.create(base + "/solves/" + id + "/strategy?path="
                                + URLEncoder.encode(childLabel, StandardCharsets.UTF_8)))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(childNode.statusCode()).isEqualTo(200);
        assertThat(JsonUtil.MAPPER.readTree(childNode.body()).has("node_type")).isTrue();
    }

    @Test
    void sseStreamsProgressUntilTerminal() throws Exception {
        HttpResponse<String> created = client.send(
                HttpRequest.newBuilder(URI.create(base + "/solves"))
                        .POST(HttpRequest.BodyPublishers.ofString(SOLVE_REQUEST))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        String id = JsonUtil.MAPPER.readTree(created.body()).get("id").asString();

        HttpResponse<Stream<String>> sse = client.send(
                HttpRequest.newBuilder(URI.create(base + "/solves/" + id + "/events"))
                        .timeout(Duration.ofMinutes(2))
                        // Javalin only engages its SSE handler when the client accepts event-streams
                        .header("Accept", "text/event-stream")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofLines());
        assertThat(sse.statusCode()).isEqualTo(200);
        // The stream ends when the server closes the connection after the terminal event.
        var eventNames = sse.body()
                .filter(line -> line.startsWith("event:"))
                .map(line -> line.substring("event:".length()).trim())
                .toList();
        assertThat(eventNames).contains("progress");
        assertThat(eventNames.getLast()).isIn("completed", "failed", "cancelled");
        assertThat(eventNames.getLast()).isEqualTo("completed");
    }

    @Test
    void validationAndNotFound() throws Exception {
        HttpResponse<String> bad = client.send(
                HttpRequest.newBuilder(URI.create(base + "/solves"))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"board\":\"Kd,Jd,Td\"}"))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(bad.statusCode()).isEqualTo(400);
        assertThat(bad.body()).contains("rangeIp");

        HttpResponse<String> missing = client.send(
                HttpRequest.newBuilder(URI.create(base + "/solves/no-such-job"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(missing.statusCode()).isEqualTo(404);
    }

    private JsonNode awaitTerminal(String id, Duration timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (true) {
            HttpResponse<String> status = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/solves/" + id))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode job = JsonUtil.MAPPER.readTree(status.body());
            String state = job.get("state").asString();
            if (!"RUNNING".equals(state)) return job;
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("job did not finish in time: " + job);
            }
            Thread.sleep(200);
        }
    }
}
