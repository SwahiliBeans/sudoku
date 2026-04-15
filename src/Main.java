import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static final int SIZE = 9;
    private static final int BOX = 3;
    private static final String DEFAULT_PUZZLE_FILE = "puzzle1.txt";

    public static void main(String[] args) {
        String filePath;
        String mode;
        Scanner scanner = new Scanner(System.in);

        mode = promptMode(scanner);
        try {
            filePath = resolvePuzzlePath(DEFAULT_PUZZLE_FILE);
            int[][] grid = readPuzzle(filePath);
            System.out.println("Input:");
            printGrid(grid);
            long start = System.nanoTime();
            boolean solved = mode.equals("naive")
                    ? solveNaive(grid)
                    : solveSmart(grid);
            long end = System.nanoTime();
            System.out.println();
            System.out.println("Mode: " + mode);
            if (solved) {
                System.out.println("Solved:");
                printGrid(grid);
            } else {
                System.out.println("No solution found.");
            }
            System.out.println("Time (ms): " + ((end - start) / 1_000_000.0));
        } catch (IOException e) {
            System.out.println("Could not read file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid puzzle format: " + e.getMessage());
        }
    }
    private static String resolvePuzzlePath(String fileName) throws IOException {
        Path[] candidates = new Path[] {
                Path.of(fileName),
                Path.of("..", fileName),
                Path.of("sudoku", fileName)
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
        }
        throw new IOException("Cannot find " + fileName + " in current directory, parent directory, or sudoku directory.");
    }
    private static String promptMode(Scanner scanner) {
        while (true) {
            System.out.print("Choose mode (naive/smart): ");
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.equals("1") || input.equals("naive")) {
                return "naive";
            }
            if (input.equals("2") || input.equals("smart")) {
                return "smart";
            }
            System.out.println("Type naive or smart");
        }
    }
    private static int[][] readPuzzle(String filePath) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
        if (lines.size() < SIZE) {
            throw new IllegalArgumentException("Need at least 9 lines of puzzle digits");
        }
        int start = lines.size() == SIZE ? 0 : 1;
        if (lines.size() - start != SIZE) {
            throw new IllegalArgumentException("Puzzle must have exactly 9 rows of digits");
        }
        int[][] grid = new int[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            String row = lines.get(start + r);
            if (row.length() != SIZE) {
                throw new IllegalArgumentException("Row " + (r + 1) + " must have 9 digits");
            }
            for (int c = 0; c < SIZE; c++) {
                char ch = row.charAt(c);
                if (ch < '0' || ch > '9') {
                    throw new IllegalArgumentException("Row " + (r + 1) + " has non-digit characters");
                }
                grid[r][c] = ch - '0';
            }
        }
        return grid;
    }
    private static boolean solveNaive(int[][] grid) {
        int[] cell = findFirstEmpty(grid);
        if (cell == null) {
            return true;
        }
        int row = cell[0];
        int col = cell[1];
        for (int value = 1; value <= SIZE; value++) {
            if (isValid(grid, row, col, value)) {
                grid[row][col] = value;
                if (solveNaive(grid)) {
                    return true;
                }
                grid[row][col] = 0;
            }
        }
        return false;
    }
    private static boolean solveSmart(int[][] grid) {
        int[] cell = findMrvCell(grid);
        if (cell == null) {
            return true;
        }
        int row = cell[0];
        int col = cell[1];
        List<Integer> domain = getDomain(grid, row, col);
        if (domain.isEmpty()) {
            return false;
        }
        List<Integer> orderedValues = orderByLcv(grid, row, col, domain);
        for (int value : orderedValues) {
            grid[row][col] = value;
            if (forwardCheck(grid) && solveSmart(grid)) {
                return true;
            }
            grid[row][col] = 0;
        }
        return false;
    }
    private static int[] findFirstEmpty(int[][] grid) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c] == 0) {
                    return new int[] { r, c };
                }
            }
        }
        return null;
    }
    private static int[] findMrvCell(int[][] grid) {
        int bestSize = Integer.MAX_VALUE;
        int[] best = null;

        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c] == 0) {
                    int domainSize = getDomain(grid, r, c).size();
                    if (domainSize < bestSize) {
                        bestSize = domainSize;
                        best = new int[] { r, c };
                    }
                }
            }
        }
        return best;
    }
    private static List<Integer> getDomain(int[][] grid, int row, int col) {
        List<Integer> domain = new ArrayList<>();
        for (int value = 1; value <= SIZE; value++) {
            if (isValid(grid, row, col, value)) {
                domain.add(value);
            }
        }
        return domain;
    }
    private static List<Integer> orderByLcv(int[][] grid, int row, int col, List<Integer> domain) {
        List<ValueScore> scores = new ArrayList<>();
        for (int value : domain) {
            grid[row][col] = value;
            int removed = countRemovedFromNeighbors(grid, row, col);
            grid[row][col] = 0;
            scores.add(new ValueScore(value, removed));
        }
        Collections.sort(scores);
        List<Integer> ordered = new ArrayList<>();
        for (ValueScore score : scores) {
            ordered.add(score.value);
        }
        return ordered;
    }
    private static int countRemovedFromNeighbors(int[][] grid, int row, int col) {
        int removed = 0;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c] == 0 && isNeighbor(row, col, r, c)) {
                    if (!isValid(grid, r, c, grid[row][col])) {
                        removed++;
                    }
                }
            }
        }
        return removed;
    }
    private static boolean isNeighbor(int r1, int c1, int r2, int c2) {
        if (r1 == r2 || c1 == c2) {
            return true;
        }
        int b1r = (r1 / BOX) * BOX;
        int b1c = (c1 / BOX) * BOX;
        int b2r = (r2 / BOX) * BOX;
        int b2c = (c2 / BOX) * BOX;
        return b1r == b2r && b1c == b2c;
    }
    private static boolean forwardCheck(int[][] grid) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c] == 0 && getDomain(grid, r, c).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
    private static boolean isValid(int[][] grid, int row, int col, int value) {
        for (int c = 0; c < SIZE; c++) {
            if (grid[row][c] == value) {
                return false;
            }
        }
        for (int r = 0; r < SIZE; r++) {
            if (grid[r][col] == value) {
                return false;
            }
        }
        int boxRow = (row / BOX) * BOX;
        int boxCol = (col / BOX) * BOX;
        for (int r = boxRow; r < boxRow + BOX; r++) {
            for (int c = boxCol; c < boxCol + BOX; c++) {
                if (grid[r][c] == value) {
                    return false;
                }
            }
        }
        return true;
    }
    private static void printGrid(int[][] grid) {
        for (int r = 0; r < SIZE; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < SIZE; c++) {
                sb.append(grid[r][c]);
                if (c < SIZE - 1) {
                    sb.append(' ');
                }
            }
            System.out.println(sb);
        }
    }
    private static class ValueScore implements Comparable<ValueScore> {
        final int value;
        final int removedCount;

        ValueScore(int value, int removedCount) {
            this.value = value;
            this.removedCount = removedCount;
        }
        @Override
        public int compareTo(ValueScore other) {
            return Integer.compare(this.removedCount, other.removedCount);
        }
    }
}
