# Codex-Written Repo Analysis: cib-payment-api

**Date:** 2026-05-29
**Project:** cib-payment-api (Spring Boot 3.5.3 + Java 21 + Maven)

---

## 项目概况

国内实时支付 API 微服务 MVP。采用六边形架构（ports-and-adapters），有三个 OpenSpec 归档的 feature：
1. `2026-05-11-add-domestic-rtp-payment-service-api` — 初始国内 RTP API
2. `2026-05-25-add-hk-iso20022-payment-simulation` — HK ISO 20022 支付模拟
3. `2026-05-29-add-fi-correspondent-rfi-workflow` — FI 代理行 RFI 工作流

---

## 架构评分：8.5/10

### 亮点

**1. 分层清晰，领域模型纯净**
- Domain 层全部是 Java `record` 和 `enum`，零框架依赖
- 应用层通过 Port 接口（17个）定义契约，基础设施层实现适配器
- `EdgeEngineBoundaryTest` 用 ArchUnit 强制执行包边界规则 — `api`/`application` 包不能直接依赖 `infrastructure.engine`

**2. 幂等性机制设计得当**
- 两种策略并存：
  - 乐观锁（`CreateDomesticPaymentService`）：依赖 `ConcurrentHashMap.putIfAbsent` 原子性，事后 double-check
  - 悲观锁 + 分桶（`CreateIsoDomesticPaymentService`, `CreateFiPaymentService`, `CreateRecallInvestigationService`）：256 路 `hash % 256` 分桶 + `synchronized` 消除 TOCTOU 窗口
- 指纹生成包含 `mockScenario`，更换场景触发 409 冲突（正确行为）
- `RecallInvestigation` 在竞态失败时调用 `idempotencyRepository.deleteIfMatches()` 清理脏数据
- 有并发测试：`CyclicBarrier` 协调 8/16 线程同时到达，验证 exactly-one 语义

**3. 安全实现规范**
- OAuth2 JWT Resource Server + scope-based 鉴权
- Scopes: `payments:create`, `payments:read`, `fi-payments:create`, `fi-payments:read`, `fi-payments:investigate`
- 自定义 `AuthenticationEntryPoint`（401 JSON）和 `AccessDeniedHandler`（403 JSON）
- `AuthorizationContextService` 从 JWT 提取完整上下文：clientId, scopes, tenantId, actor, correlationId
- `CorrelationIdFilter` 优先级最高（`HIGHEST_PRECEDENCE`），request attribute / response header / MDC 三通道同步

**4. 可观测性设计**
- `PaymentObservability` 接口定义 20+ 生命周期事件（`apiRequest`, `idempotencyReplay`, `downstreamOutcome`, `recallInvestigationCreated` 等）
- `AccountNumberMasker` 对账号、邮箱、FPS proxy、HKID、FI XML payload 做脱敏
- 提供 `PaymentObservability.noop()` 静态工厂用于测试

**5. 测试覆盖面广**
- 45+ 测试类：集成测试（`@SpringBootTest` + `MockMvc`）、服务测试、并发测试、契约测试、架构测试
- ISO 20022 消息有完整 XML fixture 文件（pain.001 × 5, pain.002 × 3, pacs.009 × 6, camt.056 × 6, camt.029 × 6）
- `JwtTestSupport` 提供过期 token、错误 audience、缺失 claim、无效签名等异常场景

**6. 模拟器体系完整**
- 4 套确定性模拟器：`MockDownstreamPaymentProcessor`（国内）→ `DeterministicHkClearingSettlementSimulator`（HK 清算）→ `DeterministicFiCorrespondentPaymentSimulator`（FI 代理行）→ `DeterministicRecallInvestigationSimulator`（Recall 调查）
- 通过 `X-Mock-Scenario` header 切换场景，测试友好

**7. ISO 20022 消息支持**
- 支持 pain.001（支付发起）、pacs.009（FI 代理行支付）、camt.056（Recall 请求）
- 输出 pain.002（状态报告）、camt.029（调查响应）
- 内容类型精确匹配（`application/pain.002+xml`, `application/pacs.009+xml`, `application/camt.029+xml`）

---

## 值得关注的问题

**1. `CreateFiPaymentService` 构造函数过多（6 个）**
- 路径：`application/service/CreateFiPaymentService.java`
- 为了支持测试做了大量构造器链式重载（with/without observability、with/without Clock），维护成本高
- 建议：用包级可见的测试构造器或 Builder 模式简化

**2. ISO 解析器用正则而非 XML Schema**
- `Pain001Parser`, `Pacs009Parser`, `Camt056Parser` 都是手写正则提取 XML 字段
- MVP 可接受，生产环境应改用 JAXB + XSD 校验确保消息合规性

**3. 类名拼写：`InternalInterbankTransfer`**
- 路径：`domain/model/InternalInterbankTransfer.java`
- `Interbank` 应为 `Interbank` 或 `InterBank`（如果是有意设计则忽略）

**4. 没有 Dockerfile**
- K8s manifests（deployment.yaml, service.yaml, gateway.yaml, healthcheck-policy.yaml）齐全但缺少 Dockerfile，部署链路不完整

**5. MVP 限制（设计如此，非缺陷）**
- 内存存储（`ConcurrentHashMap`），单副本部署
- 本地硬编码 2048-bit RSA 密钥对
- 无真实支付网关对接

---

## Codex 写代码的特征

从 repo 观察到的 Codex 生成代码模式：

| 特征 | 表现 |
|------|------|
| **偏好 record 类型** | 所有 domain model、DTO、port outcome 都是 Java record，零样板代码 |
| **接口优先** | 17 个 port 接口，每个外部依赖都有明确契约 |
| **构造函数链式重载** | 为兼顾 Spring DI 和测试注入，产生较多构造器变体 |
| **手写 XML 解析** | 不使用 JAXB，选择正则直接提取 |
| **内联测试 double** | 不使用 Mockito，偏好手写静态内部类作为 mock |
| **ConcurrentHashMap 为主** | 所有 in-memory 存储都用 ConcurrentHashMap，原子操作正确使用 |
| **synchronized 分桶** | 用 hash 分桶 + synchronized 而非 ReentrantLock 或 StampedLock |

---

## 三层业务流程

### 1. ISO 国内支付 (pain.001 → pain.002)
```
POST /v1/domestic-payments (XML)
  → AdmissionService 解析 pain.001 → IsoPaymentCandidate
  → HkPaymentEngine 生成 InternalInterbankTransfer → HK 清算模拟器
  → Pain002Renderer 渲染 pain.002 XML 返回
```

### 2. FI 代理行支付 (pacs.009 → JSON ack)
```
POST /v1/fi-payments (XML)
  → AdmissionService 解析 pacs.009 → FiPaymentCandidate
  → FiCorrespondentRouteProfile 推导路由（NOSTRO/VOSTRO/LORO）
  → FI 代理行模拟器 → FiPaymentAcknowledgementResponse (202)
```

### 3. Recall 调查 (camt.056 → camt.029)
```
POST /v1/fi-payments/{id}/recall-requests (XML)
  → 查找 FI 支付（验证 SETTLED/PROCESSING 状态）
  → 解析 camt.056 → 双重 synchronized（idempotencyLock + recallPaymentLock）
  → 调查模拟器 → Camt029Renderer 渲染 camt.029 XML (202)
```

---

## 文件统计

| 层级 | 类数量 |
|------|--------|
| Domain Model | 27 |
| Application Services | 11 |
| Application Ports | 17 |
| Application Exceptions | 7 |
| Infrastructure Adapters | 17 |
| API Controllers | 2 |
| API DTOs | 13 |
| **总计** | **~94** |

测试类：45+
