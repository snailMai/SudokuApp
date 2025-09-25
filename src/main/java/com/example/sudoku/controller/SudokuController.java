package com.example.sudoku.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

// Java 8 兼容：使用 POJO 代替 record
class VerifyRequest {
    private List<List<Integer>> board;
    public VerifyRequest() {}
    public List<List<Integer>> getBoard() { return board; }
    public void setBoard(List<List<Integer>> board) { this.board = board; }
}

class VerifyResponse {
    private boolean ok;
    private String message;
    private List<List<Integer>> solution;

    public VerifyResponse() {}
    public VerifyResponse(boolean ok, String message, List<List<Integer>> solution) {
        this.ok = ok; this.message = message; this.solution = solution;
    }
    public static VerifyResponse ok(String message, List<List<Integer>> solution) {
        return new VerifyResponse(true, message, solution);
    }
    public static VerifyResponse fail(String message) {
        return new VerifyResponse(false, message, null);
    }
    public boolean isOk() { return ok; }
    public String getMessage() { return message; }
    public List<List<Integer>> getSolution() { return solution; }
}

@RestController
@RequestMapping(path = "/api/sudoku", produces = MediaType.APPLICATION_JSON_VALUE)
public class SudokuController {

    @PostMapping(path = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VerifyResponse> verify(@RequestBody VerifyRequest req) {
        try {
            int[][] grid = toGrid(req.getBoard());
            String checkMsg = basicCheck(grid);
            if (checkMsg != null) {
                return ResponseEntity.ok(VerifyResponse.fail(checkMsg));
            }
            int[][] copy = deepCopy(grid);
            boolean solvable = solve(copy);
            if (!solvable) {
                return ResponseEntity.ok(VerifyResponse.fail("当前盘面无解或矛盾"));
            }
            return ResponseEntity.ok(VerifyResponse.ok("校验通过，存在解", toList(copy)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(VerifyResponse.fail(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(VerifyResponse.fail("服务器错误"));
        }
    }

    @GetMapping("/sample/refresh")
    public ResponseEntity<Map<String, Object>> refreshSample() {
        // 这里简单返回两套内置示例之一；你也可以改为从库或服务拉取
        List<int[][]> samples = Arrays.asList(
                new int[][]{
                        {5,3,0,0,7,0,0,0,0},
                        {6,0,0,1,9,5,0,0,0},
                        {0,9,8,0,0,0,0,6,0},
                        {8,0,0,0,6,0,0,0,3},
                        {4,0,0,8,0,3,0,0,1},
                        {7,0,0,0,2,0,0,0,6},
                        {0,6,0,0,0,0,2,8,0},
                        {0,0,0,4,1,9,0,0,5},
                        {0,0,0,0,8,0,0,7,9}
                },
                new int[][]{
                        {0,0,0,2,6,0,7,0,1},
                        {6,8,0,0,7,0,0,9,0},
                        {1,9,0,0,0,4,5,0,0},
                        {8,2,0,1,0,0,0,4,0},
                        {0,0,4,6,0,2,9,0,0},
                        {0,5,0,0,0,3,0,2,8},
                        {0,0,9,3,0,0,0,7,4},
                        {0,4,0,0,5,0,0,3,6},
                        {7,0,3,0,1,8,0,0,0}
                }
        );
        int pick = (int)(System.currentTimeMillis() % samples.size());
        int[][] chosen = samples.get(pick);
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("board", toList(chosen));
        resp.put("message", "已返回新的示例题");
        return ResponseEntity.ok(resp);
    }

    private static int[][] toGrid(List<List<Integer>> board) {
        if (board == null || board.size() != 9) throw new IllegalArgumentException("board 必须为 9x9");
        int[][] g = new int[9][9];
        for (int r = 0; r < 9; r++) {
            List<Integer> row = board.get(r);
            if (row == null || row.size() != 9) throw new IllegalArgumentException("board 必须为 9x9");
            for (int c = 0; c < 9; c++) {
                Integer v = row.get(c);
                if (v == null) v = 0;
                if (v < 0 || v > 9) throw new IllegalArgumentException("数值必须在 0..9");
                g[r][c] = v;
            }
        }
        return g;
    }

    private static String basicCheck(int[][] g) {
        // 行、列、宫重复性检查（忽略 0）
        for (int r = 0; r < 9; r++) {
            boolean[] seen = new boolean[10];
            for (int c = 0; c < 9; c++) {
                int v = g[r][c];
                if (v == 0) continue;
                if (seen[v]) return "第" + (r + 1) + "行存在重复数";
                seen[v] = true;
            }
        }
        for (int c = 0; c < 9; c++) {
            boolean[] seen = new boolean[10];
            for (int r = 0; r < 9; r++) {
                int v = g[r][c];
                if (v == 0) continue;
                if (seen[v]) return "第" + (c + 1) + "列存在重复数";
                seen[v] = true;
            }
        }
        for (int br = 0; br < 3; br++) {
            for (int bc = 0; bc < 3; bc++) {
                boolean[] seen = new boolean[10];
                for (int r = br * 3; r < br * 3 + 3; r++) {
                    for (int c = bc * 3; c < bc * 3 + 3; c++) {
                        int v = g[r][c];
                        if (v == 0) continue;
                        if (seen[v]) return "第" + (br + 1) + "," + (bc + 1) + " 宫存在重复数";
                        seen[v] = true;
                    }
                }
            }
        }
        return null;
    }

    private static boolean solve(int[][] g) {
        int[] cell = findEmpty(g);
        if (cell == null) return true;
        int r = cell[0], c = cell[1];
        for (int v = 1; v <= 9; v++) {
            if (isValid(g, r, c, v)) {
                g[r][c] = v;
                if (solve(g)) return true;
                g[r][c] = 0;
            }
        }
        return false;
    }

    private static int[] findEmpty(int[][] g) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (g[r][c] == 0) return new int[]{r, c};
            }
        }
        return null;
    }

    private static boolean isValid(int[][] g, int r, int c, int v) {
        for (int i = 0; i < 9; i++) {
            if (g[r][i] == v || g[i][c] == v) return false;
        }
        int br = (r / 3) * 3, bc = (c / 3) * 3;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (g[br + i][bc + j] == v) return false;
            }
        }
        return true;
    }

    private static int[][] deepCopy(int[][] g) {
        int[][] cp = new int[9][9];
        for (int r = 0; r < 9; r++) System.arraycopy(g[r], 0, cp[r], 0, 9);
        return cp;
    }

    private static List<List<Integer>> toList(int[][] g) {
        List<List<Integer>> out = new ArrayList<>();
        for (int r = 0; r < 9; r++) {
            List<Integer> row = new ArrayList<>(9);
            for (int c = 0; c < 9; c++) row.add(g[r][c]);
            out.add(row);
        }
        return out;
    }
}


