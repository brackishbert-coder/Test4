package test4;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//Visual SOM Node for rendering
public class VisualSOMNode {
 public Point3D position3D;
 public Color color;
 public String metricName;
 public double size;
 public int somIndex;
 public double[] representation;
 public List<Point3D> warpTrail;
 
 public VisualSOMNode(Point3D position, Color color, String metricName, 
                     int somIndex, double[] representation) {
     this.position3D = position;
     this.color = color;
     this.metricName = metricName;
     this.somIndex = somIndex;
     this.size = 8.0;
     this.representation = Arrays.copyOf(representation, representation.length);
     this.warpTrail = new ArrayList<>();
 }
}