package test4;


import java.util.*;

//Warp trail node for reversible computing
public class WarpTrailNode {
 public final double[] input;
 public final int[] bmuPosition;
 public final long timestamp;
 public final List<WeightTransformation> transformations;
 
 public WarpTrailNode(double[] input, int[] bmuPosition) {
     this.input = Arrays.copyOf(input, input.length);
     this.bmuPosition = Arrays.copyOf(bmuPosition, bmuPosition.length);
     this.timestamp = System.currentTimeMillis();
     this.transformations = new ArrayList<>();
 }
}

//Weight transformation record
class WeightTransformation {
 public final int[] position;
 public final double[] oldWeights;
 public final double[] newWeights;
 public final double influence;
 
 public WeightTransformation(int[] position, double[] oldWeights, 
                            double[] newWeights, double influence) {
     this.position = Arrays.copyOf(position, position.length);
     this.oldWeights = Arrays.copyOf(oldWeights, oldWeights.length);
     this.newWeights = Arrays.copyOf(newWeights, newWeights.length);
     this.influence = influence;
 }
}

//Best Matching Unit result
class BMUResult {
 public final int[] position;
 public final double distance;
 
 public BMUResult(int[] position, double distance) {
     this.position = Arrays.copyOf(position, position.length);
     this.distance = distance;
 }
}

//Meta-level warp trail for meta-SOM
class MetaWarpTrail {
 public final BaseSOM baseSOM;
 public final double[] representation;
 public final int[] bmuPosition;
 public final long timestamp;
 
 public MetaWarpTrail(BaseSOM baseSOM, double[] representation, int[] bmuPosition) {
     this.baseSOM = baseSOM;
     this.representation = Arrays.copyOf(representation, representation.length);
     this.bmuPosition = Arrays.copyOf(bmuPosition, bmuPosition.length);
     this.timestamp = System.currentTimeMillis();
 }
}