package test4;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

// Individual SOM visualization panel
public class IndividualSOMPanel extends JPanel {
    private BaseSOM som;
    private String title;
    private Color baseColor;
    
    public IndividualSOMPanel(BaseSOM som, String title, Color baseColor) {
        this.som = som;
        this.title = title;
        this.baseColor = baseColor;
        setPreferredSize(new Dimension(200, 220));
        setBackground(Color.BLACK);
        setBorder(BorderFactory.createLineBorder(baseColor, 2));
    }
    
    public void updateSOM(BaseSOM newSOM) {
            this.som = newSOM;
        repaint();
    }
    public void setSOM(BaseSOM newSOM) {
        this.som = newSOM;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        
            if (som == null) return;
            
            int width = getWidth();
            int height = getHeight() - 30; // Leave space for title
            int gridSize = Math.min(width, height) - 20;
            int cellSize = gridSize / 16; 
            int startX = (width - gridSize) / 2;
            int startY = 25 + (height - gridSize) / 2;
            
            // Draw title
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            FontMetrics fm = g2d.getFontMetrics();
            int titleWidth = fm.stringWidth(title);
            g2d.drawString(title, (width - titleWidth) / 2, 18);
            
            // Get SOM weights for visualization
            double[][][] weights = som.getWeights();
            if (weights == null) return;
            
            // Calculate activation levels for each cell
            double maxActivation = Double.MIN_VALUE;
            double minActivation = Double.MAX_VALUE;
            double[][] activations = new double[16][16];
            
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    // Calculate average weight magnitude as activation
                    double sum = 0;
                    for (int k = 0; k < 4; k++) {
                        sum += Math.abs(weights[i][j][k]);
                    }
                    activations[i][j] = sum / 4;
                    maxActivation = Math.max(maxActivation, activations[i][j]);
                    minActivation = Math.min(minActivation, activations[i][j]);
                }
            }
            
            // Draw SOM grid
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    int x = startX + i * cellSize;
                    int y = startY + j * cellSize;
                    
                    // Normalize activation to 0-1 range
                    double normalizedActivation = (activations[i][j] - minActivation) / 
                                                (maxActivation - minActivation + 1e-10);
                    
                    // Create color based on base color and activation
                    int red = (int)(baseColor.getRed() * normalizedActivation);
                    int green = (int)(baseColor.getGreen() * normalizedActivation);
                    int blue = (int)(baseColor.getBlue() * normalizedActivation);
                    
                    Color cellColor = new Color(
                        Math.min(255, Math.max(0, red)),
                        Math.min(255, Math.max(0, green)),
                        Math.min(255, Math.max(0, blue))
                    );
                    
                    // Draw cell
                    g2d.setColor(cellColor);
                    g2d.fillRect(x, y, cellSize, cellSize);
                    
                    // Draw cell border
                    g2d.setColor(Color.GRAY);
                    g2d.setStroke(new BasicStroke(1));
                    g2d.drawRect(x, y, cellSize, cellSize);
                }
            }
            
            // Draw recent BMU positions
            drawRecentBMUs(g2d, startX, startY, cellSize);
            
            // Draw statistics
            drawStatistics(g2d, width, height);
        
    }
    
    private void drawRecentBMUs(Graphics2D g2d, int startX, int startY, int cellSize) {
        List<WarpTrailNode> trails = som.getWarpTrails();
        if (trails.isEmpty()) return;
        
        // Draw last few BMU positions
        int trailCount = Math.min(10, trails.size());
        for (int i = trails.size() - trailCount; i < trails.size(); i++) {
            WarpTrailNode trail = trails.get(i);
            int[] bmu = trail.bmuPosition;
            
            if (bmu[0] >= 0 && bmu[0] < 8 && bmu[1] >= 0 && bmu[1] < 8) {
                int x = startX + bmu[0] * cellSize + cellSize / 2;
                int y = startY + bmu[1] * cellSize + cellSize / 2;
                
                // Fade based on age
                float alpha = (float)(i - (trails.size() - trailCount)) / trailCount;
                g2d.setColor(new Color(1.0f, 1.0f, 0.0f, alpha * 0.8f));
                
                int dotSize = (int)(4 + alpha * 4);
                g2d.fillOval(x - dotSize/2, y - dotSize/2, dotSize, dotSize);
            }
        }
    }
    
    private void drawStatistics(Graphics2D g2d, int width, int height) {
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.PLAIN, 9));
        
        int y = height + 45;
        g2d.drawString("Trails: " + som.getWarpTrailCount(), 5, y);
        
        double[] repr = som.getRepresentation();
        y += 12;
        g2d.drawString(String.format("Entropy: %.2f", repr[2]), 5, y);
    }
}

// Meta-SOM visualization panel
class MetaSOMPanel extends JPanel {
    private MetaSOM metaSOM;
    private Map<BaseSOM, int[]> positions;
    private final Object dataLock = new Object();
    
    public MetaSOMPanel() {
        setPreferredSize(new Dimension(200, 220));
        setBackground(Color.BLACK);
        setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
    }
    
    public void updateMetaSOM(MetaSOM metaSOM) {
        synchronized (dataLock) {
            this.metaSOM = metaSOM;
            this.positions = metaSOM != null ? metaSOM.getBaseSOMPositions() : null;
        }
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        synchronized (dataLock) {
            int width = getWidth();
            int height = getHeight() - 30;
            int gridSize = Math.min(width, height) - 20;
            int cellSize = gridSize / 10; // 10x10 meta-SOM grid
            int startX = (width - gridSize) / 2;
            int startY = 25 + (height - gridSize) / 2;
            
            // Draw title
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            FontMetrics fm = g2d.getFontMetrics();
            String title = "Meta-SOM";
            int titleWidth = fm.stringWidth(title);
            g2d.drawString(title, (width - titleWidth) / 2, 18);
            
            // Draw grid
            g2d.setColor(new Color(50, 50, 50));
            for (int i = 0; i <= 10; i++) {
                // Vertical lines
                g2d.drawLine(startX + i * cellSize, startY, startX + i * cellSize, startY + gridSize);
                // Horizontal lines
                g2d.drawLine(startX, startY + i * cellSize, startX + gridSize, startY + i * cellSize);
            }
            
            // Draw SOM positions
            if (positions != null) {
                Color[] colors = {
                    new Color(255, 107, 107), // Red - Euclidean
                    new Color(78, 205, 196),  // Teal - Manhattan
                    new Color(69, 183, 209),  // Blue - Cosine
                    new Color(249, 202, 36),  // Yellow - Chebyshev
                    new Color(108, 92, 231)   // Purple - Minkowski
                };
                
                int colorIndex = 0;
                for (Map.Entry<BaseSOM, int[]> entry : positions.entrySet()) {
                    int[] pos = entry.getValue();
                    if (pos[0] >= 0 && pos[0] < 10 && pos[1] >= 0 && pos[1] < 10) {
                        int x = startX + pos[0] * cellSize + cellSize / 2;
                        int y = startY + pos[1] * cellSize + cellSize / 2;
                        
                        g2d.setColor(colors[colorIndex % colors.length]);
                        g2d.fillOval(x - 6, y - 6, 12, 12);
                        g2d.setColor(Color.WHITE);
                        g2d.setStroke(new BasicStroke(2));
                        g2d.drawOval(x - 6, y - 6, 12, 12);
                        
                        // Draw label
                        g2d.setFont(new Font("Arial", Font.BOLD, 8));
                        String label = entry.getKey().getMetricName().substring(0, 1);
                        FontMetrics labelFm = g2d.getFontMetrics();
                        int labelWidth = labelFm.stringWidth(label);
                        g2d.drawString(label, x - labelWidth/2, y + 3);
                    }
                    colorIndex++;
                }
            }
            
            // Draw statistics
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.PLAIN, 9));
            int y = height + 45;
            g2d.drawString("Meta-Organization", 5, y);
        }
    }
}

// Multi-panel visualization combining 3D and individual SOMs
class MultiPanelVisualization extends JPanel {
    private SOMVisualizationPanel mainPanel;
    private List<IndividualSOMPanel> somPanels;
    public JPanel rightPanel;
    private MetaSOMPanel metaPanel;
    private boolean showIndividualSOMs = true;
    public void rebuild() {
    	
    	
        removeAll();     // clear all subpanels or nodes
        revalidate();    // force layout manager to recalc
        repaint();       // draw again
    }

    public MultiPanelVisualization() {
        setLayout(new BorderLayout());
        
        // Create main 3D visualization
        mainPanel = new SOMVisualizationPanel();
        
        // Create right panel for individual SOMs
        rightPanel = new JPanel();
        rightPanel.setLayout(new GridLayout(3, 2, 5, 5));
        rightPanel.setBackground(Color.BLACK);
        rightPanel.setPreferredSize(new Dimension(420, 0));
        
        // Initialize individual SOM panels
        somPanels = new ArrayList<>();
        Color[] colors = {
            new Color(255, 107, 107), // Red - Euclidean
            new Color(78, 205, 196),  // Teal - Manhattan
            new Color(69, 183, 209),  // Blue - Cosine
            new Color(249, 202, 36),  // Yellow - Chebyshev
            new Color(108, 92, 231)   // Purple - Minkowski
        };
        String[] names = {"Euclidean", "Manhattan", "Cosine", "Chebyshev", "Minkowski"};
        
        for (int i = 0; i < 5; i++) {
            IndividualSOMPanel panel = new IndividualSOMPanel(null, names[i], colors[i]);
            somPanels.add(panel);
            rightPanel.add(panel);
        }
        
        // Add meta-SOM visualization panel
        metaPanel = new MetaSOMPanel();
        rightPanel.add(metaPanel);
        
        // Layout
        add(mainPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
    }
    
    public void updateVisualization(List<BaseSOM> baseSOMList, MetaSOM metaSOM) {
        // Update main 3D panel
    	SwingUtilities.invokeLater(() ->
    	mainPanel.updateVisualization(baseSOMList, metaSOM)
    			);
        // Update individual SOM panels
        if (showIndividualSOMs && baseSOMList.size() >= somPanels.size()) {
            for (int i = 0; i < somPanels.size(); i++) {
                somPanels.get(i).updateSOM(baseSOMList.get(i));
                somPanels.get(i).repaint();;
            }
        }
        
        // Update meta-SOM panel
        metaPanel.updateMetaSOM(metaSOM);
        metaPanel.repaint();
    }
    
    public void toggleIndividualSOMs() {
        showIndividualSOMs = !showIndividualSOMs;
        rightPanel.setVisible(showIndividualSOMs);
        revalidate();
    }
    
    public SOMVisualizationPanel getMainPanel() {
        return mainPanel;
    }
    
    public MetaSOMPanel getMetaPanel() {
        return metaPanel;
    }
}