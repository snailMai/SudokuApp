# 数独校验小项目

## 运行

1. 安装 JDK 17 与 Maven
2. 在项目根目录执行：

```bash
mvn spring-boot:run
```

启动后访问：`http://localhost:8081/` 打开前端页面。

## 接口
- POST `/api/sudoku/verify`
- 请求体：
```json
{ "board": [[0,1,2,3,4,5,6,7,8], [...]] }
```
- 响应示例：
```json
{ "ok": true, "message": "校验通过，存在解", "solution": [[...]] }
```

## 前端
- `src/main/resources/static/index.html` 内置 9x9 网格、输入限制、示例题与清空按钮。
- 按钮会向后端发送 JSON 进行校验。



