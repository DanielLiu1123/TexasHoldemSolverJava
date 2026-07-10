# TexasHoldemSolver

一个基于 CFR（Counterfactual Regret Minimization，反事实遗憾最小化）的德州扑克翻牌后求解器，计算双人对局中接近纳什均衡的混合策略。只支持标准 52 张牌德州扑克。

## Language

### 博弈建模

**GameTree（博弈树）**:
一手牌从当前局面到终局的完整决策树，由四种节点组成。求解器的输入和输出（策略）都挂在这棵树上。
_Avoid_: decision tree, 决策树（泛指时）

**ActionNode（行动节点）**:
轮到某一玩家做决策的节点，子节点对应 check / bet / raise / call / fold 等动作。策略（Trainable）只存在于这种节点上。

**ChanceNode（机会节点）**:
发公共牌的节点，子节点按发出的牌枚举。

**ShowdownNode（摊牌节点）**:
双方亮牌比大小的终局节点，收益由 HandEvaluator 决定。

**TerminalNode（弃牌终局节点）**:
一方 fold 导致的终局节点，收益直接由底池归属决定。
_Avoid_: fold node

### 范围与手牌

**Range（范围）**:
一个玩家可能持有的所有底牌组合及其权重，用字符串语法（如 "AA,KQs:0.5"）表达，解析为 PrivateCards 数组。

**PrivateCards（底牌组合）**:
范围中的单个两张底牌组合，带权重。
_Avoid_: hole cards（代码语境下）

**HandEvaluator（手牌评估器）**:
判定五到七张牌牌力强弱的组件。按德扑规则在类初始化时生成完美哈希表，返回稠密 rank（1 = 皇家同花顺，7462 = 最小高牌，越小越强，相等即平局）。全静态、无实例、无数据文件。
_Avoid_: 比牌器

**Deck（牌组）**:
固定的 52 张牌，按 card id 排序（`Deck.card(i).getCardInt() == i`）。ChanceNode 的子节点与它同序，第 i 条边发的就是 `Deck.card(i)`。

**RiverRange（河牌范围）**:
一名玩家的范围投影到某个完整公共牌面后的结果：未被牌面阻断的手牌，各自带 rank，按弱到强排序。以列存（struct-of-arrays）布局，摊牌 kernel 直接线性扫描。

### 求解

**Solver（求解器）**:
在 GameTree 上迭代训练直至策略收敛的算法骨架。遍历与训练主循环在 AbstractCfrSolver，两个具体实现只决定子节点如何调度：SequentialCfrSolver 顺序遍历，ParallelCfrSolver 用 ForkJoin 分叉。

**Trainable（可训练单元）**:
挂在每个 ActionNode 上的策略存储与更新单元，记录 regret 并累积平均策略。六种实现对应六种 CFR 变体：cfr / cfr_plus / pcfr_plus / pdcfr_plus / pdcfr / discounted_cfr（默认）。

**Average strategy（平均策略）**:
CFR 收敛到纳什均衡的那个策略——历次迭代所打策略的 reach 加权平均。区别于 current strategy（当前策略），后者只有在 CFR+ 家族里才近似收敛，vanilla CFR 的当前策略会绕着均衡振荡。策略导出的永远是平均策略。
_Avoid_: current strategy（作为"解"时）

**Exploitability（可利用度）**:
衡量当前策略离纳什均衡的距离，由 BestResponse 计算；低于阈值即视为收敛、停止迭代。

**Strategy dump（策略导出）**:
训练完成后把整棵树上每个节点的混合策略序列化为 JSON，供 Web UI 展示或外部程序消费。
