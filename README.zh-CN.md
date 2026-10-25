# vda5050-testkit

[VDA 5050](https://github.com/VDA5050/VDA5050)（调度系统与 AGV / 移动机器人之间的 MQTT 接口）的一致性检查工具。连上 broker，或者读入录下来的抓包文件，它会告诉你哪一方违反了标准、具体在哪条消息、依据的是标准哪一节。

[English](README.md)

## 为什么做这个

VDA 5050 对接中的问题，大多数不是 JSON 格式错，而是**行为**错：

- 订单更新没有从决策点（decision point）接上
- 车辆收到订单后既不执行也不拒绝
- `nodeStates` 跳过了节点
- `cancelOrder` 报了 FINISHED，路线却还在
- connection 消息没有带 retain 标志

这些问题 schema 校验发现不了，以往只能靠工程师盯着 MQTT Explorer 人工排查。

连官方 schema 本身都有问题：3.0.0 正式发布的 schema 里有尾逗号，其中三个文件根本不是合法 JSON；2.x 的 state schema 漏掉了正文里允许的 `PAUSED` 状态。所有修补都列在 [docs/schema-patches.md](docs/schema-patches.md)。

## 检查什么

共 15 条规则，每条发现都标明责任方（车端、调度方或发布者），报告可以直接转给车辆厂商或调度系统团队。完整列表见 [README.md](README.md#what-it-checks)，或者运行 `vda5050-testkit rules`。

主要分三类：

- **消息格式**：topic 结构、JSON、官方 schema（2.0.0 / 2.1.0 / 3.0.0）、header 与 topic 一致、headerId 递增、时间戳、retain 标志
- **车端行为**：上线公告、state 至少 30 秒一次、每个订单都有接受或拒绝、`nodeStates` / `edgeStates` 正确推进、动作状态只能向前走、`cancelOrder` 按标准执行
- **调度方行为**：订单结构、订单更新从决策点接上、重发内容一致

## 快速开始

需要 Java 21 及以上。

```
./mvnw package -DskipTests
java -jar vda5050-testkit-cli/target/vda5050-testkit.jar demo                   # 内存里模拟两台车和调度方，不需要 broker
java -jar vda5050-testkit-cli/target/vda5050-testkit.jar demo --with-mistakes   # 调度方故意发一个错误的订单更新
```

检查现场系统：

```
vda5050-testkit record -b tcp://broker:1883 --check -d 600 -o shift.jsonl   # 录制 10 分钟，结束后出报告
vda5050-testkit check shift.jsonl                                           # 事后检查
vda5050-testkit check --format json --report report.json shift.jsonl
```

退出码：0 表示全部通过，1 表示有规则失败，2 表示输入错误，可以直接接进 CI 或验收流程。

模拟车（没有真车时测试调度系统用）：

```
vda5050-testkit sim -b tcp://localhost:1883 -n 5
```

## 局限

- 检查器只能看到 broker 发给它的消息。QoS 0 丢的消息和根本没发的消息看起来一样，所以 headerId 跳号只报警告。
- 行为规则只跟踪录制期间发出的订单，要先开始录制，再让调度方下单。
- 行为规则主要按 2.1 开发，已经处理了 3.0 的字段名、错误类型和 `instantActionStates` 等差异；3.0 的 zone、corridor 和 request/response 机制还没有检查。
- 通过检查不等于获得认证。VDA 5050 没有官方认证，本项目与 VDA / VDMA 没有关联。

## 许可

Apache-2.0。内置的 VDA 5050 schema 由 VDA 以 MIT 许可发布，见 [NOTICE](NOTICE)。
