package test4;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import game.BoardUtils;
import game.VectorMoveValidator;
import game.tile;

public class LegalMoveLibrary {

	private static final Random rand = new Random();
	private static boolean isStuck=false;
	// Remember the last move returned so we can avoid repeats
	private static double[] lastReturnedMove = null;

	// Utility to check if two normalized moves are identical

	public static void setBoard(ArrayList<tile> tiles) {
	    BoardUtils.tiles = tiles;
	}




public static double[] getRandomValidMoveNormalizedSynced(boolean isWhite) {

    if (BoardUtils.tiles == null || BoardUtils.tiles.isEmpty())
        return new double[]{-1, -1, -1, -1};

    List<double[]> legal = new ArrayList<>();

    // ---------------------------------------------------
    // 1. Generate ALL legal moves on the board
    // ---------------------------------------------------
    for (tile from : BoardUtils.tiles) {

        char src = from.getPiece();
        if (src == ' ' || src == '.' || src == '\0') continue;

        boolean pieceIsWhite = Character.isUpperCase(src);
        if (pieceIsWhite != isWhite) continue;

        int fx = from.getColumn();
        int fy = from.getRow();

        // Generate pseudo-legal destinations
        List<int[]> candidates = LegalMoveLibrary.genAllPseudoMoves(src, fx, fy);

        for (int[] c : candidates) {
            int tx = c[0];
            int ty = c[1];

            if (!inBounds(tx, ty)) continue;

            // -----------------------------------------------
            // Check if move is legal under chess rules
            // (moving into check, pinned pieces, etc.)
            // -----------------------------------------------
            if (!VectorMoveValidator.isLegalMove(
                    new double[]{fx, fy, tx, ty},
                    true
            )) continue;

            // Normalize for output
            legal.add(new double[]{
                fx / 7.0,
                fy / 7.0,
                tx / 7.0,
                ty / 7.0
            });
        }
    }

    // ---------------------------------------------------
    // 2. No legal moves? -> stalemate / checkmate
    // ---------------------------------------------------
    if (legal.isEmpty()) {
        System.out.println("⚠ No legal moves for " + (isWhite ? "WHITE" : "BLACK"));
        return new double[]{-1, -1, -1, -1};
    }

    // ---------------------------------------------------
    // 3. Avoid repeating the last move if alternatives exist
    // ---------------------------------------------------
    if (lastReturnedMove != null && legal.size() > 1) {
        legal.removeIf(m -> sameMove(m, lastReturnedMove));
    }

    // If removal erased everything, restore full list
    if (legal.isEmpty()) {
        legal = getAllLegalMoves(isWhite);
    }

    // ---------------------------------------------------
    // 4. Choose random from remaining
    // ---------------------------------------------------
    double[] chosen = legal.get(rand.nextInt(legal.size()));
    lastReturnedMove = chosen;
    return chosen;
}


//Add this inside LegalMoveLibrary (e.g. near your other gen* methods)
private static List<int[]> genAllPseudoMoves(char src, int fx, int fy) {
 List<int[]> candidates = new ArrayList<>();

 switch (Character.toLowerCase(src)) {
     case 'p' -> {
         // uses your existing pawn move generator
         candidates.addAll(genPawnMoves(src, fx, fy));
     }
     case 'n' -> {
         // knight moves
         candidates.addAll(genKnightMoves(fx, fy));
     }
     case 'b' -> {
         // bishop-style diagonals
         candidates.addAll(genSlidingMoves(
             fx, fy,
             new int[][]{
                 { 1,  1},
                 { 1, -1},
                 {-1,  1},
                 {-1, -1}
             }
         ));
     }
     case 'r' -> {
         // rook-style orthogonals
         candidates.addAll(genSlidingMoves(
             fx, fy,
             new int[][]{
                 { 1,  0},
                 {-1,  0},
                 { 0,  1},
                 { 0, -1}
             }
         ));
     }
     case 'q' -> {
         // queen = rook + bishop
         candidates.addAll(genSlidingMoves(
             fx, fy,
             new int[][]{
                 { 1,  0}, {-1,  0}, { 0,  1}, { 0, -1},
                 { 1,  1}, { 1, -1}, {-1,  1}, {-1, -1}
             }
         ));
     }
     case 'k' -> {
         // king moves
         candidates.addAll(genKingMoves(fx, fy));
     }
     default -> {
         // unknown piece type, no moves
     }
 }

 return candidates;
}




// Mapped board-space legality
private static boolean isLegalBoardMove(int sx, int sy, int dx, int dy) {
    return VectorMoveValidator.isLegalMove(
            new double[]{sx, sy, dx, dy},
            true // board-space mode
    );
}


private static boolean sameMove(double[] a, double[] b) {
    return Math.abs(a[0] - b[0]) < 0.0001 &&
           Math.abs(a[1] - b[1]) < 0.0001 &&
           Math.abs(a[2] - b[2]) < 0.0001 &&
           Math.abs(a[3] - b[3]) < 0.0001;
}

private static List<double[]> getAllLegalMoves(boolean isWhite) {
    List<double[]> list = new ArrayList<>();
    for (tile t : BoardUtils.tiles) {
        char pc = t.getPiece();
        if (pc == ' ' || pc == '.' || pc == '\0') continue;
        boolean pWhite = Character.isUpperCase(pc);
        if (pWhite != isWhite) continue;

        int fx = t.getColumn(), fy = t.getRow();
        List<int[]> moves = LegalMoveLibrary.genAllPseudoMoves(pc, fx, fy);

        for (int[] m : moves) {
            if (VectorMoveValidator.isLegalMove(
                    new double[]{fx, fy, m[0], m[1]},
                    true)) {

                list.add(new double[]{
                    fx / 7.0, fy / 7.0,
                    m[0] / 7.0, m[1] / 7.0
                });
            }
        }
    }
    return list;
}


	private static final int BOARD_W = 8;
	private static final int BOARD_H = 8;

	private static boolean inBounds(int x, int y) {
	    return x >= 0 && x < BOARD_W && y >= 0 && y < BOARD_H;
	}

	private static char getPieceSafe(int x, int y) {
	    if (!inBounds(x, y)) return ' ';
	    tile t = BoardUtils.getTile(x, y,BOARD_W,BOARD_H);   // or BoardUtils.getTile(BoardUtils.tiles, x, y, BOARD_W, BOARD_H)
	    return (t == null) ? ' ' : t.getPiece();
	}
	public static boolean hasAnyLegalMoves(boolean isWhite) {
	    if (BoardUtils.tiles == null) return false;

	    for (tile from : BoardUtils.tiles) {
	        char src = from.getPiece();
	        if (src == ' ' || src == '.' || src == '\0') continue;

	        // color filter
	        boolean pieceIsWhite = Character.isUpperCase(src);
	        if (pieceIsWhite != isWhite) continue;

	        int fx = from.getColumn();
	        int fy = from.getRow();

	        // build pseudo-legal destinations for this piece
	        List<int[]> candidates = new ArrayList<>();
	        switch (Character.toLowerCase(src)) {
	            case 'p' -> candidates.addAll(genPawnMoves(src, fx, fy));
	            case 'n' -> candidates.addAll(genKnightMoves(fx, fy));
	            case 'b' -> candidates.addAll(genSlidingMoves(fx, fy, new int[][] {
	                    {1,1},{1,-1},{-1,1},{-1,-1}
	            }));
	            case 'r' -> candidates.addAll(genSlidingMoves(fx, fy, new int[][] {
	                    {1,0},{-1,0},{0,1},{0,-1}
	            }));
	            case 'q' -> candidates.addAll(genSlidingMoves(fx, fy, new int[][] {
	                    {1,0},{-1,0},{0,1},{0,-1},
	                    {1,1},{1,-1},{-1,1},{-1,-1}
	            }));
	            case 'k' -> candidates.addAll(genKingMoves(fx, fy));
	        }

	        // test each destination
	        for (int[] to : candidates) {
	            int tx = to[0], ty = to[1];
	            if (tx < 0 || tx > 7 || ty < 0 || ty > 7) continue;

	            tile toTile = BoardUtils.getTile(tx, ty, 8);
	            if (toTile == null) continue;

	            // normalize to 0..1 vector
	            double[] moveVec = new double[] {
	                fx / 7.0, fy / 7.0,
	                tx / 7.0, ty / 7.0
	            };

	            // If your VectorMoveValidator already enforces blocking, captures, etc.,
	            // this is enough to declare at least one legal move exists.
	            if (VectorMoveValidator.isLegalMove(moveVec, false)) {
	                return true;
	            }

	            // (Optional) If you later add "no self-check" filtering, simulate here
	            // and ensure the move doesn't leave your own king in check before returning true.
	        }
	    }
	    return false;
	}


	public static List<int[]> genPawnMoves(char src, int x, int y) {
	    List<int[]> m = new ArrayList<>();
	    int dir = Character.isUpperCase(src) ? -1 : 1;
	    m.add(new int[]{x, y + dir});
	    // first move double step
	    if ((dir == -1 && y == 6) || (dir == 1 && y == 1))
	        m.add(new int[]{x, y + 2 * dir});
	    // captures
	    m.add(new int[]{x + 1, y + dir});
	    m.add(new int[]{x - 1, y + dir});
	    return m;
	}

	public static List<int[]> genKnightMoves(int x, int y) {
	    int[][] d = {
	        {1,2},{2,1},{-1,2},{-2,1},{1,-2},{2,-1},{-1,-2},{-2,-1}
	    };
	    List<int[]> m = new ArrayList<>();
	    for (int[] v : d) m.add(new int[]{x + v[0], y + v[1]});
	    return m;
	}

	public static List<int[]> genKingMoves(int x, int y) {
	    List<int[]> m = new ArrayList<>();
	    for (int dx=-1; dx<=1; dx++)
	        for (int dy=-1; dy<=1; dy++)
	            if (dx != 0 || dy != 0)
	                m.add(new int[]{x + dx, y + dy});
	    return m;
	}

	public static List<int[]> genSlidingMoves(int x, int y, int[][] dirs) {
	    List<int[]> m = new ArrayList<>();
	    for (int[] d : dirs) {
	        int dx = d[0], dy = d[1];
	        for (int step = 1; step < 8; step++) {
	            int nx = x + dx * step, ny = y + dy * step;
	            if (!inBounds(nx, ny)) break;
	            m.add(new int[]{nx, ny});
	            char piece = BoardUtils.get(nx, ny);
	            if (piece != ' ' && piece != '.') break; // stop sliding through pieces
	        }
	    }
	    return m;
	}

	
	
	
	
	private static double[] normalizeMove(tile from, tile to) {
		double sx = from.getColumn() / 7.0;
		double sy = 1-(from.getRow()) / 7.0;
		double dx = to.getColumn() / 7.0;
		double dy = 1-(to.getRow()) / 7.0;
		return new double[] { sx, sy, dx, dy };
	}

	public static double[] getRandomValidWhiteMoveNormalized() {
		return getRandomValidMoveNormalized(true);
	}

	public static double[] getRandomValidBlackMoveNormalized() {
		return getRandomValidMoveNormalized(false);
	}

	/**
	 * Generate a 256×256 SOM seed: 50% legal white moves, 50% legal black moves.
	 */
	public static double[] generateMixedMoveSeeds() {
		int half = 256 / 2;

		double[] seeds = new double[256];

		double[] rWhiteMoveNorm = getRandomValidWhiteMoveNormalized();

		for (int i = 0; i < half; i++) {
			seeds[i] = rWhiteMoveNorm[i];
		}

		double[] ds = getRandomValidBlackMoveNormalized();
		for (int i = half; i < 256; i++) {
			seeds[i] = ds[i - half];
		}

		return seeds;
	}

	// === INTERNAL HELPERS ===

	public static double[] getRandomValidMoveNormalized(boolean isWhite) {
		double[] vec = new double[256];
		boolean valid = false;
		int sx = 0, sy = 0, dx = 0, dy = 0;

		while (!valid) {
			sx = rand.nextInt(8);
			sy = isWhite ? 6 + rand.nextInt(2) : rand.nextInt(2); // only ranks with pieces

			char piece = getPieceAtStart(isWhite, sx, sy);
			if (piece == ' ' || (isWhite && Character.isLowerCase(piece)) || (!isWhite && Character.isUpperCase(piece)))
				continue;

			int[] move = getRandomLegalMoveForPiece(piece, sx, sy, isWhite);
			if (move == null)
				continue;
			dx = move[0];
			dy = move[1];
			if (dx < 0 || dx > 7 || dy < 0 || dy > 7)
				continue;

			// avoid “no-move” vectors
			if (sx == dx && sy == dy)
				continue;

			char dstPiece = getPieceAtStart(isWhite, dx, dy);
			if (Character.isUpperCase(piece) && Character.isUpperCase(dstPiece))
				continue;
			if (Character.isLowerCase(piece) && Character.isLowerCase(dstPiece))
				continue;

			vec[0] = sx / 7.0;
			vec[1] = sy / 7.0;
			vec[2] = dx / 7.0;
			vec[3] = dy / 7.0;

			valid = true;
		}

		for (int i = 4; i < 256; i++)
			vec[i] = rand.nextDouble();

		return vec;
	}

	private static char getPieceAtStart(boolean isWhite, int x, int y) {
		if (isWhite) {
			if (y == 6)
				return 'P';
			if (y == 7) {
				char[] back = { 'R', 'N', 'B', 'Q', 'K', 'B', 'N', 'R' };
				return back[x];
			}
		} else {
			if (y == 1)
				return 'p';
			if (y == 0) {
				char[] back = { 'r', 'n', 'b', 'q', 'k', 'b', 'n', 'r' };
				return back[x];
			}
		}
		return ' ';
	}

	private static int[] getRandomLegalMoveForPiece(char piece, int sx, int sy, boolean isWhite) {
		switch (Character.toLowerCase(piece)) {
		case 'p':
			return randomPawnMove(sx, sy, isWhite);
		case 'n':
			return randomKnightMove(sx, sy);
		case 'b':
			return randomBishopMove(sx, sy);
		case 'r':
			return randomRookMove(sx, sy);
		case 'q':
			return randomQueenMove(sx, sy);
		case 'k':
			return randomKingMove(sx, sy);
		default:
			return null;
		}
	}

	private static int[] randomPawnMove(int sx, int sy, boolean isWhite) {
		int dir = isWhite ? -1 : 1;
		int[][] options = { { sx, sy + dir }, // single forward
				{ sx, sy + 2 * dir }, // double move from start
				{ sx - 1, sy + dir }, // diagonal capture left
				{ sx + 1, sy + dir } // diagonal capture right
		};
		return options[rand.nextInt(options.length)];
	}

	private static int[] randomKnightMove(int sx, int sy) {
		int[][] deltas = { { 1, 2 }, { 2, 1 }, { -1, 2 }, { -2, 1 }, { 1, -2 }, { 2, -1 }, { -1, -2 }, { -2, -1 } };
		int[] d = deltas[rand.nextInt(deltas.length)];
		return new int[] { sx + d[0], sy + d[1] };
	}

	private static int[] randomBishopMove(int sx, int sy) {
		int dist = 1 + rand.nextInt(7);
		int dx = sx + (rand.nextBoolean() ? dist : -dist);
		int dy = sy + (rand.nextBoolean() ? dist : -dist);
		return new int[] { dx, dy };
	}

	private static int[] randomRookMove(int sx, int sy) {
		int dist = 1 + rand.nextInt(7);
		if (rand.nextBoolean())
			return new int[] { sx, sy + (rand.nextBoolean() ? dist : -dist) };
		else
			return new int[] { sx + (rand.nextBoolean() ? dist : -dist), sy };
	}

	private static int[] randomQueenMove(int sx, int sy) {
		return rand.nextBoolean() ? randomRookMove(sx, sy) : randomBishopMove(sx, sy);
	}

	private static int[] randomKingMove(int sx, int sy) {
		int dx = sx + rand.nextInt(3) - 1;
		int dy = sy + rand.nextInt(3) - 1;
		return new int[] { dx, dy };
	}






	public static boolean isStuck() {
		return isStuck;
	}
}
