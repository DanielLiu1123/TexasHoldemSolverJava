package pokersolver.api;

import pokersolver.Card;
import pokersolver.GameTree;
import pokersolver.SolverEnvironment;
import pokersolver.ranges.PrivateCards;
import pokersolver.solver.Algorithm;
import pokersolver.solver.GameTreeBuildingSettings;
import pokersolver.solver.MonteCarloAlg;
import pokersolver.solver.ParallelCfrPlusSolver;
import pokersolver.solver.SolverConfig;
import pokersolver.utils.PrivateRangeConverter;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates and tracks solve jobs; each job trains on its own virtual thread. */
public final class SolveService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SolveService.class);

    private static final float SMALL_BLIND = 0.5f;
    private static final float BIG_BLIND = 1.0f;
    private static final int ROUND_FLOP = 2;
    private static final int ROUND_TURN = 3;
    private static final int ROUND_RIVER = 4;

    private final GameResources resources;
    private final ConcurrentHashMap<String, SolveJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SolveService(GameResources resources) {
        this.resources = resources;
    }

    public GameResources resources() {
        return resources;
    }

    public SolveJob create(SolveRequest request) {
        validate(request);
        SolveJob job = new SolveJob(UUID.randomUUID().toString());
        jobs.put(job.id(), job);
        executor.execute(() -> run(job, request));
        return job;
    }

    public @Nullable SolveJob get(String id) {
        return jobs.get(id);
    }

    private static void validate(SolveRequest request) {
        require(request.board() != null && !request.board().isBlank(), "board is required");
        require(request.rangeIp() != null && !request.rangeIp().isBlank(), "rangeIp is required");
        require(request.rangeOop() != null && !request.rangeOop().isBlank(), "rangeOop is required");
        require(request.pot() != null && request.pot() > 0, "pot must be > 0");
        require(request.effectiveStack() != null && request.effectiveStack() > 0, "effectiveStack must be > 0");
        int boardSize = Objects.requireNonNull(request.board()).split(",").length;
        require(boardSize >= 3 && boardSize <= 5, "board must have 3 (flop), 4 (turn) or 5 (river) cards");
        if (request.iterations() != null) require(request.iterations() > 0, "iterations must be > 0");
        if (request.progressInterval() != null) require(request.progressInterval() > 0, "progressInterval must be > 0");
        if (request.algorithm() != null) Algorithm.fromId(request.algorithm());
        if (request.monteCarlo() != null) MonteCarloAlg.fromId(request.monteCarlo());
        if (request.game() != null) GameType.fromId(request.game());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private void run(SolveJob job, SolveRequest request) {
        try {
            GameType game = GameType.fromId(Objects.requireNonNullElse(request.game(), GameType.HOLDEM.id()));
            GameResources.Loaded loaded = resources.get(game);

            int[] board = Arrays.stream(Objects.requireNonNull(request.board()).split(","))
                    .map(String::trim)
                    .mapToInt(Card::strCard2int)
                    .toArray();
            int round =
                    switch (board.length) {
                        case 3 -> ROUND_FLOP;
                        case 4 -> ROUND_TURN;
                        default -> ROUND_RIVER;
                    };

            PrivateCards[] rangeIp =
                    PrivateRangeConverter.rangeStr2Cards(Objects.requireNonNull(request.rangeIp()), board);
            PrivateCards[] rangeOop =
                    PrivateRangeConverter.rangeStr2Cards(Objects.requireNonNull(request.rangeOop()), board);

            float pot = Objects.requireNonNull(request.pot());
            float effectiveStack = Objects.requireNonNull(request.effectiveStack());
            GameTree tree = SolverEnvironment.gameTreeFromParams(
                    loaded.deck(),
                    pot / 2,
                    pot / 2,
                    round,
                    Objects.requireNonNullElse(request.raiseLimit(), 5),
                    SMALL_BLIND,
                    BIG_BLIND,
                    effectiveStack + pot / 2,
                    buildSettings(request));

            int iterations = Objects.requireNonNullElse(request.iterations(), 100);
            SolverConfig config = SolverConfig.builder()
                    .tree(tree)
                    .range1(rangeIp)
                    .range2(rangeOop)
                    .initialBoard(board)
                    .compairer(loaded.compairer())
                    .deck(loaded.deck())
                    .iterationNumber(iterations)
                    .printInterval(Objects.requireNonNullElse(request.progressInterval(), 10))
                    .algorithm(Algorithm.fromId(
                            Objects.requireNonNullElse(request.algorithm(), Algorithm.DISCOUNTED_CFR.id())))
                    .monteCarloAlg(MonteCarloAlg.fromId(Objects.requireNonNullElse(request.monteCarlo(), "none")))
                    .stopExploitability(Objects.requireNonNullElse(request.stopExploitability(), 0.0))
                    .progressListener((iteration, exploitability, elapsedMs) ->
                            job.publish(ProgressEvent.progress(iteration, exploitability, elapsedMs)))
                    .build();

            ParallelCfrPlusSolver solver = new ParallelCfrPlusSolver(
                    config, Objects.requireNonNullElse(request.threads(), -1), 1.0, 0.0, 1, 0);
            job.attachSolver(solver);
            solver.train();

            job.complete();
        } catch (Throwable e) {
            // Throwable, not Exception: tree building uses `assert`, and an escaping
            // AssertionError must not leave the job RUNNING forever.
            log.error("solve job {} failed", job.id(), e);
            job.fail(Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    private static GameTreeBuildingSettings buildSettings(SolveRequest request) {
        return new GameTreeBuildingSettings(
                streetSetting(firstNonNull(request.flopIp(), request.flop()), false),
                streetSetting(firstNonNull(request.turnIp(), request.turn()), true),
                streetSetting(firstNonNull(request.riverIp(), request.river()), true),
                streetSetting(firstNonNull(request.flopOop(), request.flop()), false),
                streetSetting(firstNonNull(request.turnOop(), request.turn()), true),
                streetSetting(firstNonNull(request.riverOop(), request.river()), true));
    }

    private static SolveRequest.@Nullable StreetSpec firstNonNull(
            SolveRequest.@Nullable StreetSpec override, SolveRequest.@Nullable StreetSpec shared) {
        return override != null ? override : shared;
    }

    private static GameTreeBuildingSettings.StreetSetting streetSetting(
            SolveRequest.@Nullable StreetSpec spec, boolean defaultAllin) {
        float[] defaultSizes = {50.0f};
        if (spec == null) {
            return new GameTreeBuildingSettings.StreetSetting(defaultSizes, defaultSizes, null, defaultAllin);
        }
        return new GameTreeBuildingSettings.StreetSetting(
                Objects.requireNonNullElse(spec.betSizes(), defaultSizes),
                Objects.requireNonNullElse(spec.raiseSizes(), defaultSizes),
                spec.donkSizes(),
                Objects.requireNonNullElse(spec.allin(), defaultAllin));
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
