# 德州扑克 Solver

[![license](https://img.shields.io/github/license/DanielLiu1123/TexasHoldemSolverJava?style=flat-square)](LICENSE)
[![build](https://img.shields.io/github/actions/workflow/status/DanielLiu1123/TexasHoldemSolverJava/build.yml?style=flat-square)](.github/workflows/build.yml)

README [English](README.md) | [中文](README.zh-CN.md)

一个面向单挑（heads-up）德州扑克的翻牌后 solver，核心是 Counterfactual Regret Minimization（CFR，
反事实遗憾最小化）。它为给定局面构建博弈树，运行某个 CFR 变体直到策略的可利用度
（exploitability）收敛，再把每个决策节点上的混合策略序列化为 JSON。

基于 Java 25。无数据文件、无原生依赖，除 Gradle wrapper 外无需任何安装步骤。

## 特性

- **浏览器 Web UI** 与语言无关的 **HTTP/JSON API**，收敛过程通过 SSE 实时推送
  （[ADR 0001](docs/adr/0001-unified-http-api-replaces-swing-and-jpype.md)）
- **六种 CFR 变体** —— `discounted_cfr`（默认）、`pdcfr`、`pdcfr_plus`、`pcfr_plus`、`cfr_plus`、
  `cfr` —— 默认变体[由实测数据选出，而非论文发表时间](docs/adr/0003-discounted-cfr-remains-the-default.md)
- **完美哈希牌力评估器**，由德扑规则推导生成：约 150 KB 表、23 ms 建表，无字典需要加载
  （[ADR 0002](docs/adr/0002-derive-hand-ranks-instead-of-loading-a-dictionary.md)）
- CFR 热循环 **SIMD 向量化**（`jdk.incubator.vector`）
- 可利用度直接对最佳应对（best response）测量，因此收敛是一个数字，而不是一种期望

领域术语见 [CONTEXT.md](CONTEXT.md)，设计决策记录见 [docs/adr](docs/adr)。

## 环境要求

- **JDK 25** —— 构建目标为 Java 25，并使用孵化中的 Vector API
- 已内置 Gradle wrapper（`./gradlew`），无需单独安装 Gradle
- `web-ui` 模块所需的 Node 由构建自动下载

## 构建与运行

### Web UI

启动内嵌服务（同时提供 HTTP API 和打包好的 Web UI），用浏览器打开 <http://localhost:8080>：

```bash
./gradlew :solver-api:run --args="--port 8080"
```

界面提供 range 编辑器、牌面选择器、实时收敛曲线和策略浏览器。

### HTTP API

同一个服务对外提供 JSON API，可从任意语言调用。端点与请求字段见
[solver-api/README.md](solver-api/README.md)。

```bash
curl -s -X POST localhost:8080/api/v1/solves -H 'Content-Type: application/json' -d '{
  "board": "Kd,Jd,Td,7s,8s",
  "rangeIp":  "AA,KK,QQ,JJ,TT,99,88,AK,AQ,KQ,JT",
  "rangeOop": "AA,KK,QQ,JJ,TT,99,88,AK,AQ,KQ,JT",
  "pot": 10, "effectiveStack": 95, "iterations": 100, "stopExploitability": 0.5
}'
```

### 命令行

CLI 接收一个博弈树 JSON 文件，以及 range、公共牌、迭代次数，输出策略 JSON：

```bash
./gradlew :solver-core:installDist
cd solver-core
./build/install/poker-solver/bin/poker-solver \
  --tree src/test/resources/gametree/river-tree.json \
  -p1 "AA,KK,QQ,JJ,TT,99,AK,AQ,KQ,JT" \
  -p2 "AA,KK,QQ,JJ,TT,99,AK,AQ,KQ,JT" \
  -b "Kd,Jd,Td,7s,8s" -n 100 -i 10 \
  -a discounted_cfr -o /tmp/strategy.json
```

> 运行 `installDist` 产物需要 `JAVA_HOME` 指向 JDK 25。

## 模块结构

| 模块 | 说明 |
| --- | --- |
| `solver-core` | 博弈树构建、CFR 求解器、牌力评估、CLI |
| `solver-api` | 内嵌 HTTP/JSON API（Javalin + 虚拟线程）；并托管 Web UI |
| `web-ui` | React + TypeScript 前端，构建时打包进 `solver-api` |
| `benchmarks` | 牌力评估、树构建、求解的 JMH 基准 |

## 解读求解结果

求解过程中，每 `-i` 次迭代输出一次可利用度（以底池百分比计）：

```text
iteration 0:  exploitability 20.737505% of pot
iteration 21: exploitability 0.191139% of pot (14 ms)
iteration 41: exploitability 0.056625% of pot (9 ms)
```

可利用度衡量的是：一个采取最佳应对的对手，能从当前策略上赢走多少。它恰好在纳什均衡处归零；
低于约 0.5% 底池通常就足以当作最优策略使用。

策略 JSON 把每个行动节点映射到可选动作，以及每手牌在这些动作上的混合策略：

```json
{
  "nodeType": "action_node",
  "player": 1,
  "actions": ["CHECK", "BET 2.0"],
  "strategy": {
    "strategy": {
      "AsAh": [0.9961, 0.0039],
      "JsTs": [0.6416, 0.3584]
    }
  }
}
```

也就是说，拿到 `JsTs` 时的均衡打法是 64% 过牌、36% 下注。

## 算法

内置六种 CFR 变体。单线程 200 次迭代后的可利用度（底池百分比，越低越好），由 `AlgorithmBakeoff`
实测：

|                                                               | river（宽 range） | river（大牌 range） | turn       |
| ------------------------------------------------------------- | ---------------- | ------------------ | ---------- |
| `cfr` —— 经典 CFR（Zinkevich 2007）                            | 0.0413           | 0.2196             | 1.506      |
| `cfr_plus` —— regret-matching⁺（Tammelin 2014）                | 0.0099           | 0.0100             | 0.204      |
| `pcfr_plus` —— predictive CFR+（Farina 2021）                  | 0.0103           | 0.0251             | 0.137      |
| `pdcfr_plus` —— predictive discounted CFR+（Xu 2024）          | 0.0381           | 0.0213             | 0.209      |
| `pdcfr` —— predictive discounted CFR（Xu 2024）                | 0.0089           | 0.0174             | 0.0714     |
| **`discounted_cfr`** —— discounted CFR（Brown & Sandholm 2019） | **0.0013**       | **0.0051**         | **0.0400** |

Discounted CFR 在所有实测场景中都胜出，因此作为默认变体。乐观（optimistic）系变体在矩阵博弈上领先、
在这里落后，这与它们各自论文的结论一致。详见
[ADR 0003](docs/adr/0003-discounted-cfr-remains-the-default.md)。

牌力评估是一个由德扑规则生成的完美哈希评估器：一次七张牌评估等于四次位聚集、一次 popcount，
外加两次数组读取，表约 150 KB，无需加载任何数据文件
（[ADR 0002](docs/adr/0002-derive-hand-ranks-instead-of-loading-a-dictionary.md)）。

只支持 52 张牌的标准德州扑克（[ADR 0004](docs/adr/0004-drop-short-deck-support.md)）。

## 基准测试

性能由 JMH `benchmarks` 模块护航：

```bash
./gradlew :benchmarks:jmh -PjmhIncludes='RiverSolveBenchmark'
```

在 M 系列 Mac、JDK 25 上实测：

| | |
| --- | --- |
| 七张牌牌力评估 | 26.3 ns |
| river 求解，20 次迭代，单线程 | 1.97 ms |
| 评估器建表（进程内一次性） | 22.9 ms |

完整列表见 [benchmarks/README.md](benchmarks/README.md)。

## 参与贡献

欢迎提 issue 和 PR。`./gradlew build` 会跑完整检查：测试、Spotless、Error Prone 和 NullAway。
架构决策记录在 [docs/adr](docs/adr) —— 若某次改动推翻了旧决策，请新增一份 ADR，而不是修改旧的。

## License

[MIT](LICENSE)。

## 致谢

本项目起源于 [bupticybee](https://github.com/bupticybee) 的
[TexasHoldemSolverJava](https://github.com/bupticybee/TexasHoldemSolverJava)（上游已停止维护）。
求解器核心此后已被大幅重写；原始版权声明按 MIT 许可证要求保留在 [LICENSE](LICENSE) 中。
