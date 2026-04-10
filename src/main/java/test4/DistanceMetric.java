//Distance metric interface
package test4;



public interface DistanceMetric {
 double calculate(double[] a, double[] b);
 String getName();
}

//Euclidean distance implementation
class EuclideanDistance implements DistanceMetric {
 @Override
 public double calculate(double[] a, double[] b) {
     double sum = 0.0;
     for (int i = 0; i < a.length; i++) {
         sum += Math.pow(a[i] - b[i], 2);
     }
     return Math.sqrt(sum);
 }
 
 @Override
 public String getName() { return "Euclidean"; }
}

//Manhattan distance implementation
class ManhattanDistance implements DistanceMetric {
 @Override
 public double calculate(double[] a, double[] b) {
     double sum = 0.0;
     for (int i = 0; i < a.length; i++) {
         sum += Math.abs(a[i] - b[i]);
     }
     return sum;
 }
 
 @Override
 public String getName() { return "Manhattan"; }
}

//Cosine distance implementation
class CosineDistance implements DistanceMetric {
 @Override
 public double calculate(double[] a, double[] b) {
     double dotProduct = 0.0;
     double magnitudeA = 0.0;
     double magnitudeB = 0.0;
     
     for (int i = 0; i < a.length; i++) {
         dotProduct += a[i] * b[i];
         magnitudeA += a[i] * a[i];
         magnitudeB += b[i] * b[i];
     }
     
     magnitudeA = Math.sqrt(magnitudeA);
     magnitudeB = Math.sqrt(magnitudeB);
     
     if (magnitudeA == 0.0 || magnitudeB == 0.0) return 1.0;
     return 1.0 - (dotProduct / (magnitudeA * magnitudeB));
 }
 
 @Override
 public String getName() { return "Cosine"; }
}

//Chebyshev distance implementation
class ChebyshevDistance implements DistanceMetric {
 @Override
 public double calculate(double[] a, double[] b) {
     double maxDiff = 0.0;
     for (int i = 0; i < a.length; i++) {
         maxDiff = Math.max(maxDiff, Math.abs(a[i] - b[i]));
     }
     return maxDiff;
 }
 
 @Override
 public String getName() { return "Chebyshev"; }
}

//Minkowski distance implementation
class MinkowskiDistance implements DistanceMetric {
 private final double p;
 
 public MinkowskiDistance(double p) {
     this.p = p;
 }
 
 @Override
 public double calculate(double[] a, double[] b) {
     double sum = 0.0;
     for (int i = 0; i < a.length; i++) {
         sum += Math.pow(Math.abs(a[i] - b[i]), p);
     }
     return Math.pow(sum, 1.0 / p);
 }
 
 @Override
 public String getName() { return "Minkowski"; }
}