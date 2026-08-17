package test4;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONObject;

// Meta-level Self-Organizing Map that organizes base SOMs
public class MetaSOM {
    private final int width;
    private final int height;
    private int representationDimension;
    private final double[][][] weights;
    private Map<BaseSOM, int[]> baseSOMPositions;
    private final List<MetaWarpTrail> metaWarpTrails;
    private final DistanceMetric metaMetric;
    private final Random random;
    private double[] lastOutput ;
	private static String homePath = "/home/wes/tv/test4/som/";
	private String uName;
	// Track all BaseSOMs that this MetaSOM manages
	private  List<BaseSOM> baseSOMList = new ArrayList<>();

    public MetaSOM(int width, int height, int representationDimension, boolean hasReward,String uName) {
        this.width = width;
        this.height = height;
		this.uName = uName;
        this.representationDimension = hasReward
                ? representationDimension + 1    // add reward slot
                : representationDimension;
        this.weights = new double[width][height][this.representationDimension];
        this.baseSOMPositions = new ConcurrentHashMap<>();
        this.metaWarpTrails = new ArrayList<>();
        this.metaMetric = new EuclideanDistance(); // Meta-level uses Euclidean
        this.random = new Random();
        this.lastOutput = new double[width*2];
      //  initializeWeights();
    }
    
    private void initializeWeights() {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                for (int k = 0; k < representationDimension; k++) {
                    weights[i][j][k] = random.nextGaussian() * 0.5;
                }
            }
        }
    }
    
    private BMUResult findBMU(double[] representation) {
        double minDistance = Double.MAX_VALUE;
        int[] bmuPosition = new int[2];
        
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                double distance = metaMetric.calculate(representation, weights[i][j]);
                if (distance < minDistance) {
                    minDistance = distance;
                    bmuPosition[0] = i;
                    bmuPosition[1] = j;
                }
            }
        }
        
        return new BMUResult(bmuPosition, minDistance);
    }
    

    public double[] projectTemporal(double[] input) {
    	

        if (lastOutput == null || lastOutput.length != representationDimension) {
            lastOutput = new double[representationDimension];
        }

        double[] combined = new double[representationDimension];
        for (int i = 0; i < representationDimension; i++) {
            combined[i] = 0.7 * input[i] + 0.3 * lastOutput[i]; // smooth memory
        }

        double[] out = project(combined);
        lastOutput = out;
        return out;
    }


    public double[] project(double[] input) {
    	BMUResult bmu = findBMU(input);
    	double[] projected = new double[representationDimension];
    	for (int k = 0; k < representationDimension; k++) {
    	    projected[k] = weights[bmu.position[0]][bmu.position[1] ][k];
    	}
    	return projected;

    }

    
    
    
 // MetaSOM.java — add near the bottom (ensure imports: java.util.Arrays; java.util.List;)
    public double[] getLearnedVectorFor(BaseSOM som) {
        // The metaSOM expects a 4-D representation for each base SOM.
        double[] rep = som.getRepresentation(); // length == representationDimension (4)
        BMUResult bmu = findBMU(rep);           // private is fine to call internally
        // Return the learned embedding at that BMU.
        return Arrays.copyOf(
            weights[bmu.position[0]][bmu.position[1]],
            representationDimension
        );
    }

    public double[] getAggregateLearnedVector(List<BaseSOM> soms) {
        double[] agg = new double[representationDimension]; // 4-D
        if (soms == null || soms.isEmpty()) return agg;
        for (BaseSOM s : soms) {
            double[] v = getLearnedVectorFor(s); // 4-D each
            for (int i = 0; i < representationDimension; i++) agg[i] += v[i];
        }
        for (int i = 0; i < representationDimension; i++) agg[i] /= soms.size();
        return agg;
    }
    /**
     * Trains the MetaSOM by moving the BMU and its neighbors toward the given input vector.
     * This version updates all representationDimension weights, including the reward channel.
     */
    public void train(double[] input, double learningRate, double radius) {
        // Defensive check: resize input if needed
        if (input.length != representationDimension) {
            double[] fixed = new double[representationDimension];
            System.arraycopy(input, 0, fixed, 0, Math.min(input.length, representationDimension));
            input = fixed;
        }

        // 1. Find BMU for this input
        BMUResult bmu = findBMU(input);
        int bmuX = bmu.position[0];
        int bmuY = bmu.position[1];

        // 2. Update BMU and its neighbors
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                double dist = Math.hypot(i - bmuX, j - bmuY);
                if (dist <= radius) {
                    double influence = Math.exp(-(dist * dist) / (2 * radius * radius));
                    for (int k = 0; k < representationDimension; k++) {
                        double oldWeight = weights[i][j][k];
                        double delta = learningRate * influence * (input[k] - oldWeight);
                        weights[i][j][k] += delta;
                    }
                }
            }
        }
    }

    public BMUResult organizeBaseSOM(BaseSOM baseSOM, double learningRate) {
        double[] representation = baseSOM.getRepresentation();
        BMUResult bmu = findBMU(representation);
        
        // Update meta-level weights
        double radius = Math.max(width, height) * 0.3;
        
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                double distance = Math.sqrt(
                    Math.pow(i - bmu.position[0], 2) + 
                    Math.pow(j - bmu.position[1], 2)
                );
                
                if (distance <= radius) {
                    double influence = Math.exp(-distance * distance / (2 * radius * radius));
                    double effectiveLearningRate = learningRate * influence;
                    
                    for (int k = 0; k < representationDimension; k++) {
                        double target = (k < representation.length) ? representation[k] : 0.0;  // pad if needed
                        weights[i][j][k] += effectiveLearningRate * (target - weights[i][j][k]);
                    }

                }
            }
        }
        
        // Store position for visualization
        baseSOMPositions.put(baseSOM, Arrays.copyOf(bmu.position, bmu.position.length));

        // Create meta warp trail
        MetaWarpTrail metaTrail = new MetaWarpTrail(baseSOM, representation, bmu.position);
        metaWarpTrails.add(metaTrail);
        
        // Keep last 50 meta trails
        if (metaWarpTrails.size() > 500) {
            metaWarpTrails.remove(0);
        }
        
        return bmu;
    }
    
    
 // inside MetaSOM.java
    public double[] getLearnedVector(double[] input) {
        BMUResult bmu = findBMU(input);   // find best matching unit
        double[] learned = Arrays.copyOf(weights[bmu.position[0]][bmu.position[1]], weights[bmu.position[0]][bmu.position[1]].length);
        return learned;
    }

    
    
    public double[] getMetaRepresentation() {
        // Create representation of the meta-SOM state
        double sum = 0.0;
        double sumSquares = 0.0;
        int totalWeights = width * height * representationDimension;
        
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                for (int k = 0; k < representationDimension; k++) {
                    double weight = weights[i][j][k];
                    sum += weight;
                    sumSquares += weight * weight;
                }
            }
        }
        
        double mean = sum / totalWeights;
        double variance = (sumSquares / totalWeights) - (mean * mean);
        double organizationComplexity = baseSOMPositions.size() / 10.0; // Normalized
        double trailDensity = metaWarpTrails.size() / 50.0;
        
        return new double[]{mean, variance, organizationComplexity, trailDensity};
    }
    
    // Getters for visualization
    public Map<BaseSOM, int[]> getBaseSOMPositions() {
        return new HashMap<>(baseSOMPositions);
    }
    
    public List<MetaWarpTrail> getMetaWarpTrails() {
        return new ArrayList<>(metaWarpTrails);
    }
    
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getRepresentationDimension() { return representationDimension; }
    
    // Method to access weights for visualization
    public double[][][] getWeights() {
        double[][][] weightsCopy = new double[width][height][representationDimension];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                weightsCopy[i][j] = Arrays.copyOf(weights[i][j], representationDimension);
            }
        }
        return weightsCopy;
    }
    
    // Reset method for system reset
    public void reset() {
        baseSOMPositions.clear();
        metaWarpTrails.clear();
        initializeWeights();
    }
    
    private static double sanitize(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v))
            return 0.0; // or any default value like small random noise
        return v;
    }

    public void registerBaseSOM(BaseSOM som) {
    	if (!baseSOMList.contains(som)) {
    		baseSOMList.add(som);
    	}
    }

// =======================================
// META SOM SAVE (Option 2 - separate BaseSOM files)
// =======================================

public void saveToJson(String dir, String fn,String uName) throws IOException {
    JSONObject root = new JSONObject();

    root.put("width", width);
    root.put("height", height);
    root.put("representationDimension", representationDimension);
    root.put("uName", uName);

    // save meta weights
    JSONArray wArray = new JSONArray();
    for (int i = 0; i < width; i++) {
        JSONArray row = new JSONArray();
        for (int j = 0; j < height; j++) {
            JSONArray cell = new JSONArray();
            for (int k = 0; k < representationDimension; k++) {
                cell.put(weights[i][j][k]);
            }
            row.put(cell);
        }
        wArray.put(row);
    }
    root.put("weights", wArray);

    // save lastOutput
    JSONArray lo = new JSONArray();
    for (double v : lastOutput) lo.put(v);
    root.put("lastOutput", lo);

    // save each BaseSOM position
    JSONObject posObj = new JSONObject();
    for (BaseSOM b : baseSOMList) {
        int[] pos = baseSOMPositions.get(b);
        if (pos != null) {
            JSONArray arr = new JSONArray();
            arr.put(pos[0]);
            arr.put(pos[1]);
            posObj.put(b.getMetricName(), arr);

            // ALSO save the BaseSOM itself
            File f = new File(dir + uName + b.getMetricName() + ".json");
            b.saveToJson(f);
        }
    }
    root.put("baseSOMPositions", posObj);

    // write meta json
    File metaFile = new File(dir + uName + fn + ".json");
    try (FileWriter fw = new FileWriter(metaFile)) {
        fw.write(root.toString(2));
    }

    System.out.println("✅ MetaSOM saved: " + metaFile.getAbsolutePath());
}



// =======================================
// META SOM LOAD
// =======================================

public static MetaSOM loadFromJson(String dir, String fn, String uName) throws IOException {
    String path = dir + uName + fn + ".json";
    String text = Files.readString(Path.of(path));
    JSONObject root = new JSONObject(text);

    int width = root.getInt("width");
    int height = root.getInt("height");
    int repDim = root.getInt("representationDimension");

    MetaSOM meta = new MetaSOM(width, height, repDim, false, uName);

    // restore meta weights
    JSONArray wArray = root.getJSONArray("weights");
    for (int i = 0; i < width; i++) {
        JSONArray row = wArray.getJSONArray(i);
        for (int j = 0; j < height; j++) {
            JSONArray cell = row.getJSONArray(j);
            for (int k = 0; k < repDim; k++) {
                meta.weights[i][j][k] = cell.getDouble(k);
            }
        }
    }

    // restore lastOutput
    if (root.has("lastOutput")) {
        JSONArray lo = root.getJSONArray("lastOutput");
        meta.lastOutput = new double[lo.length()];
        for (int i = 0; i < lo.length(); i++) {
            meta.lastOutput[i] = lo.getDouble(i);
        }
    }

    // restore BaseSOM list + positions
    if (root.has("baseSOMPositions")) {
        JSONObject posObj = root.getJSONObject("baseSOMPositions");

        for (String metricName : posObj.keySet()) {
            JSONArray arr = posObj.getJSONArray(metricName);
            int px = arr.getInt(0);
            int py = arr.getInt(1);

            File f = new File(dir + uName + metricName + ".json");
            if (!f.exists()) {
                System.err.println("⚠ Missing BaseSOM file: " + f);
                continue;
            }

            BaseSOM som = BaseSOM.loadFromJson(f);
            meta.baseSOMList.add(som);
            meta.baseSOMPositions.put(som, new int[]{px, py});
        }
    }

    System.out.println("✅ MetaSOM load complete. Loaded BaseSOMs: " + meta.baseSOMList.size());
    return meta;
}


public List<BaseSOM>  getBaseSOMList() {
	// TODO Auto-generated method stub
	return  baseSOMList ;
}

public int[] getBaseSOMPosition(BaseSOM som) {
	// TODO Auto-generated method stub
	return baseSOMPositions.get(som);
}

    
    
    
    
    
}