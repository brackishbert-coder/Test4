package test4;
import game.LegalMoveLibrary;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;

// Individual Self-Organizing Map with rule-based feedback adaptation
public class BaseSOM {
    private final int width;
    private final int height;
    private final int inputDimension;
    private final DistanceMetric metric;
    private final String metricName;
    final double[][][] weights;
    private final List<WarpTrailNode> warpTrails;
    private final Random random;

    // adaptive parameters
    private double globalLearningRate = 0.1;
    private double rewardMomentum = 0.8;
    private double lastReward = 0.0;
	private double learningRate;
	private double radius;
	private String uName;
	private String path;

    public BaseSOM(int width, int height, int inputDimension, DistanceMetric metric) {
        this.width = width;
        this.height = height;
        this.inputDimension = inputDimension;
        this.metric = metric;
		this.setPath("");
		this.setuName("SOMUNAME");
        this.metricName = metric.getName();
        this.weights = new double[width][height][inputDimension];
        this.warpTrails = new ArrayList<>();
        this.random = new Random();
        initializeWeights();
    }

    
// =============================
// BASE SOM SAVE + LOAD (Option 2)
// =============================

public void saveToJson(File file) throws IOException {
    JSONObject root = new JSONObject();

    root.put("width", width);
    root.put("height", height);
    root.put("inputDimension", inputDimension);
    root.put("metric", metricName);

    // weights
    JSONArray wArray = new JSONArray();
    for (int i = 0; i < width; i++) {
        JSONArray row = new JSONArray();
        for (int j = 0; j < height; j++) {
            JSONArray cell = new JSONArray();
            for (int k = 0; k < inputDimension; k++) {
                cell.put(weights[i][j][k]);
            }
            row.put(cell);
        }
        wArray.put(row);
    }
    root.put("weights", wArray);

    // write file
    try (FileWriter fw = new FileWriter(file)) {
        fw.write(root.toString(2));
    }
    System.out.println("✅ BaseSOM saved: " + file.getAbsolutePath());
}


public static BaseSOM loadFromJson(File file) throws IOException {
    if (!file.exists()) throw new IOException("Missing BaseSOM file: " + file);
    String text = Files.readString(file.toPath());
    JSONObject root = new JSONObject(text);

    int width = root.getInt("width");
    int height = root.getInt("height");
    int dim = root.getInt("inputDimension");
    String metricName = root.getString("metric");

    DistanceMetric metric = BaseSOM.metricFromName(metricName);

    BaseSOM som = new BaseSOM(width, height, dim, metric);

    // load weights
    JSONArray wArray = root.getJSONArray("weights");
    for (int i = 0; i < width; i++) {
        JSONArray row = wArray.getJSONArray(i);
        for (int j = 0; j < height; j++) {
            JSONArray cell = row.getJSONArray(j);
            for (int k = 0; k < dim; k++) {
                som.weights[i][j][k] = cell.getDouble(k);
            }
        }
    }

    System.out.println("✅ BaseSOM loaded: " + file.getAbsolutePath());
    return som;
}


 /** Converts metricName → actual DistanceMetric instance */
 public static DistanceMetric metricFromName(String name) {
     return switch (name.toLowerCase()) {
         case "euclidean" -> new EuclideanDistance();
         case "manhattan" -> new ManhattanDistance();
         case "chebyshev" -> new ChebyshevDistance();
         case "cosine"	-> new CosineDistance();
         case "minkowski" -> new MinkowskiDistance(3.0);
         default -> new EuclideanDistance();
     };
 }



    private static double sanitize(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v))
            return 0.0; // or any default value like small random noise
        return v;
    }

    
    
    private void initializeWeights() {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
            	double[] normalized = LegalMoveLibrary.generateMixedMoveSeeds();
                for (int k = 0; k < inputDimension; k++) {
                    weights[i][j][k] = normalized[k];
                }
            }
        }
    }

    /** Standard BMU search */
    public BMUResult findBMU(double[] input) {
        double minDistance = Double.MAX_VALUE;
        int[] bmuPosition = new int[2];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                double distance = metric.calculate(input, weights[i][j]);
                if (distance < minDistance) {
                    minDistance = distance;
                    bmuPosition[0] = i;
                    bmuPosition[1] = j;
                }
            }
        }
        return new BMUResult(bmuPosition, minDistance);
    }

    /** Original update (default reward = 1) */
    public BMUResult update(double[] input, double learningRate) {
        this.setLearningRate(learningRate);
		return update(input, learningRate, 1.0);
    }

    /** New update with reward feedback (-1 to +1) */
    public BMUResult update(double[] input, double learningRate, double reward) {
        this.setLearningRate(learningRate);
		// smooth reward history for stability
        double adjustedReward = (rewardMomentum * lastReward) + ((1 - rewardMomentum) * reward);
        lastReward = adjustedReward;

        BMUResult bmu = findBMU(input);
        WarpTrailNode trail = new WarpTrailNode(input, bmu.position);

        // dynamic neighborhood radius (shrinks over time)
        double maxDimension = Math.max(width, height);
        radius = maxDimension * 0.5 * Math.exp(-warpTrails.size() / 1000.0);

        // apply reward scaling to the effective learning rate
        double rewardScaledLR = learningRate * (1.0 + adjustedReward * globalLearningRate);

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                double distance = Math.sqrt(
                        Math.pow(i - bmu.position[0], 2) + Math.pow(j - bmu.position[1], 2));
                if (distance <= radius) {
                    double influence = Math.exp(-distance * distance / (2 * radius * radius));
                    double effectiveLearningRate = rewardScaledLR * influence;

                    double[] oldWeights = Arrays.copyOf(weights[i][j], inputDimension);
                    int len = Math.min(input.length, inputDimension);

                    for (int k = 0; k < len; k++) {
                        weights[i][j][k] += effectiveLearningRate * (input[k] - weights[i][j][k]);
                    }

                    trail.transformations.add(new WeightTransformation(
                            new int[]{i, j},
                            oldWeights,
                            Arrays.copyOf(weights[i][j], inputDimension),
                            influence
                    ));
                }
            }
        }

        warpTrails.add(trail);
        if (warpTrails.size() > 100)
            warpTrails.remove(0);

        return bmu;
    }

    // --- Representation and analysis methods (unchanged) ---
    public double[] getRepresentation() {
        double sum = 0.0, sumSquares = 0.0;
        int totalWeights = width * height * inputDimension;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                for (int k = 0; k < inputDimension; k++) {
                    double w = weights[i][j][k];
                    sum += w;
                    sumSquares += w * w;
                }
            }
        }
        double mean = sum / totalWeights;
        double variance = (sumSquares / totalWeights) - (mean * mean);
        double entropy = calculateEntropy();
        double trailDensity = warpTrails.size() / 100.0;
        return new double[]{mean, variance, entropy, trailDensity};
    }

    private double calculateEntropy() {
        Map<Integer, Integer> histogram = new HashMap<>();
        double binSize = 0.1;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                for (int k = 0; k < inputDimension; k++) {
                    int bin = (int) Math.floor(weights[i][j][k] / binSize);
                    histogram.put(bin, histogram.getOrDefault(bin, 0) + 1);
                }
            }
        }
        int totalBins = width * height * inputDimension;
        double entropy = 0.0;
        for (int count : histogram.values()) {
            double probability = (double) count / totalBins;
            if (probability > 0)
                entropy -= probability * Math.log(probability) / Math.log(2);
        }
        return entropy;
    }

    public double[] reconstruct(double[] representation) {
        if (warpTrails.isEmpty()) return null;
        return Arrays.copyOf(
                warpTrails.get(warpTrails.size() - 1).input,
                warpTrails.get(warpTrails.size() - 1).input.length);
    }

    // --- Getters ---
    public String getMetricName() { return metricName; }
    public int getWarpTrailCount() { return warpTrails.size(); }
    public List<WarpTrailNode> getWarpTrails() { return new ArrayList<>(warpTrails); }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getInputDimension() { return inputDimension; }
    public double[][][] getWeights() {
        double[][][] copy = new double[width][height][inputDimension];
        for (int i = 0; i < width; i++)
            for (int j = 0; j < height; j++)
                copy[i][j] = Arrays.copyOf(weights[i][j], inputDimension);
        return copy;
    }


	public double getLearningRate() {
		return learningRate;
	}


	public void setLearningRate(double learningRate) {
		this.learningRate = learningRate;
	}


	public String getuName() {
		return uName;
	}


	public void setuName(String uName) {
		this.uName = uName;
	}


	public String getPath() {
		return path;
	}


	public void setPath(String path) {
		this.path = path;
	}
}
