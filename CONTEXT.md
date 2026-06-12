# TexasHoldemSolver

一个基于 CFR（Counterfactual Regret Minimization，反事实遗憾最小化）的德州扑克翻牌后求解器，计算双人对局中接近纳什均衡的混合策略。支持标准德扑和短牌（short-deck）变体。

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
双方亮牌比大小的终局节点，收益由 Compairer 决定。

**TerminalNode（弃牌终局节点）**:
一方 fold 导致的终局节点，收益直接由底池归属决定。
_Avoid_: fold node

### 范围与手牌

**Range（范围）**:
一个玩家可能持有的所有底牌组合及其权重，用字符串语法（如 "AA,KQs:0.5"）表达，解析为 PrivateCards 数组。

**PrivateCards（底牌组合）**:
范围中的单个两张底牌组合，带权重。
_Avoid_: hole cards（代码语境下）

**Compairer（比牌器）**:
判定摊牌时五张牌牌力强弱的组件。现有实现 Dic5Compairer 通过预先排序的字典文件（约 260 万行）查表得到牌力等级。

### 求解

**Solver（求解器）**:
在 GameTree 上迭代训练直至策略收敛的算法骨架。具体实现有单线程 CfrPlusRiverSolver 和基于 ForkJoin 的 ParallelCfrPlusSolver 等。

**Trainable（可训练单元）**:
挂在每个 ActionNode 上的策略存储与更新单元，记录 regret 和平均策略。三种实现对应三种 CFR 变体：cfr / cfr_plus / discounted_cfr。

**Exploitability（可利用度）**:
衡量当前策略离纳什均衡的距离，由 BestResponse 计算；低于阈值即视为收敛、停止迭代。

**Strategy dump（策略导出）**:
训练完成后把整棵树上每个节点的混合策略序列化为 JSON，供 Web UI 展示或外部程序消费。
