# TexasHoldemSolverJava

[![release](https://img.shields.io/github/v/release/bupticybee/TexasHoldemSolverJava?label=release&style=flat-square)](https://github.com/bupticybee/TexasHoldemSolverJava/releases)
[![license](https://img.shields.io/github/license/bupticybee/TexasHoldemSolverJava?style=flat-square)](https://github.com/bupticybee/TexasHoldemSolverJava/blob/master/LICENSE)

README [English](README.md) | [中文](README.zh-CN.md)

## 项目介绍

一个完全开源，java实现的高效标准德州扑克和短牌solver, 看看这个 [介绍视频](https://www.bilibili.com/video/BV1FV411e7Jr) 了解更多.

![algs](img/solvergui.gif)

这是一个基于java的德州扑克solver,完全开源,提供浏览器 Web UI、命令行调用和内嵌 HTTP API(见 solver-api/README.md),实现了标准德州扑克和德州扑克的一个变种-德州扑克短牌的solver,和piosolver等常见德州扑克solver类似，重点提供翻牌后情况的求解，solver求解结果结果和piosolver对齐。速度上在~~turn和~~river上比piosolver快一些，但是flop比piosolver慢。

项目特性:
- 高效,~~转牌和~~河牌计算速度超过piosolver
- 准确，结果和piosolver几乎相同
- 完全开源并且免费
- 内嵌 HTTP API 直接提供浏览器 Web UI
- 支持标准德州扑克和流行的变种玩法短牌
- 主要聚焦在翻牌后求解
- 支持命令行调用

本项目适合:
- 德州扑克高级玩家
- 不完全信息博弈领域研究的学者。

## 安装

首先需要安装64位 [Java Runtime Environment](https://www.oracle.com/java/technologies/javase-jre8-downloads.html).

下载[release](https://github.com/bupticybee/TexasHoldemSolverJava/releases) 包,release包的结构如下：

```
--- Solver
 |- resources
 |- RiverSolver.jar
 |- riversolver.sh
```

安装就这样完成了，就是这么简单!

其中RiverSolver是德州扑克solver主体程序。
- 短牌flop求解示例
- 短牌turn求解示例
- 短牌river求解示例
- 标准德州扑克turn求解示例
- 标准德州扑克river求解示例

riversolver.sh 包含了命令行调用solver的示例

除了需要下载软件本身之外，TexasHoldemSolverJava 还依赖 JRE 11.0.2 作为运行库。如果电脑上没有请安装java JRE 11.0.2。


## 使用
### Web UI

启动内嵌服务后用浏览器打开 http://localhost:8080：

```bash
./gradlew :solver-api:run --args="--port 8080 --resources riversolver/src/test/resources"
```

本 fork 已移除原 Swing 图形界面，由 Web UI 取代
（[ADR 0001](docs/adr/0001-unified-http-api-replaces-swing-and-jpype.md)）。

### python 调用方法

本 fork 已移除基于 JPype 的 python 接口
（[ADR 0001](docs/adr/0001-unified-http-api-replaces-swing-and-jpype.md)）。
python——以及任何语言——请改用内嵌的 [HTTP API](solver-api/README.md) 调用。

### 命令行调用方法

参考[release](https://github.com/bupticybee/TexasHoldemSolverJava/releases) 包中的riversolver.sh

### 分析求解器产生的结果
首先求解器运行的时候会输出类似如下的日志:
```text
Iter: 0
player 0 exploitability 1.653075
player 1 exploitability 2.146374
Total exploitability 47.493111 precent
-------------------
Iter: 11
player 0 exploitability 0.040586
player 1 exploitability 0.322102
Total exploitability 4.533607 precent
-------------------
......
-------------------
Iter: 41
player 0 exploitability -0.114473
player 1 exploitability 0.168947
Total exploitability 0.680923 precent
.Using 4 threads
```
注意其中的 exploitability的收敛情况，一般来说 小于0.5就完全够用了

solver运行完毕后会输出一个output_strategy.json文件包含了求解出来的策略,建议用firefox（对，就是那个浏览器）打开这个文件,根据游戏树的不同大小，这个文件可能会有几kb到几G大

打开后可以看到类似下图的结果：

![algs](img/strategy1.png)

```text
player : 1
```
这个字段表示当前节点是player1进行动作

```text
actions:
    0: "CHECK"
    1: "BET 4.0"
```

而strategy字段下就是不同手牌应该采取的策略:

![algs](img/strategy2.png)

在strategy的具体每一个项中，则包含了拿到该手牌时的"最佳策略"

![algs](img/strategy3.png)

比如在上图中的信息就代表player1在拿到 Qd7c (方块Q，梅花7) 手牌的时候，最优策略就是以 34%的概率去check，以65%概率去 bet。

## 编译release包

一般情况下release包不需要编译，而可以直接从[项目release](https://github.com/bupticybee/TexasHoldemSolverJava/releases) 下载
如果需要对项目进行二次开发，则需要重新编译release包。本项目是一个IDEA项目，需要在IDEA环境下编译release包，具体步骤：
1. 安装IntellIJ IDEA
2. 从github上下载本项目，并且加载到IntellIJ IDEA中
3. 菜单栏 build -> build project 编译项目
4. 菜单栏 build -> build artifacts -> all artifacts -> build 生成release包
5. 编译完成的release包可以在工程根目录下的out 路径中找到


## 对照实验

和piosolver的速度对比实验如下,同一个牌面下turn 和river的速度和piosolver仍处于接近水平, 但是flop比piosolver慢很多，由于flop的代码尚未很好的优化.

|                       | flop sample | turn sample | river sample |
| --------------------- | ----------- | ----------- | ------------ |
| piosolver             | 7.91s       | 1.5s        | 0.56s        |
| TexasHoldemSolverJava | 98s         | 4.21s       | 0.06s        |

上面实验中的pio格式输入，和结果对比列在下面表格中，任何人均可复现：

|                | flop sample | turn sample | river sample |
| -------------- | ----------- | ----------- | ------------ |
| 输入 (pio格式)        |   [flop](benchmarks/benchmark_flop.txt)          | [turn](benchmarks/benchmark_turn.txt)            |        [river](benchmarks/benchmark_river.txt)      |
| 输入 (图片格式)         |   ![flop](img/flop_setting.jpeg)          | ![turn](img/turn_setting.jpeg)            |       ![river](img/river_setting.jpeg)       | 
| 结果对比         |   ![flop](img/flop_result.jpeg)          | ![turn](img/turn_result.jpeg)            |       ![river](img/river_result.jpeg)       | 

结果策略上和Piosolver的略微不同是由于TexasHoldemSolverJava采用了和Piosolver略微不同的游戏树构建算法，并且两个算法停止时均为完全收敛.

## 算法
如图,得益于实现的最新算法的变种 discounted cfr++, 在算法上可以保证比cfr+等传统算法快得多的速度。
![algs](img/algs.png)

## c++ 版本

如果你觉得这个java版本还不够快，可以尝试一下我们的[c++版本](https://github.com/bupticybee/TexasSolver) ,c++版本在turn和river上会比java版本快，但是有两个缺点：

- ~~仅支持Linux机器~~
- ~~使用前必须重新编译~~
- ~~没有很好的优化，~~ 在flop的计算上~~会占用数量惊人的内存空间~~ 比c++版本快5倍以上

## License

[MIT](LICENSE) © bupticybee

## 联系方式

icybee@yeah.net

