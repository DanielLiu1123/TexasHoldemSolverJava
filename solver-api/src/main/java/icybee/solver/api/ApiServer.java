package icybee.solver.api;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The embedded HTTP API around the solver core (see ADR 0001).
 *
 * <pre>
 * POST   /api/v1/solves              submit a solve job        → 202 JobView
 * GET    /api/v1/solves/{id}         job status and progress   → 200 JobView
 * GET    /api/v1/solves/{id}/events  live progress (SSE)
 * GET    /api/v1/solves/{id}/strategy  strategy JSON once completed
 * DELETE /api/v1/solves/{id}         request cancellation      → 202 JobView
 * GET    /api/v1/health              process / dictionary status
 * </pre>
 */
public final class ApiServer implements AutoCloseable {

    private final SolveService service;
    private final Javalin app;

    public ApiServer(Path resourcesDir) {
        this.service = new SolveService(new GameResources(resourcesDir));
        // Javalin 7: all routes, handlers and lifecycle configuration go into the create block.
        this.app = Javalin.create(config -> {
            config.startup.showJavalinBanner = false;
            config.concurrency.useVirtualThreads = true;
            config.jsonMapper(new JacksonJsonMapper());
            // The web UI is embedded under /web when built (:web-ui:viteBuild);
            // tests and API-only builds run without it.
            if (ApiServer.class.getResource("/web/index.html") != null) {
                config.staticFiles.add("/web", io.javalin.http.staticfiles.Location.CLASSPATH);
                config.spaRoot.addFile("/", "/web/index.html", io.javalin.http.staticfiles.Location.CLASSPATH);
            }
            config.routes.exception(IllegalArgumentException.class, (e, ctx) -> ctx.status(HttpStatus.BAD_REQUEST)
                    .json(Map.of("error", String.valueOf(e.getMessage()))));
            config.routes.exception(tools.jackson.core.JacksonException.class, (e, ctx) -> ctx.status(
                            HttpStatus.BAD_REQUEST)
                    .json(Map.of("error", String.format("malformed request body: %s", e.getOriginalMessage()))));
            config.routes.post("/api/v1/solves", ctx -> {
                SolveRequest request = ctx.bodyAsClass(SolveRequest.class);
                SolveJob job = service.create(request);
                ctx.status(HttpStatus.ACCEPTED).json(JobView.of(job));
            });
            config.routes.get("/api/v1/solves/{id}", ctx -> ctx.json(JobView.of(job(ctx.pathParam("id")))));
            config.routes.delete("/api/v1/solves/{id}", ctx -> {
                SolveJob job = job(ctx.pathParam("id"));
                job.requestCancel();
                ctx.status(HttpStatus.ACCEPTED).json(JobView.of(job));
            });
            config.routes.get("/api/v1/solves/{id}/strategy", ctx -> {
                SolveJob job = job(ctx.pathParam("id"));
                String strategy = job.strategyJson();
                if (strategy == null) {
                    ctx.status(HttpStatus.CONFLICT)
                            .json(Map.of("error", String.format("job is %s; no strategy available", job.state())));
                    return;
                }
                ctx.contentType("application/json").result(strategy);
            });
            config.routes.get(
                    "/api/v1/health",
                    ctx -> ctx.json(Map.of(
                            "status",
                            "ok", //
                            "loadedGames",
                            service.resources().loadedGames())));
            config.routes.sse("/api/v1/solves/{id}/events", client -> {
                SolveJob job = job(client.ctx().pathParam("id"));
                client.keepAlive();
                Consumer<ProgressEvent> subscriber = event -> {
                    client.sendEvent(event.type(), event);
                    if (event.isTerminal()) client.close();
                };
                client.onClose(() -> job.unsubscribe(subscriber));
                job.subscribe(subscriber);
            });
        });
    }

    private SolveJob job(String id) {
        SolveJob job = service.get(id);
        if (job == null) throw new NotFoundResponse(String.format("job not found: %s", id));
        return job;
    }

    /** Starts the server; port 0 picks a free port (see {@link #port()}). */
    public ApiServer start(int port) {
        app.start(port);
        return this;
    }

    public int port() {
        return app.port();
    }

    @Override
    public void close() {
        app.stop();
        service.close();
    }

    public static void main(String[] args) {
        int port = 8080;
        Path resources = Path.of(".");
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[i + 1]);
                case "--resources" -> resources = Path.of(args[i + 1]);
                default -> {
                    // positional/unknown tokens are ignored; flags are self-describing
                }
            }
        }
        ApiServer server = new ApiServer(resources).start(port);
        System.out.println(String.format("solver-api listening on http://localhost:%d", server.port()));
    }
}
