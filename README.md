# test4 — SOM Layer 2

The **second neural layer** of the [Distributed Chess System](../). Receives test3's
intermediate vectors, reorganizes them through its own reversible SOM hierarchy, and — checking
against a **library of legal chess moves** — emits a **move vector** (as JSON).

## What it does

- **Reorganize** — another `BaseSOM` / `MetaSOM` / `ReversibleSOMHierarchy` stack, with a
  Swing visualizer (`ReversableSOMVisualizer`, `SOMVisualizationPanel`, `IndividualSOMPanel`).
- **Constrain to legal moves** — `LegalMoveLibrary` (~500 lines) limits the output to moves
  that actually exist on a chess board, so the layer emits *playable* candidates, not arbitrary
  vectors.
- **Emit** — output is written as JSON via `org.json`.

Where test3 organizes *what is seen*, test4 organizes *what may be done*.

## Dependencies

- **JDK 17+**, **Maven**.
- `org.json:json` (JSON I/O).
- Sibling modules declared in `pom.xml`: **`test3`** and **`SOM`** — these are local Maven
  artifacts. If a clean build can't resolve them, `mvn install` those sibling projects first
  (or build from a parent reactor that includes them).

## Build & run

```bash
# (if needed) install siblings first:  cd ../test3 && mvn install
mvn compile

# run the reversible-SOM visualizer
java -cp target/classes test4.ReversableSOMVisualizer
```

## Structure

```
test4/
├── pom.xml                        depends on org.json + sibling test3 / SOM
├── som/                           SOM assets
└── src/main/java/test4/
    ├── BaseSOM.java / MetaSOM.java                    the SOM levels
    ├── ReversibleSOMHierarchy.java                    invertible hierarchy
    ├── ReversableSOMVisualizer.java                   viewer  [main]
    ├── LegalMoveLibrary.java                          legal chess-move constraints
    └── SOMVisualizationPanel.java / IndividualSOMPanel.java   Swing rendering
```

---

*Part of the Distributed Chess System: its output (move) vectors feed the VectorServer relay.*
