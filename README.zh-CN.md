# TexasHoldemSolverJava

[![license](https://img.shields.io/github/license/bupticybee/TexasHoldemSolverJava?style=flat-square)](LICENSE)

README [English](README.md) | [中文](README.zh-CN.md)

> **关于本 fork。** 这是 [bupticybee 原版 TexasHoldemSolverJava](https://github.com/bupticybee/TexasHoldemSolverJava)
> 的现代化 fork（上游已停止维护）。基于 Java 25 与 Gradle 多模块构建；原 Swing 图形界面和
> JPype Python 桥接已由浏览器 Web UI + 内嵌 HTTP API 取代
> （[ADR 0001](docs/adr/0001-unified-http-api-replaces-swing-and-jpype.md)）；CFR 热循环用
> Java Vector API 做了 SIMD 向量化。若需要更快的原生求解器，见 C++ 移植版
> [TexasSolver](https://github.com/bupticybee/TexasSolver)。

## 项目介绍

一个完全开源、Java 实现的高效标准德州扑克与短牌（六加）solver。与 piosolver 等商业 solver 类似，
重点求解**翻牌后**情况，结果与 piosolver 对齐。river 上比 piosolver 快，flop 上比 piosolver 慢。

核心是 Counterfactual Regret Minimization（CFR）：构建翻牌后博弈树，运行某个 CFR 变体直到策略的
可利用度（exploitability）收敛，再把每个节点上的混合策略序列化为 JSON。领域术语见
[CONTEXT.md](CONTEXT.md)。

本项目适合：

- 德州扑克高级玩家
- 不完全信息博弈领域的研究者

## 项目特性

- 高效 —— river 与 turn 的求解速度接近或超过 piosolver
- 准确 —— 结果与 piosolver 高度一致
- 完全开源且免费（MIT）
- 浏览器 Web UI 与语言无关的 HTTP/JSON API（含 SSE 实时收敛进度流）
- 支持标准德州扑克与短牌
- 可选 CFR 变体：`discounted_cfr`（默认）、`pcfr_plus`、`cfr_plus`、`cfr`
- CFR 热循环 SIMD 向量化（`jdk.incubator.vector`）

## 环境要求

- **JDK 25**（构建目标为 Java 25，并使用孵化中的 Vector API）
- 已内置 Gradle wrapper（`./gradlew`），无需单独安装 Gradle
- `web-ui` 模块所需的 Node 由构建自动下载

仓库已自带牌力字典与示例 range（位于 `riversolver/src/test/resources`），无需额外下载数据。

## 构建与运行

### Web UI（推荐）

启动内嵌服务（同时提供 HTTP API 和打包好的 Web UI），用浏览器打开 <http://localhost:8080>：

```bash
./gradlew :solver-api:run --args="--port 8080 --resources riversolver/src/test/resources"
```

界面提供 range 编辑器、牌面选择器、实时收敛曲线和策略浏览器。

### HTTP API

同一个服务对外提供 JSON API，可从任意语言调用。端点、请求字段与 `curl` 示例见
[solver-api/README.md](solver-api/README.md)：

```bash
curl -s -X POST localhost:8080/api/v1/solves -H 'Content-Type: application/json' -d '{
  "game": "shortdeck",
  "board": "Kd,Jd,Td,7s,8s",
  "rangeIp":  "AA,KK,QQ,JJ,TT,99,88,AK,AQ,KQ,JT",
  "rangeOop": "AA,KK,QQ,JJ,TT,99,88,AK,AQ,KQ,JT",
  "pot": 10, "effectiveStack": 95, "iterations": 100, "stopExploitability": 0.5
}'
```

### 命令行

CLI 接收一个 YAML 规则文件以及 range/牌面/迭代次数，输出策略 JSON。请在 `riversolver` 模块目录下运行
（示例 YAML 里的字典路径相对该目录解析）：

```bash
./gradlew :riversolver:installDist
cd riversolver
./build/install/RiverSolver/bin/RiverSolver \
  -c src/test/resources/yamls/rule_holdem_simple.yaml \
  -p1 "AA,KK,QQ,JJ,TT,99,AK,AQ,KQ,JT" \
  -p2 "AA,KK,QQ,JJ,TT,99,AK,AQ,KQ,JT" \
  -b "Kd,Jd,Td,7s,8s" -n 100 -i 10 \
  -a discounted_cfr -o /tmp/strategy.json
```

> 运行 `installDist` 产物需要 `JAVA_HOME` 指向 JDK 25。

## 模块结构

| 模块 | 说明 |
| --- | --- |
| `riversolver` | 求解器核心：博弈树构建、CFR 求解器、牌力评估、CLI |
| `solver-api` | 内嵌 HTTP/JSON API（Javalin + 虚拟线程）；并托管 Web UI |
| `web-ui` | React + TypeScript 前端，构建时打包进 `solver-api` |
| `benchmarks` | 牌力查找、树构建、求解的 JMH 基准 |

## 分析求解器产生的结果

求解过程中会按迭代输出可利用度（以底池百分比计）：

```text
Iter: 0   Total exploitability 47.49 percent
Iter: 11  Total exploitability  4.53 percent
Iter: 41  Total exploitability  0.68 percent
```

关注可利用度的收敛情况——低于约 0.5% 底池通常就足以当作最优策略使用。

策略 JSON 把每个节点映射到所考虑的动作，以及每手牌在这些动作上的混合策略：

![strategy](img/strategy2.png)

例如拿到 `qd7c` 时最优策略可能是 check 34% / bet 65%。

![strategy detail](img/strategy3.png)

## 基准测试

性能由 JMH `benchmarks` 模块护航：

```bash
./gradlew :benchmarks:jmh -PjmhIncludes='RiverSolveBenchmark'
```

可用基准见 [benchmarks/README.md](benchmarks/README.md)。作为历史参考，最初与 piosolver 的逐街对比
（turn/river 接近，flop 因树规模较大而更慢）：

|                       | flop  | turn  | river |
| --------------------- | ----- | ----- | ----- |
| piosolver             | 7.91s | 1.5s  | 0.56s |
| TexasHoldemSolverJava | 98s   | 4.21s | 0.06s |

## 算法

默认的 `discounted_cfr`（Discounted CFR+）比经典 CFR/CFR+ 收敛快得多。另外提供 `pcfr_plus`
（Predictive CFR+）；在这些扑克子博弈上 DCFR 仍然收敛最快，这与 PCFR+ 论文自己的扑克实验结论一致。

![algorithms](img/algs.png)

## c++ 版本

如果觉得 Java 版还不够快，可以试试原生 [C++ 移植版 TexasSolver](https://github.com/bupticybee/TexasSolver)，
它在 turn 和 river 上更快，在 flop 上约快 5 倍。

## License

[MIT](LICENSE) © bupticybee

## 联系方式

icybee@yeah.net
