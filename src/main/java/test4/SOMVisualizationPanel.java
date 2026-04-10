package test4;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

//Main 3D visualization panel
public class SOMVisualizationPanel extends JPanel {
 public List<VisualSOMNode> visualNodes;
 private double rotationX = 0;
 private double rotationY = 0;
 private double rotationZ = 0;
 public boolean showWarpTrails = false;
 private boolean autoRotate = true;
 
 public SOMVisualizationPanel() {
     this.visualNodes = new ArrayList<>();
     setBackground(Color.BLACK);
     setPreferredSize(new Dimension(800, 600));
     
     // Mouse controls for rotation
     MouseAdapter mouseHandler = new MouseAdapter() {
         private Point lastPoint;
         
         @Override
         public void mousePressed(MouseEvent e) {
             lastPoint = e.getPoint();
             autoRotate = false;
         }
         
         @Override
         public void mouseDragged(MouseEvent e) {
             if (lastPoint != null) {
                 int dx = e.getX() - lastPoint.x;
                 int dy = e.getY() - lastPoint.y;
                 
                 rotationY += dx * 0.01;
                 rotationX += dy * 0.01;
                 
                 lastPoint = e.getPoint();
                 repaint();
             }
         }
     };
     
     addMouseListener(mouseHandler);
     addMouseMotionListener(mouseHandler);
     
     // Key controls
     setFocusable(true);
     addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent e) {
             switch (e.getKeyCode()) {
                 case KeyEvent.VK_SPACE:
                     autoRotate = !autoRotate;
                     break;
                 case KeyEvent.VK_T:
                     showWarpTrails = !showWarpTrails;
                     break;
                 case KeyEvent.VK_R:
                     rotationX = rotationY = rotationZ = 0;
                     break;
             }
             repaint();
         }
     });
 }


 public void updateVisualization(List<BaseSOM> baseSOMList, MetaSOM metaSOM) {
     
         visualNodes.clear();
         Map<BaseSOM, int[]> positions = metaSOM.getBaseSOMPositions();
         Color[] colors = {
             new Color(255, 107, 107), // Red - Euclidean
             new Color(78, 205, 196),  // Teal - Manhattan
             new Color(69, 183, 209),  // Blue - Cosine
             new Color(249, 202, 36),  // Yellow - Chebyshev
             new Color(108, 92, 231)   // Purple - Minkowski
         };
         
         for (int i = 0; i < baseSOMList.size(); i++) {
             BaseSOM som = baseSOMList.get(i);
             int[] pos = positions.get(som);
            // System.out.println("SOM " + som.getMetricName() + " position: " + (pos != null ? pos[0] + "," + pos[1] : "null"));
             if (pos != null) {
                 // Convert meta-SOM position to 3D coordinates
                 double scale = 50.0;
                 double x = (pos[0] - metaSOM.getWidth() / 2.0) * scale;
                 double y = (pos[1] - metaSOM.getHeight() / 2.0) * scale;
                 double z = (som.getRepresentation()[2] - 0.5) * scale; // Use entropy as Z
                 
                 Point3D position = new Point3D(x, y, z);
                 Color color = colors[i % colors.length];
                 
                 VisualSOMNode node = new VisualSOMNode(
                     position, color, som.getMetricName(), i, som.getRepresentation()
                 );
                 
                 // Add warp trail points
                 if (showWarpTrails) {
                     List<WarpTrailNode> trails = som.getWarpTrails();
                     for (int j = Math.max(0, trails.size() - 10); j < trails.size(); j++) {
                         WarpTrailNode trail = trails.get(j);
                         double trailX = x + (trail.input[0] * 20);
                         double trailY = y + (trail.input[1] * 20);
                         double trailZ = z + (trail.input[2] * 20);
                         node.warpTrail.add(new Point3D(trailX, trailY, trailZ));
                     }
                 }
                 
                 visualNodes.add(node);
             }
         }
     
     repaint();
 }
 
 @Override
 protected void paintComponent(Graphics g) {
     super.paintComponent(g);
     Graphics2D g2d = (Graphics2D) g;
     g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
     
     // Auto-rotate if enabled
     if (autoRotate) {
         rotationY += 0.02;
     }
     
     int width = getWidth();
     int height = getHeight();
     double distance = 300;
     
         // Sort nodes by Z-distance for proper rendering order
         List<VisualSOMNode> sortedNodes = new ArrayList<>(visualNodes);
         sortedNodes.sort((a, b) -> {
             Point3D rotatedA = a.position3D.rotate(rotationX, rotationY, rotationZ);
             Point3D rotatedB = b.position3D.rotate(rotationX, rotationY, rotationZ);
             return Double.compare(rotatedB.z, rotatedA.z); // Far to near
         });
         
         // Draw coordinate axes
         drawAxes(g2d, width, height, distance);
         
         // Draw warp trails
         if (showWarpTrails) {
             drawWarpTrails(g2d, width, height, distance);
         }
         
         // Draw SOM nodes
         for (VisualSOMNode node : sortedNodes) {
             drawSOMNode(g2d, node, width, height, distance);
         }
         
         // Draw connections between nearby nodes
         drawConnections(g2d, sortedNodes, width, height, distance);
     
     // Draw UI overlay
     drawUI(g2d);
 }
 
 private void drawAxes(Graphics2D g2d, int width, int height, double distance) {
     double axisLength = 100;
     Point3D origin = new Point3D(0, 0, 0).rotate(rotationX, rotationY, rotationZ);
     Point3D xAxis = new Point3D(axisLength, 0, 0).rotate(rotationX, rotationY, rotationZ);
     Point3D yAxis = new Point3D(0, axisLength, 0).rotate(rotationX, rotationY, rotationZ);
     Point3D zAxis = new Point3D(0, 0, axisLength).rotate(rotationX, rotationY, rotationZ);
     
     ProjectedPoint originProj = origin.project(width, height, distance);
     ProjectedPoint xProj = xAxis.project(width, height, distance);
     ProjectedPoint yProj = yAxis.project(width, height, distance);
     ProjectedPoint zProj = zAxis.project(width, height, distance);
     
     g2d.setStroke(new BasicStroke(3));
     
     // X axis - Red
     g2d.setColor(new Color(255, 100, 100));
     g2d.drawLine((int)originProj.x, (int)originProj.y, (int)xProj.x, (int)xProj.y);
     g2d.setFont(new Font("Arial", Font.BOLD, 12));
     g2d.drawString("X", (int)xProj.x + 5, (int)xProj.y);
     
     // Y axis - Green
     g2d.setColor(new Color(100, 255, 100));
     g2d.drawLine((int)originProj.x, (int)originProj.y, (int)yProj.x, (int)yProj.y);
     g2d.drawString("Y", (int)yProj.x + 5, (int)yProj.y);
     
     // Z axis - Blue
     g2d.setColor(new Color(100, 100, 255));
     g2d.drawLine((int)originProj.x, (int)originProj.y, (int)zProj.x, (int)zProj.y);
     g2d.drawString("Z", (int)zProj.x + 5, (int)zProj.y);
     
     // Draw grid
     drawGrid(g2d, width, height, distance);
 }
 
 private void drawGrid(Graphics2D g2d, int width, int height, double distance) {
     g2d.setStroke(new BasicStroke(1));
     g2d.setColor(new Color(80, 80, 80, 100));
     
     int gridSize = 50;
     int gridCount = 5;
     
     for (int i = -gridCount; i <= gridCount; i++) {
         for (int j = -gridCount; j <= gridCount; j++) {
             // Horizontal grid lines
             Point3D p1 = new Point3D(i * gridSize, j * gridSize, 0).rotate(rotationX, rotationY, rotationZ);
             Point3D p2 = new Point3D((i+1) * gridSize, j * gridSize, 0).rotate(rotationX, rotationY, rotationZ);
             
             ProjectedPoint proj1 = p1.project(width, height, distance);
             ProjectedPoint proj2 = p2.project(width, height, distance);
             
             g2d.drawLine((int)proj1.x, (int)proj1.y, (int)proj2.x, (int)proj2.y);
             
             // Vertical grid lines
             Point3D p3 = new Point3D(i * gridSize, j * gridSize, 0).rotate(rotationX, rotationY, rotationZ);
             Point3D p4 = new Point3D(i * gridSize, (j+1) * gridSize, 0).rotate(rotationX, rotationY, rotationZ);
             
             ProjectedPoint proj3 = p3.project(width, height, distance);
             ProjectedPoint proj4 = p4.project(width, height, distance);
             
             g2d.drawLine((int)proj3.x, (int)proj3.y, (int)proj4.x, (int)proj4.y);
         }
     }
 }
 
 private void drawWarpTrails(Graphics2D g2d, int width, int height, double distance) {
     g2d.setStroke(new BasicStroke(2));
     
     for (VisualSOMNode node : visualNodes) {
         if (node.warpTrail.size() > 1) {
             // Create gradient effect for trails
             for (int i = 1; i < node.warpTrail.size(); i++) {
                 Point3D p1 = node.warpTrail.get(i-1).rotate(rotationX, rotationY, rotationZ);
                 Point3D p2 = node.warpTrail.get(i).rotate(rotationX, rotationY, rotationZ);
                 
                 ProjectedPoint proj1 = p1.project(width, height, distance);
                 ProjectedPoint proj2 = p2.project(width, height, distance);
                 
                 // Fade trail based on age
                 float alpha = (float)i / node.warpTrail.size() * 0.8f;
                 Color trailColor = new Color(0.0f, 1.0f, 0.0f, alpha);
                 g2d.setColor(trailColor);
                 
                 g2d.drawLine((int)proj1.x, (int)proj1.y, (int)proj2.x, (int)proj2.y);
             }
             
             // Draw trail connection to main node
             if (!node.warpTrail.isEmpty()) {
                 Point3D lastTrail = node.warpTrail.get(node.warpTrail.size()-1).rotate(rotationX, rotationY, rotationZ);
                 Point3D nodePos = node.position3D.rotate(rotationX, rotationY, rotationZ);
                 
                 ProjectedPoint trailProj = lastTrail.project(width, height, distance);
                 ProjectedPoint nodeProj = nodePos.project(width, height, distance);
                 
                 g2d.setColor(new Color(255, 255, 0, 150)); // Yellow connection
                 g2d.setStroke(new BasicStroke(3));
                 g2d.drawLine((int)trailProj.x, (int)trailProj.y, (int)nodeProj.x, (int)nodeProj.y);
             }
         }
     }
 }
 
 private void drawSOMNode(Graphics2D g2d, VisualSOMNode node, int width, int height, double distance) {
     Point3D rotated = node.position3D.rotate(rotationX, rotationY, rotationZ);
     ProjectedPoint projected = rotated.project(width, height, distance);
     
     // Calculate size based on distance (perspective)
     double size = node.size * projected.z;
     size = Math.max(size, 4); // Minimum size
     
     // Draw node shadow
     g2d.setColor(new Color(0, 0, 0, 100));
     int shadowOffset = 2;
     g2d.fillOval(
         (int)(projected.x - size/2 + shadowOffset), 
         (int)(projected.y - size/2 + shadowOffset), 
         (int)size, (int)size
     );
     
     // Draw node
     g2d.setColor(node.color);
     int x = (int)(projected.x - size/2);
     int y = (int)(projected.y - size/2);
     g2d.fillOval(x, y, (int)size, (int)size);
     
     // Draw highlight
     g2d.setColor(new Color(255, 255, 255, 150));
     g2d.fillOval(x + (int)size/4, y + (int)size/4, (int)size/3, (int)size/3);
     
     // Draw border
     g2d.setColor(Color.WHITE);
     g2d.setStroke(new BasicStroke(2));
     g2d.drawOval(x, y, (int)size, (int)size);
     
     // Draw label with background
     g2d.setFont(new Font("Arial", Font.BOLD, 10));
     FontMetrics fm = g2d.getFontMetrics();
     String label = node.metricName.substring(0, Math.min(4, node.metricName.length()));
     int labelWidth = fm.stringWidth(label);
     int labelHeight = fm.getHeight();
     
     int labelX = (int)(projected.x - labelWidth/2);
     int labelY = (int)(projected.y + size/2 + labelHeight);
     
     // Label background
     g2d.setColor(new Color(0, 0, 0, 150));
     g2d.fillRect(labelX - 2, labelY - labelHeight + 2, labelWidth + 4, labelHeight);
     
     // Label text
     g2d.setColor(Color.WHITE);
     g2d.drawString(label, labelX, labelY);
 }
 
 private void drawConnections(Graphics2D g2d, List<VisualSOMNode> nodes, int width, int height, double distance) {
     g2d.setStroke(new BasicStroke(1));
     
     for (int i = 0; i < nodes.size(); i++) {
         for (int j = i + 1; j < nodes.size(); j++) {
             VisualSOMNode node1 = nodes.get(i);
             VisualSOMNode node2 = nodes.get(j);
             
             // Calculate distance between representations
             double distance3D = node1.position3D.distanceTo(node2.position3D);
             
             // Draw connection if nodes are close
             if (distance3D < 120) {
                 Point3D p1 = node1.position3D.rotate(rotationX, rotationY, rotationZ);
                 Point3D p2 = node2.position3D.rotate(rotationX, rotationY, rotationZ);
                 
                 ProjectedPoint proj1 = p1.project(width, height, distance);
                 ProjectedPoint proj2 = p2.project(width, height, distance);
                 
                 // Connection strength based on distance
                 float strength = (float)(1.0 - distance3D / 120.0);
                 int alpha = (int)(strength * 100);
                 
                 g2d.setColor(new Color(255, 255, 255, alpha));
                 g2d.drawLine((int)proj1.x, (int)proj1.y, (int)proj2.x, (int)proj2.y);
                 
                 // Draw midpoint indicator for strong connections
                 if (strength > 0.7) {
                     int midX = (int)((proj1.x + proj2.x) / 2);
                     int midY = (int)((proj1.y + proj2.y) / 2);
                     g2d.setColor(new Color(255, 255, 0, alpha));
                     g2d.fillOval(midX - 2, midY - 2, 4, 4);
                 }
             }
         }
     }
 }
 
 private void drawUI(Graphics2D g2d) {
     // Semi-transparent background for UI
     g2d.setColor(new Color(0, 0, 0, 150));
     g2d.fillRect(5, 5, 280, 160);
     
     g2d.setColor(Color.WHITE);
     g2d.setFont(new Font("Arial", Font.BOLD, 14));
     
     int y = 25;
     g2d.drawString("Multi-Metric SOM Hierarchy", 10, y);
     
     g2d.setFont(new Font("Arial", Font.PLAIN, 11));
     y += 20;
     g2d.drawString("Controls:", 10, y);
     y += 15;
     g2d.drawString("• Mouse: Rotate view", 15, y);
     y += 15;
     g2d.drawString("• Space: Toggle auto-rotation", 15, y);
     y += 15;
     g2d.drawString("• T: Toggle warp trails", 15, y);
     y += 15;
     g2d.drawString("• R: Reset rotation", 15, y);
     y += 20;
     
     g2d.setFont(new Font("Arial", Font.BOLD, 11));
     g2d.drawString("Status:", 10, y);
     y += 15;
     g2d.setFont(new Font("Arial", Font.PLAIN, 11));
     g2d.drawString("Nodes: " + visualNodes.size(), 15, y);
     y += 15;
     g2d.drawString("Warp Trails: " + (showWarpTrails ? "ON" : "OFF"), 15, y);
     y += 15;
     g2d.drawString("Auto-Rotate: " + (autoRotate ? "ON" : "OFF"), 15, y);
     
     // Draw color legend in bottom right
     drawColorLegend(g2d);
 }
 
 private void drawColorLegend(Graphics2D g2d) {
     int legendX = getWidth() - 180;
     int legendY = getHeight() - 120;
     
     // Background
     g2d.setColor(new Color(0, 0, 0, 150));
     g2d.fillRect(legendX - 5, legendY - 5, 175, 110);
     
     g2d.setColor(Color.WHITE);
     g2d.setFont(new Font("Arial", Font.BOLD, 12));
     g2d.drawString("Distance Metrics:", legendX, legendY + 15);
     
     Color[] colors = {
         new Color(255, 107, 107), // Red - Euclidean
         new Color(78, 205, 196),  // Teal - Manhattan
         new Color(69, 183, 209),  // Blue - Cosine
         new Color(249, 202, 36),  // Yellow - Chebyshev
         new Color(108, 92, 231)   // Purple - Minkowski
     };
     
     String[] names = {"Euclidean", "Manhattan", "Cosine", "Chebyshev", "Minkowski"};
     
     g2d.setFont(new Font("Arial", Font.PLAIN, 10));
     for (int i = 0; i < colors.length; i++) {
         int y = legendY + 30 + i * 15;
         
         // Color box
         g2d.setColor(colors[i]);
         g2d.fillRect(legendX, y - 8, 12, 12);
         g2d.setColor(Color.WHITE);
         g2d.drawRect(legendX, y - 8, 12, 12);
         
         // Label
         g2d.drawString(names[i], legendX + 18, y + 2);
     }
 }
}