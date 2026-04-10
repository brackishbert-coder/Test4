package test4;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import game.BoardUtils;
import game.VectorMoveValidator;
import game.tile;
import vectorization.vector;

public class ReversableSOMVisualizer extends JFrame {

	private static final long serialVersionUID = 1L;
	private static ReversibleSOMHierarchy whiteSomHierarchy;
	private static Timer updateTimer;
	private static ClientVectorizer clientVectorizer;
	private static VectorServer vectorServer;
	private static TileListener tileListener;
	static double exploration = 0.0; // 0.0 = no wandering, 1.0 = full chaos
	private static MultiPanelVisualization whiteMultiPanel;
	private static String homePath = "/home/wes/tv/test4/som/";
	private static ReversibleSOMHierarchy blackSomHierarchy;
	private static MultiPanelVisualization blackMultiPanel;
	private static TurnListener turnListener;

	public static void main(String[] args) {
		ReversableSOMVisualizer app = new ReversableSOMVisualizer();
		app.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		app.setSize(1200, 800);
		app.setVisible(true);
	}


	public ReversableSOMVisualizer() {
		tileListener = new TileListener();
		new Thread(tileListener).start();
		vectorServer = new VectorServer();
		new Thread(vectorServer).start();
		clientVectorizer = new ClientVectorizer();
		Thread thread = new Thread(clientVectorizer);
		thread.start();
		turnListener = new TurnListener();
		new Thread(turnListener).start();
		whiteSomHierarchy = new ReversibleSOMHierarchy(homePath,"white");
		blackSomHierarchy = new ReversibleSOMHierarchy(homePath,"black");
		

		whiteMultiPanel = new MultiPanelVisualization();
		blackMultiPanel = new MultiPanelVisualization();
		JTabbedPane comp = new JTabbedPane();
		comp.add("WHITE",whiteMultiPanel);
		comp.add("BLACK",blackMultiPanel);
		add(comp, BorderLayout.CENTER);

		// Setup update timer
		updateTimer = new Timer(5, e -> updateSystem());
		updateTimer.start();
		updateTimer = new Timer(50, e -> SwingUtilities.invokeLater(() -> repaintPanels()));
		updateTimer.start();
		// Request focus for key events
		whiteMultiPanel.getMainPanel().requestFocusInWindow();
		blackMultiPanel.getMainPanel().requestFocusInWindow();
		try {
			whiteSomHierarchy.getMetaSOM().loadFromJson(homePath,"meta_som_reversible","white");
			blackSomHierarchy.getMetaSOM().loadFromJson(homePath,"meta_som_reversible","black");
		} catch (IOException e) {
			System.out.println("Failed to load Meta SOM: " + e.getMessage());
		}
		repaintPanels();
	}
	private static void repaintPanels() {
	    whiteMultiPanel.repaint();
	    blackMultiPanel.repaint();
	    blackMultiPanel.revalidate();
	    whiteMultiPanel.revalidate();
	}

	static double[] explore(double[] vector, double exploration) {
		double[] out = new double[vector.length];
		for (int i = 0; i < vector.length; i++) {
			double noise = (Math.random() - 0.5) * 2.0 * exploration;
			out[i] = clamp(vector[i] + noise, 0.0, 1.0);
		}
		return out;
	}

	static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}

	// Helper to normalize coordinates
	private static double norm(double index) {
		return (index + 0.5) / 8; // center of the tile
	}

	// Helper to build normalized move vector
	private static double[] normalizedMove(double sx, double sy, double dx, double dy) {
		return new double[] { norm(sx), norm(sy), norm(dx), norm(dy) };
	}
	public void rebuildAllVisualNodes() {
	    System.out.println("Rebuilding Visual SOM Node Display...");

	    // Clear all previous state
	    whiteSomHierarchy.clearVisualization();
	    blackSomHierarchy.clearVisualization();

	    whiteSomHierarchy.buildVisualizationFromLoaded();
	    blackSomHierarchy.buildVisualizationFromLoaded();
	   

	    whiteMultiPanel.rebuild();
	    blackMultiPanel.rebuild();

	    repaint();
	}

	private static void updateSystem() {

			if (tileListener.getTiles() != null && tileListener.getTiles().size()>0) {
				synchronized (tileListener.getTiles()) {
					
					System.out.println("A)Current board snapshot:");
					for (int y = 0; y < 8; y++) {
						for (int x = 0; x < 8; x++) {
							System.out.print(tileListener.getTiles().get(y*8+x).getPiece() + " ");
						}
						System.out.println();
					}
				}
				double[] input = clientVectorizer.getFeatureVector();
				System.out.println("input send: "+input[0]+" "+input[1]+" "+input[2]+" "+input[3]+" Legal: "+VectorMoveValidator.isLegalMove(input,false)+(turnListener.getTurn()?" is White "+BoardUtils.isWhitePiece(input) :" is Black "+BoardUtils.isBlackPiece(input) ) );
				
				
				if(BoardUtils.isWhitePiece(input)) {
				double reward = VectorMoveValidator.evaluateMove(input, WIDTH, HEIGHT, false);
				
					double[] metaInput = buildMetaInput(input, reward);
					whiteSomHierarchy.processInputAsync(metaInput, reward);

					whiteSomHierarchy.getMetaSOM().train(metaInput, 0.1, 2.0);

					try {
						whiteSomHierarchy.getMetaSOM().saveToJson(homePath,"meta_som_reversible","white");
						blackSomHierarchy.getMetaSOM().saveToJson(homePath,"meta_som_reversible","black");
					} catch (IOException e) {
						System.out.println("Failed to save Meta SOM: " + e.getMessage());
					}

					System.out.println("white metaInput send: "+metaInput[0]+" "+metaInput[1]+" "+metaInput[2]+" "+metaInput[3]+" Legal: "+VectorMoveValidator.isLegalMove(metaInput,false)+(turnListener.getTurn() ?" is White "+BoardUtils.isWhitePiece(metaInput) :" is Black "+BoardUtils.isBlackPiece(metaInput) ) );
					
					System.out.println("WHITE");
					VectorServerQueue.push(metaInput);
				}else if(BoardUtils.isBlackPiece(input)) {
					double reward = VectorMoveValidator.evaluateMove(input, WIDTH, HEIGHT, false);
					
					double[] metaInput = buildMetaInput(input, reward);
					blackSomHierarchy.processInputAsync(metaInput, reward);

					blackSomHierarchy.getMetaSOM().train(metaInput, 0.1, 2.0);

					
					try {
						whiteSomHierarchy.getMetaSOM().saveToJson(homePath,"meta_som_reversible","white");
						blackSomHierarchy.getMetaSOM().saveToJson(homePath,"meta_som_reversible","black");
					} catch (IOException e) {
						System.out.println("Failed to save Meta SOM: " + e.getMessage());
					}
					System.out.println("black metaInput send: "+metaInput[0]+" "+metaInput[1]+" "+metaInput[2]+" "+metaInput[3]+" Legal: "+VectorMoveValidator.isLegalMove(metaInput,false)+(BoardUtils.isWhiteTurn?" is White "+BoardUtils.isWhitePiece(metaInput) :" is Black "+BoardUtils.isBlackPiece(metaInput) ) );
					System.out.println("BLACK");
					
					VectorServerQueue.push(metaInput);
				}
				
				
			}
			

			
			
			
			
			
			
			
			
			SwingUtilities.invokeLater(() ->
			blackMultiPanel.updateVisualization(blackSomHierarchy.getBaseSOMList(), blackSomHierarchy.getMetaSOM())
			);
			SwingUtilities.invokeLater(() ->
			whiteMultiPanel.updateVisualization(whiteSomHierarchy.getBaseSOMList(), whiteSomHierarchy.getMetaSOM())
	);
	}
	public void loadEverythingAndRefresh(String dir, String fileName, String uName,ReversibleSOMHierarchy hierarchy) {
	    try {
	        System.out.println("\n=== LOADING META + BASE SOMs ===");

	        // 1 — Load MetaSOM from disk
	        MetaSOM loadedMeta = MetaSOM.loadFromJson(dir, fileName, uName);
	        System.out.println("MetaSOM loaded.");

	        // 2 — Load BaseSOMs from disk (handled in MetaSOM.load…)
	        // Now update the hierarchy with the new Meta + Base SOMs
	        hierarchy.setMetaSOM(loadedMeta);
	        hierarchy.setBaseSOMs(loadedMeta.getBaseSOMList());
	        System.out.println("Hierarchy updated with loaded SOMs.");

	        // 3 — Rebuild all UI panels to point to the *new* SOM instances
	        rebuildPanelsFromHierarchy();
	        System.out.println("Visualizer panels rebuilt.");

	        // 4 — Force update + repaint
	        revalidate();
	        repaint();

	        System.out.println("=== LOAD + REFRESH COMPLETE ===");

	    } catch (Exception e) {
	        e.printStackTrace();
	        System.err.println("ERROR: Unable to load and refresh SOMs.");
	    }
	}
	public void rebuildPanelsFromHierarchy() {
	    System.out.println("Rebuilding SOM panels…");
	    whiteMultiPanel.revalidate();
		blackMultiPanel.revalidate();
		Color[] colors = {
	            new Color(255, 107, 107), // Red - Euclidean
	            new Color(78, 205, 196),  // Teal - Manhattan
	            new Color(69, 183, 209),  // Blue - Cosine
	            new Color(249, 202, 36),  // Yellow - Chebyshev
	            new Color(108, 92, 231)   // Purple - Minkowski
	        };
	        String[] names = {"Euclidean", "Manhattan", "Cosine", "Chebyshev", "Minkowski"};
	        
	    for (int i=0;i<= whiteSomHierarchy.getBaseSOMList().size()-1;i++) {
	        IndividualSOMPanel panel = new IndividualSOMPanel(whiteSomHierarchy.getBaseSOMList().get(i), names[i], colors[i]);
	        whiteMultiPanel.add(panel);
	    }
	    for (int i=0;i<= blackSomHierarchy.getBaseSOMList().size()-1;i++) {
	        IndividualSOMPanel panel = new IndividualSOMPanel(blackSomHierarchy.getBaseSOMList().get(i), names[i], colors[i]);
	        whiteMultiPanel.add(panel);
	    }
	    // If the MetaSOM has a panel:
	    

	    revalidate();
	    repaint();
	}

	public static double[] buildMetaInput(double[] rawInput, double reward) {
		double[] metaInput = new double[21];
		for (int i = 0; i < metaInput.length; i++) {
			metaInput[i] = 0.0;
		}

		for (int i = 0; i < rawInput.length && i < 20; i++) {
			metaInput[i] = rawInput[i];
		}

		metaInput[20] = reward; // ✅ append reward channel
		return metaInput;
	}

}
