<h1 align="center">🏪 TermSim Engine · 终端模拟经营游戏引擎</h1>

<p align="center">
  <b>🤖 本项目由 AI 生成 · 0 行手写代码</b><br>
  <small>整个引擎代码 + 玩法逻辑 + 两个游戏配置文件均由 AI 大模型生成，作者未手写一行业务代码。</small>
</p>

<p align="center">
  <a href="https://github.com/itsunhao/term-sim-engine/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT"></a>
  <img src="https://img.shields.io/badge/Java-17+-orange.svg" alt="Java 17+">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.5-green.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/AI-Generated-red.svg" alt="AI Generated">
  <a href="README_EN.md">English</a>
</p>

---

## 📖 项目简介

**TermSim Engine** 是一个基于 **Spring Boot 3.3.5 + Thymeleaf** 的终端风格模拟经营游戏引擎。核心理念：**引擎与游戏文件（JSON）分离**——换一个 JSON 就是另一个游戏。

- **引擎**（Java 代码）提供通用经营模拟框架：时间/四季/天气、客流模拟、NPC 社交、贷款理财、随机事件、存档系统等；
- **游戏文件**（JSON）决定具体玩法：小卖部、饭馆、奶茶店……任何场景一个 JSON 即可"安装即玩"；
- **界面**：经典 CRT 终端风格——荧光绿文字、扫描线、闪烁光标、命令行输入。

开箱自带 **「街角小卖部」** 和 **「街角饭馆」** 两套完整游戏。

## ✨ 功能亮点（12 个版本迭代）

| 模块 | 功能 |
|---|---|
| 时间系统 | 现实 1 秒 = 游戏 1 分钟，暂停/倍速（0.5×~5×），四季×天气×时段影响客流 |
| NPC 社交 | 6 位街坊，好感度+评价+主动社交（闲聊/送礼/请客），委托订单 |
| 经营策略 | 批发贸易、外卖平台、雇佣系统、银行理财、供应链折扣 |
| 地产分店 | 4 处地段贷款买房、迁移店铺、多店独立经营 |
| 竞争节日 | 街区竞争（价格战/营销战）、四季节日庆典、口碑评级 |
| 员工培养 | 经验成长、忠诚度、晋升骨干、永久技能 |
| 3D 背景 | Three.js 粒子星云+网格地平线，离线降级 |
| 工程质量 | 62 个 JUnit 5 测试用例，纯 JSON 存储无数据库 |

## 🚀 快速开始

```bash
# 需要 JDK 17+ 与 Maven
git clone https://github.com/itsunhao/term-sim-engine.git
cd term-sim-engine
mvn spring-boot:run
# 浏览器打开 http://localhost:8899/
```

## 🎮 内置游戏

- **🎮 街角小卖部**：26 种商品、批发贸易、4 名雇员、银行/委托/彩票、4 处地产、6 位街坊
- **🍜 街角饭馆**：26 道菜、7 级装修解锁菜系、外卖平台

## 📁 项目结构

```
├── games/                     # 游戏配置（每个 .json 一个游戏）
│   ├── convenience-store.json
│   └── street-restaurant.json
├── src/main/java/com/termgame/
│   ├── config/                # 引擎配置
│   ├── model/def/             # 游戏定义模型（record 映射 JSON）
│   ├── engine/                # 游戏引擎核心
│   └── web/                   # 控制器与 API
└── src/test/                  # 62 个 JUnit 测试
```

## 🤖 关于 AI 生成

> **0 手工编程**：引擎的 Java 代码、玩法逻辑、Thymeleaf 模板、前端样式、两个 JSON 游戏文件，全部由 AI 大模型生成。
>
> **作者的工作**：提需求（"我要一个终端风格的小卖部经营游戏"）、规划玩法、逐版本迭代调试、写测试并验收。
>
> **12 个版本**（v1.0 → v1.12）沉淀了分店系统、供应链、口碑评级、街区竞争、节日庆典、员工晋升等完整系统。

## 📄 License

[MIT License](LICENSE) — 可自由使用、修改、分发与商用。

## 🔗 链接

- Gitee 镜像：https://gitee.com/itsunhao/term-sim-engine
- 作者：itsunhao
