# 参照 kotlin-preferences 的检查记录

参考：https://github.com/mole828/kotlin-preferences（2026-10-29）。采用其业务集中、机制留在边界、优先可读性的方向，不要求照搬示例项目的轮询方式或数据库选择。

## 本次落实

- 在 modeling 定义 GachaPageRequest，集中零基页码、正分页大小和 Long 偏移溢出的约束；服务和 SQL 入口都校验，HTTP 层沿用 400 错误映射。
- /gachas 交给现有 ContentNegotiation 序列化，减少手动 JSON 处理。
- 本地采集循环没有有效账号时等待；取消异常继续抛出；应用停止时取消采集 scope。

## 后续优先项

1. 统一采集业务规则。backend/src/service/GachaRecorder.kt 的 updateGacha 只取第一组绑定并检查 appCode；agent/src/Main.kt 的 resolveAccount 展开所有绑定后取首个账号，没有相同过滤。两条路径可能选出不同账号，且翻页间隔也不同。先明确目标账号和请求间隔规则，再将纯规则及采集用例收进 modeling，让后端和 Agent 注入各自 HTTP 适配器。两份 ArkNights.kt 不宜继续各自演进。
2. 明确一次采集结果的原子性。Agents.collect 依次 upsert、逐条 record、更新过期状态，目前 SQL repository 直接使用数据库对象。中途失败会保留已写部分；重试依靠唯一键去重。若要求整批结果共同成功，应在后端保存结果的边界建立事务，repository 读取事务上下文；远端采集应在事务开始前完成，避免网络请求长期占用事务。需要真实数据库验证中途失败回滚、重复写入和并发更新。当前 backend 使用 sqlx4k 1.11.0，采用参考仓库 1.12.0 的事务写法前需检查兼容性。
3. 将 User 作为明确的 HTTP 输出。/users 当前直接发送 ArkNights.AccountInfo，前端只使用 modeling.User 的 uid 和 nickName。可在边界映射成共享响应，避免官网字段变化影响客户端；先确认是否有其他调用方依赖完整字段。
4. 整理前端客户端边界。webui/src/Component.kt 同时处理 URL、JSON、注册流程和 UI；可提取共享的 HTTP 调用接口与 Ktor 实现，组件保留界面状态。HttpClient 的创建和关闭由应用生命周期负责；错误提示和用户列表刷新失败应分别处理，避免注册成功后刷新失败被显示成提交失败。

## 性能改进的前提

当前没有查询耗时、数据量或采集吞吐的测量证据，不先改成批量 SQL、游标分页或增加索引。需要时记录全局及单用户分页的 EXPLAIN ANALYZE、写入耗时后再决定。若替换实现以优化性能，保留明确命名且标记弃用的可读参考实现，并共享正确性测试。
