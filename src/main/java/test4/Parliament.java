package test4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import game.LegalMoveLibrary;
import game.VectorMoveValidator;

/**
 * The Parliament — the per-colour meta map, rebuilt as an adjudicator.
 *
 * Each of the five base maps is a witness that sees the same moves through its
 * own distance metric. For the side to play, every witness dreams up its
 * favourite move. The parliament holds those dreams to a vote: each legal move
 * on the board gathers support from the witnesses whose dream sits near it,
 * weighted by that witness's standing. Where the witnesses agree, support piles
 * up and that move wins; where they argue, the pick loosens and a touch of noise
 * lets it explore. The winning choice is always snapped to a real legal move.
 *
 * Standing is equal for every witness right now — a straight democracy. When the
 * adversarial phase arrives, the standings simply start drifting by reward, and
 * the same machine becomes a competition. Cooperative-vs-winner is the
 * {@link #temperature} dial. Three philosophies, one parliament.
 */
public final class Parliament {

    private final Random rng = new Random();

    /** One standing per witness (base map). Equal == democracy. */
    private final double[] standings;

    // --- dials (brick five will expose these in the control panel) ---
    /** Base exploration noise applied to the chosen move, scaled by disagreement. */
    public volatile double noise = 0.03;
    /** Softness of the vote. Higher == more willing to pick a non-winner. */
    public volatile double temperature = 0.6;

    public Parliament(int numWitnesses) {
        this.standings = new double[numWitnesses];
        Arrays.fill(this.standings, 1.0); // democracy: everyone counts the same
    }

    /**
     * Decide a legal move for the side to play, or null if there are none
     * (checkmate / stalemate). Reads the live, synced board via LegalMoveLibrary.
     */
    public double[] decide(List<BaseSOM> witnesses, boolean isWhite, int width, int height) {
        List<double[]> legal = LegalMoveLibrary.getAllLegalMovesSynced(isWhite);
        if (legal == null || legal.isEmpty()) return null;

        // 1) Every witness dreams its favourite move.
        int n = witnesses.size();
        double[][] dreams = new double[n][];
        for (int m = 0; m < n; m++) {
            dreams[m] = dreamFrom(witnesses.get(m), width, height);
        }

        // 2) Vote: each legal move gathers standing-weighted support from the
        //    witnesses whose dream lands near it. Agreement accumulates.
        double[] support = new double[legal.size()];
        for (int li = 0; li < legal.size(); li++) {
            double[] mv = legal.get(li);
            double s = 0.0;
            for (int m = 0; m < n; m++) {
                if (dreams[m] == null) continue;
                double w = (m < standings.length) ? standings[m] : 1.0;
                s += w / (1.0 + moveDist(mv, dreams[m]));   // closeness, weighted
            }
            support[li] = s;
        }

        // 3) Disagreement -> exploration. The more the dreams scatter, the looser
        //    the pick and the louder the noise.
        double disagreement = spread(dreams);
        double temp = temperature * (0.5 + disagreement);

        // 4) Soft pick over support (never a strict argmax).
        int chosen = softmaxSample(support, temp);
        double[] move = Arrays.copyOf(legal.get(chosen), 4);

        // 5) A touch of noise, scaled by disagreement, then re-snap to the nearest
        //    legal move so noise can only ever land us on another *real* move.
        if (noise > 0.0) {
            double amp = noise * (0.5 + disagreement);
            for (int k = 0; k < 4; k++) move[k] += (rng.nextDouble() - 0.5) * 2.0 * amp;
            move = nearestLegal(move, legal);
        }
        return move;
    }

    /** A witness's dream: the node-move it rates highest by reward. */
    private double[] dreamFrom(BaseSOM som, int width, int height) {
        double[][][] w = som.getWeights();              // [W][H][dim]
        int bw = w.length;
        int bh = (bw > 0) ? w[0].length : 0;
        double best = Double.NEGATIVE_INFINITY;
        double[] bestMove = null;
        for (int i = 0; i < bw; i++) {
            for (int j = 0; j < bh; j++) {
                double[] node = w[i][j];
                double[] mv = { clamp01(node[0]), clamp01(node[1]), clamp01(node[2]), clamp01(node[3]) };
                double r = VectorMoveValidator.evaluateMove(mv, width, height, false);
                if (r > best) { best = r; bestMove = mv; }
            }
        }
        return bestMove;
    }

    private static double moveDist(double[] a, double[] b) {
        double s = 0.0;
        for (int k = 0; k < 4; k++) { double d = a[k] - b[k]; s += d * d; }
        return Math.sqrt(s);
    }

    private static double[] nearestLegal(double[] m, List<double[]> legal) {
        double bd = Double.MAX_VALUE;
        double[] best = legal.get(0);
        for (double[] mv : legal) {
            double d = moveDist(m, mv);
            if (d < bd) { bd = d; best = mv; }
        }
        return Arrays.copyOf(best, 4);
    }

    /** Mean pairwise distance among the dreams, squashed toward [0,1]. */
    private static double spread(double[][] dreams) {
        List<double[]> ds = new ArrayList<>();
        for (double[] d : dreams) if (d != null) ds.add(d);
        if (ds.size() < 2) return 0.0;
        double sum = 0.0; int c = 0;
        for (int i = 0; i < ds.size(); i++)
            for (int j = i + 1; j < ds.size(); j++) { sum += moveDist(ds.get(i), ds.get(j)); c++; }
        return Math.min(1.0, (sum / c) / 2.0);   // 4 dims in [0,1] -> max dist ~2
    }

    private int softmaxSample(double[] support, double temp) {
        if (temp < 1e-6) {
            int best = 0;
            for (int i = 1; i < support.length; i++) if (support[i] > support[best]) best = i;
            return best;
        }
        double max = Double.NEGATIVE_INFINITY;
        for (double v : support) max = Math.max(max, v);
        double[] p = new double[support.length];
        double sum = 0.0;
        for (int i = 0; i < support.length; i++) { p[i] = Math.exp((support[i] - max) / temp); sum += p[i]; }
        double r = rng.nextDouble() * sum, acc = 0.0;
        for (int i = 0; i < support.length; i++) { acc += p[i]; if (r <= acc) return i; }
        return support.length - 1;
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    // --- knobs / standings (the adversarial phase will drive these) ---
    public double[] getStandings() { return standings; }
    public void setNoise(double n) { this.noise = n; }
    public void setTemperature(double t) { this.temperature = t; }
}
