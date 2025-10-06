package com.example.sudoku.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

// 校验结果类
class CheckResult {
    private boolean valid;
    private String message;
    private List<String> errorPositions;
    private int errorNumber;
    
    public CheckResult(boolean valid, String message, List<String> errorPositions, int errorNumber) {
        this.valid = valid;
        this.message = message;
        this.errorPositions = errorPositions;
        this.errorNumber = errorNumber;
    }
    
    public static CheckResult valid() {
        return new CheckResult(true, null, null, 0);
    }
    
    public static CheckResult invalid(String message, List<String> errorPositions, int errorNumber) {
        return new CheckResult(false, message, errorPositions, errorNumber);
    }
    
    public boolean isValid() { return valid; }
    public String getMessage() { return message; }
    public List<String> getErrorPositions() { return errorPositions; }
    public int getErrorNumber() { return errorNumber; }
}

// Java 8 兼容：使用 POJO 代替 record
class VerifyRequest {
    private List<List<Integer>> board;
    private Map<String, Integer> boardData; // 新增：支持 {行_列: 值} 格式
    
    public VerifyRequest() {}
    public List<List<Integer>> getBoard() { return board; }
    public void setBoard(List<List<Integer>> board) { this.board = board; }
    public Map<String, Integer> getBoardData() { return boardData; }
    public void setBoardData(Map<String, Integer> boardData) { this.boardData = boardData; }
}

class VerifyResponse {
    private boolean ok;
    private String message;
    private List<List<Integer>> solution;
    private List<String> errorPositions; // 新增：错误位置列表
    private int errorNumber; // 新增：重复的数字

    public VerifyResponse() {}
    public VerifyResponse(boolean ok, String message, List<List<Integer>> solution) {
        this.ok = ok; this.message = message; this.solution = solution;
    }
    public VerifyResponse(boolean ok, String message, List<List<Integer>> solution, List<String> errorPositions, int errorNumber) {
        this.ok = ok; this.message = message; this.solution = solution; 
        this.errorPositions = errorPositions; this.errorNumber = errorNumber;
    }
    public static VerifyResponse ok(String message, List<List<Integer>> solution) {
        return new VerifyResponse(true, message, solution);
    }
    public static VerifyResponse fail(String message) {
        return new VerifyResponse(false, message, null);
    }
    public static VerifyResponse failWithPositions(String message, List<String> errorPositions, int errorNumber) {
        return new VerifyResponse(false, message, null, errorPositions, errorNumber);
    }
    public boolean isOk() { return ok; }
    public String getMessage() { return message; }
    public List<List<Integer>> getSolution() { return solution; }
    public List<String> getErrorPositions() { return errorPositions; }
    public int getErrorNumber() { return errorNumber; }
}

@RestController
@RequestMapping(path = "/api/sudoku", produces = MediaType.APPLICATION_JSON_VALUE)
public class SudokuController {
    
    private static final Logger logger = LoggerFactory.getLogger(SudokuController.class);

    @PostMapping(path = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VerifyResponse> verify(@RequestBody Map<String, Object> requestBody) {
        logger.info("=== 收到数独校验请求 ===");
        
        try {
            // 打印前端传来的原始数据
            logger.info("原始请求数据: {}", requestBody);
            
            int[][] grid;
            
            // 检查是否是直接的 Map<String, Integer> 格式 (如 {"1_1":1, "1_2":2})
            if (isDirectMapFormat(requestBody)) {
                logger.info("使用直接 Map 格式 (Map<String, Integer>)");
                Map<String, Integer> boardData = new HashMap<>();
                for (Map.Entry<String, Object> entry : requestBody.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (value instanceof Number) {
                        boardData.put(key, ((Number) value).intValue());
                        logger.info("  {}: {}", key, value);
                    }
                }
                logger.info("解析直接 Map 格式数据");
                grid = toGridFromMap(boardData);
            }
            // 检查是否是包装格式 (如 {"boardData": {"1_1":1, "1_2":2}})
            else if (requestBody.containsKey("boardData")) {
                logger.info("使用包装的 boardData 格式");
                Object boardDataObj = requestBody.get("boardData");
                if (boardDataObj instanceof Map) {
                    Map<String, Integer> boardData = new HashMap<>();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> boardDataMap = (Map<String, Object>) boardDataObj;
                    for (Map.Entry<String, Object> entry : boardDataMap.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        if (value instanceof Number) {
                            boardData.put(key, ((Number) value).intValue());
                            logger.info("  {}: {}", key, value);
                        }
                    }
                    logger.info("解析包装的 boardData 格式数据");
                    grid = toGridFromMap(boardData);
                } else {
                    logger.warn("boardData 字段格式错误");
                    return ResponseEntity.badRequest().body(VerifyResponse.fail("boardData 字段格式错误"));
                }
            }
            // 检查是否是旧的 board 格式 (如 {"board": [[5,3,0,...], ...]})
            else if (requestBody.containsKey("board")) {
                logger.info("使用旧的 board 格式 (List<List<Integer>>)");
                Object boardObj = requestBody.get("board");
                if (boardObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<List<Integer>> board = (List<List<Integer>>) boardObj;
                    for (int i = 0; i < board.size(); i++) {
                        logger.info("  第{}行: {}", i + 1, board.get(i));
                    }
                    logger.info("解析旧的 board 格式数据");
                    grid = toGrid(board);
                } else {
                    logger.warn("board 字段格式错误");
                    return ResponseEntity.badRequest().body(VerifyResponse.fail("board 字段格式错误"));
                }
            } else {
                logger.warn("请求数据格式不支持");
                return ResponseEntity.badRequest().body(VerifyResponse.fail("请求数据格式不支持"));
            }
            
            // 打印解析后的数独网格
            logger.info("解析后的数独网格:");
            printSudokuGrid(grid);
            
            logger.info("开始基础校验...");
            CheckResult checkResult = basicCheckWithPositions(grid);
            if (!checkResult.isValid()) {
                logger.warn("基础校验失败: {}", checkResult.getMessage());
                return ResponseEntity.badRequest().body(VerifyResponse.failWithPositions(
                    checkResult.getMessage(), 
                    checkResult.getErrorPositions(), 
                    checkResult.getErrorNumber()
                ));
            }
            logger.info("基础校验通过");
            
            logger.info("开始求解数独...");
            int[][] copy = deepCopy(grid);
            boolean solvable = solve(copy);
            if (!solvable) {
                logger.warn("数独求解失败: 当前盘面无解或矛盾");
                return ResponseEntity.ok(VerifyResponse.fail("当前盘面无解或矛盾"));
            }
            logger.info("数独求解成功");
            
            // 打印求解结果
            logger.info("求解结果:");
            printSudokuGrid(copy);
            
            logger.info("=== 数独校验完成，返回成功结果 ===");
            return ResponseEntity.ok(VerifyResponse.ok("校验通过，存在解", toList(copy)));
        } catch (IllegalArgumentException ex) {
            logger.error("参数错误: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(VerifyResponse.fail(ex.getMessage()));
        } catch (Exception ex) {
            logger.error("服务器内部错误", ex);
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

    private static int[][] toGridFromMap(Map<String, Integer> boardData) {
        int[][] g = new int[9][9]; // 默认全为0
        for (Map.Entry<String, Integer> entry : boardData.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            
            // 解析 "行_列" 格式的键
            String[] parts = key.split("_");
            if (parts.length != 2) {
                throw new IllegalArgumentException("键格式错误，应为 '行_列' 格式，如 '1_1'");
            }
            
            try {
                int row = Integer.parseInt(parts[0]) - 1; // 转换为0基索引
                int col = Integer.parseInt(parts[1]) - 1; // 转换为0基索引
                
                if (row < 0 || row >= 9 || col < 0 || col >= 9) {
                    throw new IllegalArgumentException("行列索引超出范围 (1-9)");
                }
                if (value == null || value < 1 || value > 9) {
                    throw new IllegalArgumentException("数值必须在 1-9 范围内");
                }
                
                g[row][col] = value;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("键格式错误，行列必须为数字");
            }
        }
        return g;
    }

    private static CheckResult basicCheckWithPositions(int[][] g) {
        logger.info("开始基础校验 - 检查行、列、九宫格的重复性");
        
        // 行重复性检查（忽略 0）
        logger.info("检查行重复性...");
        for (int r = 0; r < 9; r++) {
            boolean[] seen = new boolean[10];
            List<String> positions = new ArrayList<>();
            for (int c = 0; c < 9; c++) {
                int v = g[r][c];
                if (v == 0) continue;
                if (seen[v]) {
                    // 找到重复数字的所有位置
                    for (int c2 = 0; c2 < 9; c2++) {
                        if (g[r][c2] == v) {
                            positions.add((r + 1) + "_" + (c2 + 1));
                        }
                    }
                    logger.warn("第{}行存在重复数: {}，位置: {}", r + 1, v, positions);
                    return CheckResult.invalid("第" + (r + 1) + "行存在重复数 " + v, positions, v);
                }
                seen[v] = true;
            }
        }
        logger.info("行重复性检查通过");
        
        // 列重复性检查（忽略 0）
        logger.info("检查列重复性...");
        for (int c = 0; c < 9; c++) {
            boolean[] seen = new boolean[10];
            List<String> positions = new ArrayList<>();
            for (int r = 0; r < 9; r++) {
                int v = g[r][c];
                if (v == 0) continue;
                if (seen[v]) {
                    // 找到重复数字的所有位置
                    for (int r2 = 0; r2 < 9; r2++) {
                        if (g[r2][c] == v) {
                            positions.add((r2 + 1) + "_" + (c + 1));
                        }
                    }
                    logger.warn("第{}列存在重复数: {}，位置: {}", c + 1, v, positions);
                    return CheckResult.invalid("第" + (c + 1) + "列存在重复数 " + v, positions, v);
                }
                seen[v] = true;
            }
        }
        logger.info("列重复性检查通过");
        
        // 九宫格重复性检查（忽略 0）
        logger.info("检查九宫格重复性...");
        for (int br = 0; br < 3; br++) {
            for (int bc = 0; bc < 3; bc++) {
                boolean[] seen = new boolean[10];
                List<String> positions = new ArrayList<>();
                for (int r = br * 3; r < br * 3 + 3; r++) {
                    for (int c = bc * 3; c < bc * 3 + 3; c++) {
                        int v = g[r][c];
                        if (v == 0) continue;
                        if (seen[v]) {
                            // 找到重复数字的所有位置
                            for (int r2 = br * 3; r2 < br * 3 + 3; r2++) {
                                for (int c2 = bc * 3; c2 < bc * 3 + 3; c2++) {
                                    if (g[r2][c2] == v) {
                                        positions.add((r2 + 1) + "_" + (c2 + 1));
                                    }
                                }
                            }
                            logger.warn("第{},{} 宫存在重复数: {}，位置: {}", br + 1, bc + 1, v, positions);
                            return CheckResult.invalid("第" + (br + 1) + "," + (bc + 1) + " 宫存在重复数 " + v, positions, v);
                        }
                        seen[v] = true;
                    }
                }
            }
        }
        logger.info("九宫格重复性检查通过");
        logger.info("基础校验全部通过");
        return CheckResult.valid();
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
    
    /**
     * 判断是否是直接的 Map 格式 (如 {"1_1":1, "1_2":2})
     */
    private static boolean isDirectMapFormat(Map<String, Object> requestBody) {
        // 如果包含 boardData 或 board 字段，则不是直接格式
        if (requestBody.containsKey("boardData") || requestBody.containsKey("board")) {
            return false;
        }
        
        // 检查是否所有键都是 "数字_数字" 格式
        for (String key : requestBody.keySet()) {
            if (!key.matches("\\d+_\\d+")) {
                return false;
            }
        }
        
        return !requestBody.isEmpty();
    }
    
    /**
     * 打印数独网格到日志
     */
    private static void printSudokuGrid(int[][] grid) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        for (int r = 0; r < 9; r++) {
            if (r % 3 == 0 && r > 0) {
                sb.append("  ------+-------+------\n");
            }
            for (int c = 0; c < 9; c++) {
                if (c % 3 == 0 && c > 0) {
                    sb.append(" |");
                }
                if (grid[r][c] == 0) {
                    sb.append(" .");
                } else {
                    sb.append(" ").append(grid[r][c]);
                }
            }
            sb.append("\n");
        }
        logger.info(sb.toString());
    }
}


