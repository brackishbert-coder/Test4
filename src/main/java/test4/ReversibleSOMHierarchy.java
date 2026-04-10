package test4;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;

// Main system class that coordinates the entire hierarchy with reward feedback
public class ReversibleSOMHierarchy {

    private  List<BaseSOM> baseSOMList;
    private  MetaSOM metaSOM;
    private  List<DistanceMetric> metrics;
    private final Random random;
    private double learningRate;
    private boolean continuousLearning;
 // ADD THIS at the top with your other fields
    private final List<VisualSOMNode> visualNodes = new ArrayList<>();

    // Configuration constants
    public  final int BASE_SOM_SIZE = 16;
    public  final int INPUT_DIMENSION = 256;
    public  final int META_SOM_SIZE = 10;
    public  final int REPRESENTATION_DIMENSION = 4;
	private String uniquename;
	/** 
     * Executor used to process inputs without blocking the caller.
     * Single-threaded to preserve update order; bump the pool size
     * if you want more parallelism across SOMs.
     */
	private final ExecutorService asyncExecutor =
	        Executors.newFixedThreadPool(
	            Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
	        );
	private String path;
    
    public ReversibleSOMHierarchy(String path,String uniquename) {
        this.path = path;
		this.uniquename = uniquename;
		this.baseSOMList = new ArrayList<>();
        this.metaSOM = new MetaSOM(META_SOM_SIZE, META_SOM_SIZE, REPRESENTATION_DIMENSION, true,uniquename);
        this.metrics = createDistanceMetrics();
        this.random = new Random();
        this.learningRate = 0.1;
        this.continuousLearning = true;
        initializeBaseSOM();
    }

    private List<DistanceMetric> createDistanceMetrics() {
        List<DistanceMetric> metrics = new ArrayList<>();
        metrics.add(new EuclideanDistance());
        metrics.add(new ManhattanDistance());
        metrics.add(new CosineDistance());
        metrics.add(new ChebyshevDistance());
        metrics.add(new MinkowskiDistance(3.0));
        return metrics;
    }

    private void initializeBaseSOM() {
        baseSOMList.clear();
        for (DistanceMetric metric : metrics) {
            BaseSOM som = new BaseSOM(BASE_SOM_SIZE, BASE_SOM_SIZE, INPUT_DIMENSION, metric);
            metaSOM.registerBaseSOM(som);
            baseSOMList.add(som);
        }
    }

    public void clearVisualization() {
        visualNodes.clear();
    }

    public void buildVisualizationFromLoaded() {
        for (BaseSOM som : baseSOMList) {
            double[] rep = som.getRepresentation();
            int[] pos = metaSOM.getBaseSOMPosition(som);
            if (pos == null) continue;

            visualNodes.add(new VisualSOMNode(
                new Point3D(pos[0], pos[1], 0),
                Color.WHITE,
                som.getMetricName(),
                0,
                rep
            ));
        }
    }

    /**
     * Non-blocking version of processInput.
     * Queues the work on asyncExecutor and returns immediately.
     */
    public void processInputAsync(double[] input, double reward) {
        // defensive copy so caller can reuse / mutate its array safely
        double[] copy = Arrays.copyOf(input, input.length);
        asyncExecutor.submit(() -> {
            try {
                processInput(copy, reward);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    
    /** Standard processing (no reward feedback). */
    public List<BMUResult> processInput(double[] input) {
        return processInput(input, 1.0);
    }

    /** New version: includes feedback reward (-1 to +1). */
    public List<BMUResult> processInput(double[] input, double reward) {
        

        ArrayList<BMUResult> bmuResults = new ArrayList<>();

        // --- Update all Base SOMs with reward ---
        for (BaseSOM som : baseSOMList) {
            som.update(input, learningRate, reward);
        }

        // --- Update Meta-SOM with representations ---
        for (BaseSOM som : baseSOMList) {
            bmuResults.add(metaSOM.organizeBaseSOM(som, learningRate));
        }

        return bmuResults;
    }

    // --- Random input & training utilities ---
    public double[] generateRandomInput() {
        double[] input = new double[INPUT_DIMENSION];
        for (int i = 0; i < INPUT_DIMENSION; i++) input[i] = random.nextGaussian();
        return input;
    }

    public void runContinuousLearning(int iterations) {
        System.out.println("Starting continuous learning for " + iterations + " iterations...");
        for (int i = 0; i < iterations; i++) {
            double[] input = generateRandomInput();
            double reward = random.nextDouble() * 2 - 1; // random feedback placeholder
            processInput(input, reward);

            if (i % 100 == 0) printStatus(i, reward);
        }
        System.out.println("Continuous learning completed.");
    }

    private void printStatus(int iteration) { printStatus(iteration, 0.0); }

    private void printStatus(int iteration, double reward) {
        System.out.printf("Iteration %d | Reward %.3f | Active SOMs: %d | Meta trails: %d%n",
                iteration, reward, baseSOMList.size(), metaSOM.getMetaWarpTrails().size());

        Map<BaseSOM, int[]> positions = metaSOM.getBaseSOMPositions();
        for (int i = 0; i < baseSOMList.size(); i++) {
            BaseSOM som = baseSOMList.get(i);
            int[] pos = positions.get(som);
            if (pos != null) {
                System.out.printf("  %-10s -> MetaPos[%2d,%2d]  Trails:%3d%n",
                        som.getMetricName(), pos[0], pos[1], som.getWarpTrailCount());
            }
        }
        System.out.println();
    }

    // --- Demonstration & visualization ---

    public void demonstrateReversibility() {
        System.out.println("Demonstrating reversibility...");
        double[] originalInput = generateRandomInput();
        System.out.println("Original input: " + Arrays.toString(originalInput));

        processInput(originalInput, 1.0);

        for (BaseSOM som : baseSOMList) {
            double[] representation = som.getRepresentation();
            double[] reconstructed = som.reconstruct(representation);
            if (reconstructed != null) {
                double error = new EuclideanDistance().calculate(originalInput, reconstructed);
                System.out.printf("%s SOM reconstruction error: %.6f%n",
                        som.getMetricName(), error);
            }
        }
        System.out.println();
    }

    public void visualizeMetaOrganization() {
        System.out.println("Meta-SOM Organization:");
        System.out.println("=====================");

        Map<BaseSOM, int[]> positions = metaSOM.getBaseSOMPositions();
        String[][] grid = new String[META_SOM_SIZE][META_SOM_SIZE];

        for (int i = 0; i < META_SOM_SIZE; i++)
            Arrays.fill(grid[i], "  .  ");

        for (int i = 0; i < baseSOMList.size(); i++) {
            BaseSOM som = baseSOMList.get(i);
            int[] pos = positions.get(som);
            if (pos != null && pos[0] < META_SOM_SIZE && pos[1] < META_SOM_SIZE) {
                String abbrev = som.getMetricName().substring(0, Math.min(3, som.getMetricName().length()));
                grid[pos[1]][pos[0]] = String.format(" %3s ", abbrev);
            }
        }

        for (String[] row : grid) {
            for (String cell : row) System.out.print(cell);
            System.out.println();
        }
        System.out.println();
    }

    // --- Maintenance ---
    public void reset() {
        initializeBaseSOM();
        metaSOM.reset();
    }

    // --- Getters & setters ---
    public void setLearningRate(double learningRate) { this.learningRate = learningRate; }
    public double getLearningRate() { return learningRate; }

    public void setContinuousLearning(boolean continuousLearning) { this.continuousLearning = continuousLearning; }
    public boolean isContinuousLearning() { return continuousLearning; }

    public List<BaseSOM> getBaseSOMList() { return new ArrayList<>(baseSOMList); }
    public MetaSOM getMetaSOM() { return metaSOM; }
    public List<DistanceMetric> getMetrics() { return new ArrayList<>(metrics); }

	public void setMetaSOM(MetaSOM loadedMeta) {
		metaSOM=loadedMeta;		
	}

	public void setBaseSOMs(List<BaseSOM> baseSOMList2) {
		baseSOMList =baseSOMList2;
		
	}


}
