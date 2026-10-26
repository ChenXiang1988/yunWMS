# 语音合成模型选型调研（Breeze TTS 2 及可商用替代）

- 调研时间：2026-10-25
- 时效：本文为 2026-10-25 的快照，记录当时状态；上游许可证与榜单会变，以仓库现状为准，本文不回改。
- 数据来源：GitHub 仓库 README、Hugging Face 模型卡、BreezeBlue 官方博文、Fish Audio 官方博文（均为一手页面）；同类产品全景一节另引 Artificial Analysis TTS 榜单（Provider Voice Arena）与各候选 HF 模型卡；许可证条款以仓库 LICENSE 与模型卡声明为准；star / 下载量 / 榜单分数为页面快照值。
- 证据分级：**已核**＝读过一手页面；**二手**＝仅见第三方文章或聚合站；**未验证**＝无可用证据，按缺项处理。
- 结论状态：**待用户确认**（第 5.2 节 Q1–Q5 闭环前不进入实现）
- 关联调研：`docs/research/agent-harness-self-optimization.md`（Agent 侧），本篇为语音侧选型，两者独立。
- 绑定变更：无。若后续确定做语音播报/语音拣选，在该变更 `proposal.md` 中引用本篇。

---

## 0. 先说结论

**Breeze TTS 2 不能用在本仓库的交付物里**，原因是许可证，不是效果：推理代码 Apache-2.0，但**模型权重、衍生模型、自托管输出一律限研究与非商业用途**，付费订阅也**不**授予自托管输出的商业权利。这直接撞上两条硬约束：`AGENTS.md` 的「GPL/AGPL/SSPL 一票否决、零商业授权预算」，以及本仓库交付的是商业 WMS。

同价位可商用的替代已存在，且与 Breeze 有血缘关系：Breeze TTS 2 的音频 tokenizer 就是基于 **Qwen3-TTS**（Apache-2.0），而 Qwen3-TTS 自己整条模型线都是 Apache-2.0。要用，绕开 Breeze 用上游即可。

同类产品的完整对照见第 6 节；加入成本维度后的首选顺序见 6.6。

---

## 1. 需求要点（R1）

### 1.1 问题陈述

待判断三件事：

1. Breeze TTS 2 是什么，效果与工程指标是否可信；
2. 能不能进本仓库（许可证、部署成本）；
3. 如果不能，同一目标下该选谁。

### 1.2 功能范围与用途假设

> **用途尚未由用户明确**。本节按可能性列出三种场景，各自的技术要求不同，第 5 节 Q1 需闭环。许可证结论（第 4 节）与场景无关，三种场景下都成立。

| 场景 | 形态 | 关键要求 | 是否实时 |
| --- | --- | --- | --- |
| PDA 语音拣选播报 | 仓内手持终端读出库位/数量/校验 | 低延迟、噪声环境可懂、可离线、多音色区分提示音 | 是 |
| 仓库作业播报 | 打包台/分拣线工位播报、异常提示 | 音质稳定、可批量合成、中文为主 | 否 |
| 对外客服/通知 | 到货、异常电话或语音消息 | 合规、可审计、音色授权链清晰 | 是 |

注意两件事：

- **TTS 只解决「说」，不解决「听」**。语音拣选的另一半是 ASR（识别）。检索中发现 `Breeze ASR` 是联发科（MediaTek）的另一条产品线，与本文的 BreezeBlue **无关**，不可混用同一个「Breeze」判断。
- 本仓库当前无任何语音相关代码与硬件，属从零起步的新功能域。

### 1.3 关键特征

- 中文为主、英文为辅；单租户私有部署，数据不出内网的可能性需保留。
- 仓库现场是扫码枪 + PDA + 打印机，**没有 GPU**；边缘部署需额外硬件预算。
- 零商业授权预算，唯一已批准付费组件是 `hiprint` 打印客户端。
- 对延迟敏感（拣选场景要求「按下即出声」）。
- 声音克隆涉及个人声音权益与个人信息保护，若使用真人录音必须取得授权。

### 1.4 边界与非目标

**本期不做**

- 不做歌声合成、数字人、情感语音助手。
- 不做模型训练与自建 TTS 底座。
- 不评估 ASR 方案（另开调研）。
- 不因「听起来好」而选型；判定以许可证 + 部署可行性 + 中文可懂度为准。

### 1.5 现存约束

- 技术栈：Java 21 + Spring Boot 3.x 后端、Vue 3 前端、PostgreSQL；无 Python 推理服务运行时。
- 若引入 Python 侧模型，等于新增一类运行时（容器 + GPU 驱动），运维面扩大。
- 依赖准入三项：许可证、最近维护时间、能否被现有依赖替代。

---

## 2. 检索范围与方法（R2）

检索时间 2026-10-25。检索词：

| 层次 | 检索词 |
| --- | --- |
| 主候选 | `breeze tts2 github`、`Breeze TTS 2 语音合成 模型 开源` |
| 许可证核查 | `Qwen3-TTS license Apache 2.0`、`CosyVoice 3 license Apache 2.0`、`IndexTTS2 license bilibili` |
| 交叉核验 | `Breeze TTS 2 实测 限制 显存`、`Breeze TTS 2 Artificial Analysis Elo` |

方法：先读一手页面（README、模型卡、官方博文），再用第三方盘点与事实核查文交叉。检索中发现三类干扰，已排除：同名不同厂（联发科 Breeze ASR）、自媒体复述 README 的「实测」（非独立证据）、标题党式对比（「效果比 IndexTTS2 强」在原文内无对比数据）。

---

## 3. 候选与比较（R3）

### 3.1 主候选事实：Breeze TTS 2

| 项 | 内容 | 证据 |
| --- | --- | --- |
| 发布方 | BreezeBlue（商业公司，`breezeblue.ai`） | 已核 |
| 首发 | 2026-08-07 基准套件；2026-08-25 权重 + PyTorch 推理代码 | 已核 |
| 规模 | 3B 参数（HF 模型卡自述；ModelScope 显示 3.48B，两处不一致） | 已核 + 存疑 |
| 模态 | 中英双语单模型；流式；24 kHz 单声道 16-bit PCM | 已核 |
| 能力 | 音色设计（纯文本造声）、音色克隆（参考音 + 精确转写）、音色导演（克隆 + 情绪/语速指令）、内联副语言事件（`(laugh)` / `[笑]`） | 已核 |
| 延迟 | H100 预热快速路径 TTFA < 40 ms、RTF 0.32（约 3.1× 实时） | 已核（厂商自测） |
| 显存 | eager ≈ 7.7 GiB（12 GB 卡最低）；`--fast-all` ≈ 14.4 GiB（建议 24 GB） | 已核 |
| 环境 | Linux + Python ≥ 3.10 + CUDA GPU；Docker 默认 sm90（A100 需改编译参数） | 已核 |
| 仓库活跃度 | 692 star、106 fork、**16 次提交**、无 Release 区块 | 已核 |
| 生态 | HF 上月下载 13,933；4 个 Adapter、11 个 Finetune、18 个量化、16 个 Space | 已核 |
| 论文 | **未见独立 arXiv 论文**，仅 Blog + GitHub + HF 模型卡 | 未验证（检索未见，不等于不存在） |

**关键条款（决定生死的一条）**

| 对象 | 许可 | 能否商用 |
| --- | --- | --- |
| 推理代码 | Apache-2.0 | 可以 |
| 音频 tokenizer | 基于 Qwen3-TTS，Apache-2.0 | 可以 |
| **模型权重 / checkpoint / adapter / 衍生模型 / 自托管输出** | **BreezeBlue Research and Non-Commercial License** | **不可以** |

README 与模型卡都明确写了：Apache 许可**不授予**模型商用权利；付费订阅只让**通过其托管平台/API 生成的内容**可商用，不赋予开源权重与自托管输出的商业权。

### 3.2 榜单主张与核查

官方博文（2026-08-26 快照）称 Artificial Analysis Provider Voice Arena 开放权重榜第一、Quality Elo **1215**，领先第二名 90 分：

| 排名 | 模型 | 开发方 | Quality Elo |
| --- | --- | --- | --- |
| 1 | Breeze TTS 2 | BreezeBlue | 1215 |
| 2 | Fish Audio S2 Pro | Fish Audio | 1125 |
| 3 | Step Audio EditX | StepFun | 1102 |
| 4 | Voxtral TTS | Mistral | 1082 |
| 5 | Magpie-Multilingual 357M | NVIDIA | 1066 |

存在两处需要打折的地方：

1. **「第一」是分榜第一**：开放权重分榜第一，不是总榜第一。完整总榜只说「前五」，未列闭源模型分数。
2. **第三方核查数字不同**：2026-09-17 一篇独立核查文记录为 Elo 1203、总榜第 8。两次快照不同（8/26 vs 9/17），可能是榜单动态变化，也可能是引用口径不同，**无法判定谁对**。

结论：榜单优势方向可信（开放权重中确实靠前），具体分数与名次不可引用。

### 3.3 可商用替代对照

| 项目 | 权重许可 | 商用 | 规模 | 语言 | 关键事实 | 证据 |
| --- | --- | --- | --- | --- | --- | --- |
| **Qwen3-TTS**（阿里通义） | Apache-2.0 | **可以** | 0.6B / 1.7B，5 个变体（Base / CustomVoice / VoiceDesign） | 10 语种 + 方言音色 | 2026-01 开源；3 秒克隆；宣称端到端延迟约 97 ms；1.7B 实测约 3.9 GB 显存（RTX 3090） | 已核（模型卡口径）+ 二手（延迟） |
| **Fun-CosyVoice3-0.5B**（阿里 FunAudioLLM） | Apache-2.0 | **可以** | 0.5B | 多语种 + 方言 | 全栈开源（权重+训练/推理代码）；支持 vLLM 加速 | 二手（多篇一致，未读 LICENSE 原文） |
| **IndexTTS-2**（bilibili） | bilibili 模型使用许可（自定义） | **待核** | 约 5.9 GB 检查点 | 中英 | 有情绪控制；协议为自定义文件，商用条款需逐条读 | 未验证（未读到商用条款要点） |
| Fish Audio S2 Pro | 未验证 | 未验证 | 未验证 | 未验证 | 榜单第二 | 未验证 |
| **Breeze TTS 2** | 研究与非商业 | **不可以** | 3B | 中英 | 见 3.1 | 已核 |

### 3.4 六个维度上的判读

| 维度 | Breeze TTS 2 | 可商用替代 |
| --- | --- | --- |
| 功能覆盖度 | 最宽：造声 + 克隆 + 导演 + 副语言事件 + 流式，一套框架 | Qwen3-TTS 同样三合一（Base/CustomVoice/VoiceDesign 分模型）；CosyVoice3 偏克隆 |
| 效果 | 盲听偏好强（开放权重内靠前，当前名次见 6.1） | 无同口径对比数据，**未验证** |
| 许可证 | **非商用，一票否决** | Apache-2.0，可闭源商用 |
| 维护活跃度 | 16 次提交、无 Release、商业化公司主导 | Qwen3-TTS 有厂商持续投入，月下载量百万级 |
| 部署成本 | 12 GB 起、24 GB 更稳；仅 Linux + CUDA | Qwen3-TTS 1.7B 约 8 GB 卡可跑，门槛更低 |
| 与现有栈兼容 | 需新增 Python + GPU 运行时 | 同样需新增运行时，但门槛更低；或改为调用云 API |

---

## 4. 结论（R4）

**直接复用，但换候选：不采用 Breeze TTS 2，改用 Qwen3-TTS（或 Fun-CosyVoice3）。**

被否候选的落选原因：

- **Breeze TTS 2**：权重非商用许可，与「零商业授权预算 + 商业交付」直接冲突。效果再好也不进入。仅在「纯内部研究、不随产品交付」的前提下可用，本仓库不满足该前提。
- **IndexTTS-2**：自定义 bilibili 协议，商用条款未核清，按未验证处理，暂不进入候选集。

保留 Breeze 的一条价值：**它验证了「造声 + 克隆 + 导演 + 流式」可以塞进一个 3B 模型**，说明 Qwen3-TTS 0.6B/1.7B 这条路线在能力方向上不落后，无需为了「一体化」而迁就非商用条款。

**若最终用途是 PDA 语音拣选，还需补两道前置判断**（不在本篇范围，但会决定方案）：

1. 仓库现场是否具备 GPU 或边缘算力；没有则只能走云 API 或轻量小模型 CPU 推理。
2. 噪声环境下的可懂度与打断/插话处理属于系统工程（播报队列、优先级、重播），不是换个模型能解决的。

---

## 5. 风险与待确认（R5）

### 5.1 风险

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| **许可证误读** | 「开源」「开放权重」不等于可商用。Breeze 这类「代码 Apache + 权重非商用」是常见陷阱 | 选型先读 LICENSE 与模型卡，再谈效果 |
| 榜单失真 | 厂商宣发引用分榜第一、总分与名次存在第三方异议 | 不引用具体 Elo，只作方向参考 |
| 声音克隆合规 | 真人音色克隆涉及声音权益与个人信息保护；仓库播报用真人录音需书面授权 | 优先用合成音色（Voice Design），不用真人克隆 |
| 硬件与运维 | 仓内无 GPU，边缘部署要新增设备与驱动维护 | 优先评估云 API 成本；模型自托管作为备选 |
| 中文方言与噪声 | 现有候选均以普通话为主，方言音色覆盖有限；噪声环境可懂度无公开数据 | 实机试听，不以 demo 判定 |
| 数据外发 | 走云 API 意味着作业文本出内网 | 明确外发边界与脱敏规则 |

### 5.2 待用户确认

- **Q1**：用途是哪一种——PDA 语音拣选播报、工位播报、对外通知，还是别的？不同场景的技术要求差异很大。
- **Q2**：部署形态倾向——仓内边缘 GPU、自建服务器、还是云 API？这决定是否要新增 Python + GPU 运行时，也决定成本模型。
- **Q3**：是否接受为 TTS 支出（云 API 按量计费，或一台带卡服务器）？当前是零授权预算。
- **Q4**：若做语音拣选，是否同步启动 ASR 调研？TTS 只覆盖「说」的一半。
- **Q5**：本产品对外销售时，把 TTS 作为功能之一是否构成「作为服务转售」（见 6.3）？按 Fish Audio 这类社区许可，转售无论收入多少都需要单独授权，需法务确认后才可考虑该档候选。

**Q1–Q3 未闭环前，不进入实现，不开 change。**

---

## 6. 同类产品全景（同日补充）

> 采集时间 2026-10-25。榜单页面未标注自身更新日期，各模型发布日期最新可见 2026-10。
> **与第 3.2 节的关系**：3.2 引用的是厂商 8 月博文快照（Elo 1215、开放权重第一、总榜前五）；本节是当前榜单，数字已变，以本节为准。

### 6.1 榜单现状

Artificial Analysis TTS 榜单共 98 个模型，其中开放权重 **18 个**。当前前排：

| 全榜名次 | 模型 | 开放权重 | Elo | 价格（$/1M 字符） | 发布 |
| --- | --- | --- | --- | --- | --- |
| 1 | Eleven v4 Turbo | — | 1336 | 40.0 | 2026-09 |
| 3 | Qwen-Audio-3.1-TTS-Plus | — | 1307 | 19.3 | 2026-09 |
| 5 | Gemini 3.8 Flash TTS | — | 1280 | 16.5 | 2026-09 |
| **9** | **Fish Audio Drama 3 Preview** | ✔ | **1248** | 15.0 | 2026-10 |
| **11** | **Breeze TTS 2** | ✔ | **1230** | 34.0 | 2026-08 |
| 19 | Ming-3.0-tts 0.5B | ✔ | 1191 | — | 2026-10 |
| 38 | Fish Audio S2 Pro | ✔ | 1110 | 15.0 | 2026-03 |
| 57 | Kokoro 82M v1.0 | ✔ | 1066 | 0.7 | 2025-01 |
| 78 | Chatterbox | ✔ | 1025 | 25.0 | 2025-05 |

三条判读：

1. **Breeze 已被挤到开放权重第二**，Elo 1230 对 Drama 3 的 1248，差 18 分；全榜第 11。厂商「开放权重第一、领先第二名 90 分」的说法在两个月内失效。
2. **前 8 名全闭源**，开放权重只有 4 个进前 40（第 9、11、19、38 名）。开放权重整体仍落后一档。
3. **Elo 带 ±9~±20 的置信区间**，相邻名次统计上属同一档，不可据 18 分差价判定优劣。

### 6.2 可商用开源自托管（代码与权重同许可）

| 模型 | 规模 | 代码 / 权重许可 | 中文 | 能力 | 备注 | 证据 |
| --- | --- | --- | --- | --- | --- | --- |
| **Ming-3.0-tts 0.5B**（蚂蚁 inclusionAI） | 0.5B dense（Qwen2.5-0.5B 骨干；HF `Model size` 字段 1B，含 tokenizer / head） | **MIT / MIT** | 中文 WER 0.800%、SIM 0.758（Seed-TTS testset，厂商自测） | 克隆 + 声音设计 + 自由形式控制 + 14 种副语言标签；粤语、四川话；100+ 中文内置音色 | 44.1 kHz；报测环境 H800/H20、CUDA 12.4；HF 月下载 3,172 | 已核 |
| **Qwen3-TTS** | 0.6B / 1.7B，5 变体 | Apache-2.0 / Apache-2.0 | 10 语种 + 方言音色 | 克隆 + 声音设计 | 1.7B 实测约 3.9 GB 显存 | 已核（许可）+ 二手（显存） |
| **Chatterbox**（Resemble AI） | 500M（Turbo 350M / Nano 110M） | MIT / MIT | 23+ 语种 | 克隆 + 情绪夸张参数 | 输出嵌入 Perth 感知水印；仅 Nano 可 CPU | 二手 |
| **Kokoro-82M** | 82M | Apache-2.0 / Apache-2.0 | v1.1-zh 变体 103 音色 | **固定音色，无克隆** | int8 量化 92 MB；CPU 可跑；$0.7/1M | 二手 |
| Voxtral TTS 4B（Mistral） | 4B | Apache-2.0 | **不含中文**（9 语种） | 零样本克隆 | 约 3 GB 显存 | 二手 |
| VibeVoice（微软） | 1.5B / 7B | MIT | 未核 | 播客式多说话人长音频 | Elo 956 | 二手 |
| Orpheus 3B / ZONOS2 / NeuTTS Air | 3B / 8B MoE / 0.7B | Apache-2.0 / Apache-2.0 | 未核 | 低延迟流式 / 高质量克隆 / CPU GGUF 克隆 | 未实测 | 二手 |

### 6.3 有条件商用与非商用

| 模型 | 权重许可 | 结论 | 证据 |
| --- | --- | --- | --- |
| **Breeze TTS 2** | BreezeBlue Research and Non-Commercial | 不可商用 | 已核 |
| **Fish Audio Drama 3** | Fish Audio Community License v1.0：科研非商用免费；年收入 < 50 万美元免费商用；≥ 50 万需申请；**作为 TTS / 克隆 / 推理服务转售，无论收入均需许可** | 产品交付不可直接用 | 已核 |
| IndexTTS-2（bilibili） | bilibili 模型使用许可 | 待核 | 未验证 |
| F5-TTS | 代码 MIT / 权重 CC-BY-NC-4.0 | 不可商用 | 二手 |
| OpenAudio S1-mini | 权重 CC-BY-NC-SA-4.0 | 不可商用 | 二手 |
| Higgs Audio v2 / v3 | 代码 Apache / 权重为 “other” 与 Boson 研究非商用 | 不可商用 | 二手 |
| XTTS v2（Coqui） | CPML，许可链接已 404 | 不可商用，社区已建议放弃 | 二手 |

> 这一档占开放权重模型的大多数。共同上游原因是 **Emilia 数据集为 CC-BY-NC-4.0**，训练出的权重被传染；已有 Emilia-YODAS（CC-BY-4.0）这类可用语料，但仅少数模型重训过。

### 6.4 云 API

| 模型 | Elo | 价格（$/1M 字符） |
| --- | --- | --- |
| Eleven v4 Turbo | 1336 | 40.0 |
| Qwen-Audio-3.1-TTS-Plus | 1307 | 19.3 |
| Gemini 3.8 Flash TTS | 1280 | 16.5 |
| MiniMax Speech 2.8 HD | 1176 | 100.0 |
| 豆包语音合成 2.0（火山引擎） | 未进该榜 | ¥2.8/万字符（约 $39/1M） |

### 6.5 成本量级

以「每天 1 万条播报、每条 25 字」估算：25 万字/天 ≈ 750 万字/月。

| 方案 | 月成本 |
| --- | --- |
| Qwen-Audio-3.1-TTS-Plus | ≈ $145 |
| 豆包语音合成 2.0 | ≈ ¥2100 |
| 自建 GPU | 一台 24 GB 显卡整机的采购价，按此用量折算可覆盖数年；另加电费、驱动与容灾维护 |

结论：**短文本、低频次场景下按量计费的云 API 比自建 GPU 便宜一个量级**。自建只在两种情况下才成立：要求数据不出内网，或调用量足够大。

### 6.6 结论补充

第 4 节基于「许可证 + 部署成本」给出 Qwen3-TTS / CosyVoice 的结论；加入本节证据后，顺序调整为：

| 场景 | 首选 | 备选 | 理由 |
| --- | --- | --- | --- |
| 可联外网 | 云 API：Qwen-Audio-3.1-TTS-Plus | Gemini 3.8 Flash TTS（更便宜） | Elo 1307、$19.3/1M，中文最强一档；无需运维 |
| 必须离线 / 数据不出内网 | **Ming-3.0-tts 0.5B** | Qwen3-TTS 1.7B、Chatterbox | MIT 全链条、0.5B、中文 WER 0.800%、自带粤语与四川话 |
| CPU 兜底、固定句式播报 | Kokoro-82M | MeloTTS | 许可最干净、92 MB、$0.7/1M；不支持克隆，靠固定句式规避表现力短板 |

明确排除：Breeze TTS 2（权重非商用）、Drama 3（营收门槛 + 转售条款）、F5-TTS、OpenAudio S1-mini、Higgs Audio、XTTS v2。

榜单的局限也要记住：AA 只取每个模型最多 8 个代表性音色做盲听偏好，衡量的是「听起来自然」，**不等于仓库短句在噪声环境下的可懂度**，也不覆盖方言与专业词。选型最后仍需实机试听。

---

## 7. 参考链接

- Breeze TTS 2 仓库：<https://github.com/breezeblue-ai/breeze-tts>（代码 Apache-2.0，权重非商用）
- Breeze TTS 2 模型卡：<https://huggingface.co/BreezeBlue/Breeze-TTS-2>
- 官方榜单博文：<https://breezeblue.ai/zh/blog/breeze-tts-2-artificial-analysis-open-weights-no-1>
- Qwen3-TTS：GitHub / Hugging Face / ModelScope（Apache-2.0，5 个模型变体）
- CosyVoice：<https://github.com/QwenAudio/CosyVoice>（Apache-2.0）
- IndexTTS：<https://github.com/index-tts/index-tts>（bilibili 模型使用许可）
- Artificial Analysis TTS 榜单：<https://artificialanalysis.ai/text-to-speech/leaderboard>
- Ming-3.0-tts：<https://huggingface.co/inclusionAI/Ming-3.0-tts-0.5B>（MIT）、<https://github.com/inclusionAI/Ming-3.0-tts>
- Fish Audio Drama 3：<https://fish.audio/blog/fish-audio-drama-3>（Community License v1.0）、<https://huggingface.co/fishaudio/drama-3>
- Chatterbox：<https://github.com/resemble-ai/chatterbox>（MIT）
- Kokoro：<https://huggingface.co/hexgrad/Kokoro-82M>（Apache-2.0）

### 未验证项清单

- 参数量 3B 与 3.48B 的差异（HF 模型卡 vs ModelScope）。
- Breeze 榜单 Elo 的准确值与总排名（官方 8 月快照 1215/总榜前五、第三方 1203/第 8、当前榜单 1230/第 11；三处不一致，只能确认方向）。
- IndexTTS-2 商用条款的具体边界（未读协议原文要点）。
- Fish Audio S2 Pro 的许可证与规模。
- 各候选在中文噪声环境下的可懂度（无公开同口径数据，需实机试听）。
- Fun-CosyVoice3 的 Apache-2.0 仅经多篇二手一致，未读 LICENSE 原文。
- Qwen3-TTS 宣称的 97 ms 延迟未找到独立复现。
- Chatterbox、Kokoro、Voxtral、VibeVoice、Orpheus、ZONOS2、NeuTTS Air 的许可仅来自二手清单，未逐个读 LICENSE 原文。
- Ming-3.0-tts 的许可证存在冲突来源（一处写 Apache-2.0，HF 模型卡写 MIT），以模型卡为准，但未读 GitHub LICENSE 原文。
- Fish Audio Drama 3 的 Community License v1.0 全文（官方博文只给要点摘要，未读 LICENSE 原文），转售与服务化边界需法务确认。
- VibeVoice 的中文支持与质量未核。
- 云 API 的实际中文效果与限流策略未实测；豆包未进入 AA 榜单，无法横向比较。
