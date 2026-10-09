package pokersolver.api;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pokersolver.Card;
import pokersolver.GameTree;
import pokersolver.SolverEnvironment;
import pokersolver.nodes.GameRound;
import pokersolver.ranges.PrivateCards;
import pokersolver.solver.Algorithm;
import pokersolver.solver.GameTreeBuildingSettings;
import pokersolver.solver.MonteCarloAlg;
import pokersolver.solver.ParallelCfrSolver;
import pokersolver.solver.SolverConfig;
import pokersolver.utils.PrivateRangeConverter;

/** Creates and tracks solve jobs; each job trains on its own virtual thread. */
public final class SolveService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SolveService.class);

    private static final float SMALL_BLIND = 0.5f;
    private static final float BIG_BLIND = 1.0f;

    private final ConcurrentHashMap<String, SolveJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SolveJob create(SolveRequest request) {
        PreparedScenario scenario = validate(request);
        SolveJob job = new SolveJob(UUID.randomUUID().toString());
        jobs.put(job.id(), job);
        executor.execute(() -> run(job, request, scenario));
        return job;
    }

    public @Nullable SolveJob get(String id) {
        return jobs.get(id);
    }

    private record PreparedScenario(int[] board, PrivateCards[] ip, PrivateCards[] oop) {}

    private static PreparedScenario validate(SolveRequest request) {
        require(request.board() != null && !request.board().isBlank(), "board is required");
        require(request.rangeIp() != null && !request.rangeIp().isBlank(), "rangeIp is required");
        require(request.rangeOop() != null && !request.rangeOop().isBlank(), "rangeOop is required");
        require(
                request.pot() != null && Float.isFinite(request.pot()) && request.pot() > 0,
                "pot must be finite and > 0");
        require(
                request.effectiveStack() != null
                        && Float.isFinite(request.effectiveStack())
                        && request.effectiveStack() > 0,
                "effectiveStack must be finite and > 0");
        int[] board = Arrays.stream(Objects.requireNonNull(request.board()).split(",", -1))
                .map(String::trim)
                .mapToInt(Card::strCard2int)
                .toArray();
        require(board.length >= 3 && board.length <= 5, "board must have 3 (flop), 4 (turn) or 5 (river) cards");
        long boardMask = Card.boardInts2long(board);
        require(Card.cardCount(boardMask) == board.length, "board cards must be distinct");
        PrivateCards[] ip = PrivateRangeConverter.rangeStr2Cards(Objects.requireNonNull(request.rangeIp()), board);
        PrivateCards[] oop = PrivateRangeConverter.rangeStr2Cards(Objects.requireNonNull(request.rangeOop()), board);
        require(hasCompatiblePair(ip, oop), "ranges have no compatible positive-weight hand pair on the board");
        if (request.iterations() != null) require(request.iterations() > 0, "iterations must be > 0");
        if (request.progressInterval() != null) require(request.progressInterval() > 0, "progressInterval must be > 0");
        if (request.algorithm() != null) Algorithm.fromId(request.algorithm());
        if (request.monteCarlo() != null) MonteCarloAlg.fromId(request.monteCarlo());
        if (request.raiseLimit() != null) require(request.raiseLimit() >= 0, "raiseLimit must be >= 0");
        if (request.threads() != null)
            require(
                    request.threads() == -1 || (request.threads() > 0 && request.threads() <= 32767),
                    "threads must be -1 or in [1, 32767]");
        if (request.stopExploitability() != null)
            require(
                    Double.isFinite(request.stopExploitability()) && request.stopExploitability() >= 0,
                    "stopExploitability must be finite and >= 0");
        for (SolveRequest.StreetSpec spec : new SolveRequest.StreetSpec[] {
            request.flop(),
            request.turn(),
            request.river(),
            request.flopIp(),
            request.turnIp(),
            request.riverIp(),
            request.flopOop(),
            request.turnOop(),
            request.riverOop()
        }) {
            if (spec == null) continue;
            validateSizes(spec.betSizes());
            validateSizes(spec.raiseSizes());
            validateSizes(spec.donkSizes());
        }
        return new PreparedScenario(board, ip, oop);
    }

    private static boolean hasCompatiblePair(PrivateCards[] ip, PrivateCards[] oop) {
        for (PrivateCards first : ip)
            for (PrivateCards second : oop) if (!Card.boardsHasIntercept(first.mask(), second.mask())) return true;
        return false;
    }

    private static void validateSizes(float @Nullable [] sizes) {
        if (sizes != null)
            for (float size : sizes) require(Float.isFinite(size) && size > 0, "bet sizes must be finite and > 0");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private void run(SolveJob job, SolveRequest request, PreparedScenario scenario) {
        try {
            int[] board = scenario.board();
            GameRound round =
                    switch (board.length) {
                        case 3 -> GameRound.FLOP;
                        case 4 -> GameRound.TURN;
                        default -> GameRound.RIVER;
                    };

            PrivateCards[] rangeIp = scenario.ip();
            PrivateCards[] rangeOop = scenario.oop();

            float pot = Objects.requireNonNull(request.pot());
            float effectiveStack = Objects.requireNonNull(request.effectiveStack());
            GameTree tree = SolverEnvironment.gameTreeFromParams(
                    pot / 2,
                    pot / 2,
                    round.number(),
                    Objects.requireNonNullElse(request.raiseLimit(), 5),
                    SMALL_BLIND,
                    BIG_BLIND,
                    effectiveStack + pot / 2,
                    buildSettings(request));

            SolverConfig config = SolverConfig.builder()
                    .tree(tree)
                    .range1(rangeIp)
                    .range2(rangeOop)
                    .initialBoard(board)
                    .iterationNumber(Objects.requireNonNullElse(request.iterations(), 100))
                    .printInterval(Objects.requireNonNullElse(request.progressInterval(), 10))
                    .algorithm(Algorithm.fromId(
                            Objects.requireNonNullElse(request.algorithm(), Algorithm.DISCOUNTED_CFR.id())))
                    .monteCarloAlg(MonteCarloAlg.fromId(Objects.requireNonNullElse(request.monteCarlo(), "none")))
                    .stopExploitability(Objects.requireNonNullElse(request.stopExploitability(), 0.0))
                    .progressListener((iteration, exploitability, elapsedMs) ->
                            job.publish(ProgressEvent.progress(iteration, exploitability, elapsedMs)))
                    .build();

            ParallelCfrSolver solver =
                    new ParallelCfrSolver(config, Objects.requireNonNullElse(request.threads(), -1), 1.0, 0.0, 1, 0);
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
