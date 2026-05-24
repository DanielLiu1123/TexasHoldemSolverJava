# Texas Hold'em Solver — 架构概览

> 基于 **Counterfactual Regret Minimization（CFR）** 算法的德州扑克 GTO 求解器，支持 GUI、命令行、Python 绑定三种运行方式。

---

## 目录

1. [快速启动](#快速启动)
2. [整体架构](#整体架构)
3. [模块说明](#模块说明)
4. [核心算法：CFR+](#核心算法cfr)
5. [数据结构与编码](#数据结构与编码)
6. [配置参数速查](#配置参数速查)

---

## 快速启动

```bash
# GUI 模式（推荐新手）
./gradlew run

# 命令行模式
java -cp "build/libs/RiverSolver.jar" icybee.solver.runtime.CommandlineSolver \
  -c resources/yamls/rule_holdem_simple.yaml \
  -p1 "KK,QQ,AK" \
  -p2 "AK,AQ,AJ" \
  -b "Kh,Td,2c" \
  -n 1000 \
  -a cfr_plus \
  -t 8
```

---

## 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        入口层（Entry）                           │
│                                                                  │
│    SolverGui.java          CommandlineSolver.java                │
│    (Swing GUI 主窗口)       (CLI，argparse4j 解析参数)            │
│            │                        │                            │
│            └────────────┬───────────┘                            │
│                         ▼                                        │
│                  PokerSolver.java                                │
│                  (Python 绑定 / 统一调度入口)                     │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                       运行时层（Runtime）                         │
│                                                                  │
│   SolverEnvironment ─── Config ─── Deck ─── Compairer           │
│   (全局单例初始化)       (YAML)    (牌组)   (牌力评估字典)         │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                       算法层（Solver）                            │
│                                                                  │
│   Solver（抽象基类）                                              │
│    ├── CfrPlusRiverSolver         单线程 CFR+                    │
│    ├── ParallelCfrPlusSolver      多线程 CFR+（ForkJoinPool）     │
│    ├── ParallelDcfrSolver         多线程 Discounted CFR          │
│    └── PublicChanceSamplingSolver 公共机会采样                    │
│                                                                  │
│   BestResponse                    可剥削性（Exploitability）计算  │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                      数据结构层（Data）                           │
│                                                                  │
│   GameTree                博弈树容器 + 构建器                     │
│    └── GameTreeNode（抽象）                                       │
│         ├── ActionNode    玩家决策节点（含 Trainable 策略）        │
│         ├── ChanceNode    随机发牌节点                            │
│         ├── ShowdownNode  摊牌节点                                │
│         └── TerminalNode  终止节点（弃牌）                        │
│                                                                  │
│   Trainable（接口）        CFR 策略与遗憾值存储                   │
│    ├── CfrTrainable       标准 CFR                               │
│    ├── CfrPlusTrainable   CFR+（正遗憾截断）                      │
│    └── DiscountedCfrTrainable  折扣 CFR                          │
│                                                                  │
│   PrivateCards / PrivateCardsManager / RiverCombs                │
│   RiverRangeManager       River 阶段范围缓存                      │
│   Card / Deck             牌的表示与编码                          │
└─────────────────────────────────────────────────────────────────┘
```

---

## 模块说明

### `nodes/` — 博弈树节点

| 类 | 职责 |
|----|------|
| `GameTreeNode` | 抽象基类，定义 `PokerActions`、`GameRound`、`GameTreeNodeType` 枚举 |
| `ActionNode` | 玩家决策节点，持有可用动作列表和对应子节点，关联一个 `Trainable` |
| `ChanceNode` | 随机发牌节点，每张可能的社区牌对应一个子节点 |
| `ShowdownNode` | 摊牌节点，存储按赢家分配的赔率表 `player_payoffs[][]` |
| `TerminalNode` | 弃牌终止节点，直接持有赔率数组 `payoffs[]` |

---

### `solver/` — CFR 求解器

| 类 | 职责 |
|----|------|
| `CfrPlusRiverSolver` | 单线程 CFR+，River 叶子节点直接查字典比牌 |
| `ParallelCfrPlusSolver` | 多线程版，使用 `ForkJoinPool` 在 Action/Chance 节点分叉 |
| `ParallelDcfrSolver` | Discounted CFR，引入时间折扣权重加速收敛 |
| `BestResponse` | 计算策略的最优应答 EV 和 Exploitability |

**并行化控制参数**（`ParallelCfrPlusSolver`）：
- `forkprob_action` / `forkprob_chance` — 节点分叉概率（0~1）
- `fork_every_n_depth` — 每隔 N 层才允许分叉
- `no_fork_subtree_size` — 子树节点数低于此值不分叉

---

### `trainable/` — 策略存储

每个 `ActionNode` 持有一个 `Trainable` 实例，存储该节点所有手牌在所有动作上的遗憾值和累积策略。

内部以一维数组压缩存储（`action_id * card_number + card_id`），避免二维数组开销。

---

### `compairer/` — 牌力评估

`Dic5Compairer` 在初始化时将预计算的 5 张牌排名字典（约 260 万条记录）加载到 `Map<Long, Integer>` 中，运行时按位图 key 直接查表，O(1) 完成牌力评估。

---

### `ranges/` — 手牌范围

| 类 | 职责 |
|----|------|
| `PrivateCards` | 单手两张牌，含权重 `weight` 和相对概率 `relative_prob` |
| `PrivateCardsManager` | 管理全部手牌组合，支持按范围字符串过滤 |
| `RiverCombs` | River 阶段每手牌在当前公共牌下的排名快照 |
| `RiverRangeManager` | 以 `long`（位图）为 key 缓存 River 范围，避免重复计算 |

范围字符串由 `PrivateRangeConverter.rangeStr2Cards()` 解析，支持 `AA`、`AKs`、`AKo`、`KK:0.5`（带权重）等格式。

---

### `gui/` — Swing 界面

```
SolverGui（主窗口）
 ├── RangeSelector   手牌范围选择（支持 13×13 矩阵点击）
 ├── BoardSelector   公共牌选择
 └── SolverResult    策略结果展示

CustomOutputStream  将 System.out 重定向到 GUI 日志区域
```

**界面操作流程**：

```
配置范围 + 木板
    → buildTreeButton → GameTree.buildTree()
    → startSolvingButton → Solver.train()（后台线程，实时写日志）
    → showResultButton → 展示各手牌动作频率
```

---

## 核心算法：CFR+

### 基本思路

CFR 通过迭代不断减少每个信息集上的"反事实遗憾"（Counterfactual Regret），最终收敛到纳什均衡。

**CFR+ 改进**：遗憾值截断为非负，加速收敛。

### 一次迭代的流程

```
for t in 1..T:
    ┌─ 遍历博弈树（cfr 递归）
    │   ├── ActionNode：对每个动作计算反事实价值
    │   │       Regret(a) = V(σ, a) - V(σ)
    │   ├── ChanceNode：对每张可能的牌递归，概率加权求和
    │   ├── ShowdownNode：查 Compairer 字典，直接返回 payoff
    │   └── TerminalNode：直接返回 payoff
    │
    └─ 更新策略（Trainable.updateRegrets）
           R⁺(a) = max(0, R(a) + R⁺_prev(a))   ← CFR+ 截断
           π(a)  = R⁺(a) / Σ_b R⁺(b)            ← 遗憾匹配

    每隔 print_interval 次：BestResponse 计算 Exploitability
```

### 关键优化

1. **River 叶子直接查表**：到达 River 后不再递归，直接用 `Dic5Compairer` 比牌
2. **范围缓存**：`RiverRangeManager` 缓存每种公共牌组合下的手牌排名
3. **并行分叉**：在 Action/Chance 节点提交 `ForkJoinTask`，充分利用多核

---

## 数据结构与编码

### 牌的整数编码

```
card_int = rank_index * 4 + suit_index

rank_index: 2→0, 3→1, ..., A→12
suit_index: c→0, d→1, h→2, s→3

示例：Ks = 12 * 4 + 3 = 51
      Ah = 12 * 4 + 2 = 50
```

### 位图编码（`long`）

将多张牌编码为一个 `long`，第 `card_int` 位置 1。用于：
- `boardCards2long()` — 牌组转位图
- `boardsHasIntercept()` — O(1) 检测两手牌是否有重叠

### 策略数组布局

`Trainable` 内部数组按 `[action_id * card_count + card_id]` 压缩存储：

```
actions: [fold, call, raise]
cards:   [AA, KK, QQ, ...]

index = action_id * total_cards + card_id
```

---

## 配置参数速查

### 求解参数

| 参数 | 说明 | 示例 |
|------|------|------|
| `algorithm` | 算法类型 | `cfr` / `cfr_plus` / `discounted_cfr` |
| `iteration_number` | CFR 迭代次数 | `1000` |
| `threads` | 并行线程数（0=单线程） | `8` |
| `print_interval` | 多少次迭代打印一次 Exploitability | `100` |
| `raise_limit` | 单街最多加注次数 | `2` |

### 树构建参数

| 参数 | 说明 |
|------|------|
| `*_bet` | 下注大小（%底池），逗号分隔，如 `"0.5,1"` |
| `*_raise` | 加注大小 |
| `*_donk` | Donk bet 大小（OOP 主动下注） |
| `*_allin` | 是否在该街允许 All-In |

前缀为街道和位置：`flop_ip`、`flop_oop`、`turn_ip`、`turn_oop`、`river_ip`、`river_oop`

### 并行调优参数（`ParallelCfrPlusSolver`）

| 参数 | 说明 |
|------|------|
| `fork_at_action` | ActionNode 分叉概率（0~1） |
| `fork_at_chance` | ChanceNode 分叉概率 |
| `fork_every_n_depth` | 每隔 N 层才允许分叉 |
| `no_fork_subtree_size` | 子树节点数低于此值不分叉 |

---

## 依赖库

| 库 | 用途 |
|----|------|
| `tools.jackson` | JSON 序列化 / 反序列化博弈树 |
| `snakeyaml` | 解析 YAML 配置文件 |
| `progressbar` | CLI 字典加载进度条 |
| `combinatoricslib3` | 手牌组合枚举（5 选 C） |
| `argparse4j` | 命令行参数解析 |
| `java-gui-forms-rt` | IntelliJ GUI Form 运行时支持 |
