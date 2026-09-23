# 热转折案：DSC 基线、相变积分与扫描历史审阅器

## 架构
- Gradle(Java17) + Javalin + SQLite + 原生JS/SVG；事件溯源 + 依赖图重算
- 来源分层：InstrumentOutput / SampleMass / ScanRate / Atmosphere / TempCalibration / Tangent 各自独立版本记录
- 派生层：BaselineCandidate(多候选保留) / DerivedResult(onset,peak,endset,ΔH,误差预算, 过期状态)
- 方向：EXO_UP/EXO_DOWN 元数据驱动，UNKNOWN 不猜测；heating/cooling 不混段

## 步骤
1. 工程骨架：build.gradle、wrapper、Main、配置
2. 领域核心：单位/坐标基准、信号方向、采样、基线拟合、积分、onset/peak/endset、误差预算
3. 事件存储：schema、原子追加、折叠快照、指针切换、乐观锁冲突、迁移
4. 派生引擎：依赖图、局部失效、重算、explain
5. 离线案例包：初始档案 CSV+JSON、scenario 重放（7情形+修订+冲突）、legacy v1 fixture
6. Web：SVG 曲线可拖区间、并排基线、结果稳定身份、来源链、历史滑杆、导入导出
7. 自动化测试：12+ 测试类覆盖全部情形
8. 构建验证：./gradlew build -x test；./gradlew test；run 冒烟
