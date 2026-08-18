package test4;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.IOException;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import game.BoardUtils;
import game.VectorMoveValidator;

public class ReversableSOMVisualizer extends JFrame {

	private static final long serialVersionUID = 1L;
	private static ReversibleSOMHierarchy whiteSomHierarchy;
	private static Timer updateTimer;
	private static ClientVectorizer clientVectorizer;
	private static VectorServer vectorServer;
	private static TileListener tileListener;
	static double exploration = 0.0; // 0.0 = no wandering, 1.0 = full chaos
	private static MultiPanelVisualization whiteMultiPanel;
	// SOM persistence dir, relative to the working directory (the test4 project dir
	// in dev; the bundled app dir once packaged, where som/ ships alongside). Was a
	// hard-coded /home/wes/tv/... absolute path that existed on no other machine and
	// didn't even match this checkout, so the meta SOM silently failed to load.
	private static String homePath = "som/";
	private static ReversibleSOMHierarchy blackSomHierarchy;
	private static MultiPanelVisualization blackMultiPanel;
	private static TurnListener turnListener;
	// When ON (default), the SOMs keep learning across games — the original design.
	// When OFF, both maps reset to fresh at the start of every new game. UI toggled.
	private static volatile boolean persistBetweenGames = true;
	private static boolean wasAtStart = true;
	private static String lastBoardSig = null;
	// The per-colour meta maps, rebuilt as adjudicating parliaments (5 witnesses each).
	private static final Parliament whiteParliament = new Parliament(5);
	private static final Parliament blackParliament = new Parliament(5);
	private static long lastGenMs = 0L;
	private static final long GEN_INTERVAL_MS = 120;
	// OFF by default: each side learns from the moves it actually plays. ON also
	// trains from the camera feature vector on port 5010 (the old pipeline).
	private static volatile boolean useCameraInput = false;
	// Per-tick console logging (board snapshot, input line). Off keeps it fast —
	// console I/O at the 5ms tick rate is a real bottleneck.
	private static final boolean VERBOSE = false;

	public static void main(String[] args) {
		try {
			ReversableSOMVisualizer app = new ReversableSOMVisualizer();
			app.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			app.setSize(1200, 800);
			app.setVisible(true);
		} catch (Throwable t) {
			// The constructor starts the tile/vector/turn listeners before it loads the
			// SOMs. Those threads are non-daemon, so a throw here used to leave the JVM
			// alive serving port 5020 from a half-built model - the board kept playing
			// and nothing upstream could tell that test4 had failed. Fail loudly instead.
			System.err.println("✖ test4 failed to start; shutting down so nothing plays "
					+ "against an unloaded model.");
			t.printStackTrace(System.err);
			System.exit(1);
		}
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

		// Control bar: persist-between-games toggle (checked = original behavior).
		JCheckBox persistBox = new JCheckBox("Persist SOM between games", persistBetweenGames);
		persistBox.setToolTipText("Checked: the maps keep learning across games. "
				+ "Unchecked: both maps reset to fresh at the start of every new game.");
		persistBox.addItemListener(e -> persistBetweenGames = persistBox.isSelected());
		JCheckBox cameraBox = new JCheckBox("Camera feature input", useCameraInput);
		cameraBox.setToolTipText("Off (default): each side learns from the moves it actually plays. "
				+ "On: also train from the camera feature vector on port 5010 (the old pipeline).");
		cameraBox.addItemListener(e -> useCameraInput = cameraBox.isSelected());
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		controls.add(persistBox);
		controls.add(cameraBox);

		controls.add(new JLabel("   noise"));
		JSlider noiseSlider = new JSlider(0, 150, 30);   // 0.000–0.150, default 0.030
		noiseSlider.setToolTipText("Exploration noise on the chosen move, scaled by how much the metrics disagree.");
		noiseSlider.addChangeListener(e -> {
			double v = noiseSlider.getValue() / 1000.0;
			whiteParliament.setNoise(v);
			blackParliament.setNoise(v);
		});
		controls.add(noiseSlider);

		controls.add(new JLabel("   vote softness"));
		JSlider tempSlider = new JSlider(0, 200, 60);    // 0.00–2.00, default 0.60
		tempSlider.setToolTipText("How loosely the parliament picks among supported moves. Higher is more exploratory, lower is more decisive.");
		tempSlider.addChangeListener(e -> {
			double v = tempSlider.getValue() / 100.0;
			whiteParliament.setTemperature(v);
			blackParliament.setTemperature(v);
		});
		controls.add(tempSlider);

		add(controls, BorderLayout.NORTH);

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
		} catch (IOException | RuntimeException e) {
			// RuntimeException matters as much as IOException here: a truncated or
			// otherwise malformed json raises org.json.JSONException, which extends
			// RuntimeException, so an IOException-only catch let it escape the
			// constructor and kill main() while the socket listeners kept serving.
			// Staying up untrained is survivable - the launcher gates the board on the
			// "MetaSOM load complete" line, which is not printed on this path, so
			// nothing downstream will start against an unloaded model.
			System.err.println("✖ Failed to load Meta SOM (continuing UNTRAINED): " + e);
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
				maybeResetOnNewGame(tileListener.getTiles());
				maybeSaveOnMove(tileListener.getTiles());

				// --- Parliament: the move now comes from the side-to-move's meta map ---
				long nowGen = System.currentTimeMillis();
				if (nowGen - lastGenMs >= GEN_INTERVAL_MS) {
					lastGenMs = nowGen;
					boolean whiteToMove = Boolean.TRUE.equals(turnListener.getTurn());
					Parliament parliament = whiteToMove ? whiteParliament : blackParliament;
					java.util.List<BaseSOM> witnesses =
							whiteToMove ? whiteSomHierarchy.getBaseSOMList() : blackSomHierarchy.getBaseSOMList();
					game.LegalMoveLibrary.setBoard((java.util.ArrayList<game.tile>) tileListener.getTiles()); // sync live board so legal moves are real
						double[] decided = parliament.decide(witnesses, whiteToMove, 8, 8);
						System.out.println("[PARL] turn=" + (whiteToMove ? "W" : "B")
								+ " tiles=" + tileListener.getTiles().size()
								+ " legal=" + game.LegalMoveLibrary.getAllLegalMovesSynced(whiteToMove).size()
								+ " decided=" + (decided == null ? "NONE" : (decided[0] + "," + decided[1] + "->" + decided[2] + "," + decided[3])));
					if (decided != null) {
						VectorServerQueue.push(decided);
							// Learn from our own play: the side to move trains on the move it chose.
							double playReward = VectorMoveValidator.evaluateMove(decided, 8, 8, false);
							double[] playMeta = buildMetaInput(decided, playReward);
							ReversibleSOMHierarchy learner = whiteToMove ? whiteSomHierarchy : blackSomHierarchy;
							learner.processInputAsync(playMeta, playReward);
							learner.getMetaSOM().train(playMeta, 0.1, 2.0);
					}
				}

				if (VERBOSE) synchronized (tileListener.getTiles()) {
					
					System.out.println("A)Current board snapshot:");
					for (int y = 0; y < 8; y++) {
						for (int x = 0; x < 8; x++) {
							System.out.print(tileListener.getTiles().get(y*8+x).getPiece() + " ");
						}
						System.out.println();
					}
				}
				double[] input = clientVectorizer.getFeatureVector();
				if (VERBOSE) System.out.println("input send: "+input[0]+" "+input[1]+" "+input[2]+" "+input[3]+" Legal: "+VectorMoveValidator.isLegalMove(input,false)+(turnListener.getTurn()?" is White "+BoardUtils.isWhitePiece(input) :" is Black "+BoardUtils.isBlackPiece(input) ) );
				
				
				if(useCameraInput && BoardUtils.isWhitePiece(input)) {
				double reward = VectorMoveValidator.evaluateMove(input, WIDTH, HEIGHT, false);
				
					double[] metaInput = buildMetaInput(input, reward);
					whiteSomHierarchy.processInputAsync(metaInput, reward);

					whiteSomHierarchy.getMetaSOM().train(metaInput, 0.1, 2.0);

					// Meta-SOM saving moved to saveSoms() — fired once per successful move

					System.out.println("white metaInput send: "+metaInput[0]+" "+metaInput[1]+" "+metaInput[2]+" "+metaInput[3]+" Legal: "+VectorMoveValidator.isLegalMove(metaInput,false)+(turnListener.getTurn() ?" is White "+BoardUtils.isWhitePiece(metaInput) :" is Black "+BoardUtils.isBlackPiece(metaInput) ) );
					
					System.out.println("WHITE");
					// move emission is the parliament's job now (see the generation block below)
				}else if(useCameraInput && BoardUtils.isBlackPiece(input)) {
					double reward = VectorMoveValidator.evaluateMove(input, WIDTH, HEIGHT, false);
					
					double[] metaInput = buildMetaInput(input, reward);
					blackSomHierarchy.processInputAsync(metaInput, reward);

					blackSomHierarchy.getMetaSOM().train(metaInput, 0.1, 2.0);

					
					// Meta-SOM saving moved to saveSoms() — fired once per successful move
					System.out.println("black metaInput send: "+metaInput[0]+" "+metaInput[1]+" "+metaInput[2]+" "+metaInput[3]+" Legal: "+VectorMoveValidator.isLegalMove(metaInput,false)+(BoardUtils.isWhiteTurn?" is White "+BoardUtils.isWhitePiece(metaInput) :" is Black "+BoardUtils.isBlackPiece(metaInput) ) );
					System.out.println("BLACK");
					
					// move emission is the parliament's job now (see the generation block below)
				}
				
				
			}
			

			
			
			
			
			
			
			
			
			SwingUtilities.invokeLater(() ->
			blackMultiPanel.updateVisualization(blackSomHierarchy.getBaseSOMList(), blackSomHierarchy.getMetaSOM())
			);
			SwingUtilities.invokeLater(() ->
			whiteMultiPanel.updateVisualization(whiteSomHierarchy.getBaseSOMList(), whiteSomHierarchy.getMetaSOM())
	);
	}

	/**
	 * Detects the start of a new game from the incoming board and, when persistence
	 * is OFF, resets both SOM hierarchies. A freshly reset board has all four middle
	 * ranks empty; the very first move of any game breaks that, so the transition
	 * back to "all middle ranks empty" marks a new game beginning.
	 */
	private static void maybeResetOnNewGame(java.util.List<game.tile> tiles) {
		boolean atStart = isStartingPosition(tiles);
		if (atStart && !wasAtStart) {
			if (!persistBetweenGames) {
				whiteSomHierarchy.reset();
				blackSomHierarchy.reset();
				System.out.println("[SOM] new game detected — maps RESET (persist OFF)");
			} else {
				System.out.println("[SOM] new game detected — maps KEPT (persist ON)");
			}
		}
		wasAtStart = atStart;
	}

	private static boolean isStartingPosition(java.util.List<game.tile> tiles) {
		if (tiles == null || tiles.size() < 64) return false;
		for (int y = 2; y <= 5; y++) {
			for (int x = 0; x < 8; x++) {
				char p = tiles.get(y * 8 + x).getPiece();
				if (p != ' ' && p != '.' && p != ' ') return false;
			}
		}
		return true;
	}

	/** Save both maps once, only when a move actually changes the board. */
	private static void maybeSaveOnMove(java.util.List<game.tile> tiles) {
		String sig = boardSignature(tiles);
		if (lastBoardSig != null && !sig.equals(lastBoardSig)) {
			saveSoms();   // the one board changed — a move landed — persist the maps
		}
		lastBoardSig = sig;
	}

	private static String boardSignature(java.util.List<game.tile> tiles) {
		if (tiles == null) return "";
		StringBuilder sb = new StringBuilder(tiles.size());
		for (game.tile t : tiles) sb.append(t.getPiece());
		return sb.toString();
	}

	private static void saveSoms() {
		try {
			whiteSomHierarchy.getMetaSOM().saveToJson(homePath, "meta_som_reversible", "white");
			blackSomHierarchy.getMetaSOM().saveToJson(homePath, "meta_som_reversible", "black");
		} catch (IOException e) {
			System.out.println("Failed to save Meta SOM: " + e.getMessage());
		}
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
