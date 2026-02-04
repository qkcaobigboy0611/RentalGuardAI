/**
 * @author qkcao
 * @date 2026/1/23 10:40
 */
package com.rental.guard.ai.domain.dto;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务类型枚举 - 防欺诈租房系统专用
 */
@Getter
public enum TaskTypeEnum {
    // ==================== 数据查询类 ====================
    // 用户相关查询
    QUERY_USER_INFO("查询用户信息", "查询用户的基本信息和注册资料", "QUERY"),
    QUERY_USER_BEHAVIOR("查询用户行为", "查询用户的行为模式和习惯", "QUERY"),
    QUERY_USER_DEVICES("查询用户设备", "查询用户使用的设备信息", "QUERY"),
    QUERY_USER_IP_HISTORY("查询用户IP历史", "查询用户的IP地址使用历史", "QUERY"),
    QUERY_USER_SOCIAL("查询用户社交", "查询用户的社交关系和网络", "QUERY"),
    QUERY_USER_CREDIT("查询用户信用", "查询用户的信用评分和历史", "QUERY"),

    // 聊天相关查询
    QUERY_CHAT_HISTORY("查询聊天记录", "查询用户的聊天历史记录", "QUERY"),
    QUERY_CHAT_KEYWORDS("查询聊天关键词", "查询聊天中的关键词和敏感词", "QUERY"),
    QUERY_CHAT_PATTERNS("查询聊天模式", "查询聊天的模式和行为特征", "QUERY"),
    QUERY_CHAT_SENTIMENT("查询聊天情感", "分析聊天的情感倾向", "QUERY"),
    QUERY_REAL_TIME_CHAT("查询实时聊天", "查询实时的聊天数据流", "QUERY"),
    QUERY_CHAT_ROOM_INFO("查询聊天室信息", "查询聊天室的基本信息和成员", "QUERY"),

    // 交易相关查询
    QUERY_TRANSACTION_HISTORY("查询交易记录", "查询用户的支付和退款记录", "QUERY"),
    QUERY_PAYMENT_METHODS("查询支付方式", "查询用户使用的支付方式", "QUERY"),
    QUERY_REFUND_HISTORY("查询退款历史", "查询用户的退款申请历史", "QUERY"),
    QUERY_FINANCIAL_RISK("查询财务风险", "查询用户的财务风险指标", "QUERY"),

    // 房源相关查询
    QUERY_PROPERTY_INFO("查询房源信息", "查询房源的详细信息", "QUERY"),
    QUERY_PROPERTY_HISTORY("查询房源历史", "查询房源的交易和租赁历史", "QUERY"),
    QUERY_LANDLORD_INFO("查询房东信息", "查询房东的基本信息和历史", "QUERY"),
    QUERY_TENANT_HISTORY("查询租客历史", "查询租客的租赁历史", "QUERY"),

    // 系统数据查询
    QUERY_SYSTEM_LOGS("查询系统日志", "查询系统操作日志", "QUERY"),
    QUERY_AUDIT_TRAIL("查询审计轨迹", "查询用户操作的审计轨迹", "QUERY"),
    QUERY_CONFIGURATION("查询配置信息", "查询系统配置参数", "QUERY"),
    QUERY_BLACKLIST("查询黑名单", "查询黑名单用户和设备", "QUERY"),

    // ==================== 数据分析类 ====================
    // 风险分析
    RISK_ANALYSIS("风险分析", "综合分析用户的风险等级", "ANALYSIS"),
    BEHAVIOR_RISK_ANALYSIS("行为风险分析", "分析用户行为的风险程度", "ANALYSIS"),
    FINANCIAL_RISK_ANALYSIS("财务风险分析", "分析用户的财务风险", "ANALYSIS"),
    SOCIAL_RISK_ANALYSIS("社交风险分析", "分析用户的社交网络风险", "ANALYSIS"),
    REAL_TIME_RISK_ANALYSIS("实时风险分析", "实时分析用户行为风险", "ANALYSIS"),
    CROSS_PLATFORM_RISK_ANALYSIS("跨平台风险分析", "分析用户在不同平台的风险", "ANALYSIS"),

    // 模式检测
    PATTERN_DETECTION("模式检测", "检测异常行为模式", "DETECTION"),
    FRAUD_PATTERN_DETECTION("欺诈模式检测", "检测欺诈行为的模式", "DETECTION"),
    ANOMALY_DETECTION("异常检测", "检测异常行为和数据", "DETECTION"),
    BEHAVIOR_CHANGE_DETECTION("行为变更检测", "检测用户行为的突然变化", "DETECTION"),
    SYBIL_ATTACK_DETECTION("女巫攻击检测", "检测多账号协同攻击", "DETECTION"),

    // 机器学习分析
    ML_ANALYSIS("机器学习分析", "使用机器学习模型进行分析", "ML"),
    ML_MODEL_PREDICTION("模型预测", "使用机器学习模型进行预测", "ML"),
    ML_FEATURE_ENGINEERING("特征工程", "为机器学习模型生成特征", "ML"),
    ML_MODEL_TRAINING("模型训练", "训练新的机器学习模型", "ML"),
    ML_MODEL_EVALUATION("模型评估", "评估机器学习模型的性能", "ML"),
    ML_ENSEMBLE_ANALYSIS("集成分析", "使用多个模型进行集成分析", "ML"),

    // ==================== 数据处理类 ====================
    // 数据预处理
    DATA_PREPROCESSING("数据预处理", "清洗和预处理原始数据", "PROCESSING"),
    DATA_CLEANING("数据清洗", "清洗数据中的错误和异常", "PROCESSING"),
    DATA_NORMALIZATION("数据标准化", "标准化数据格式和范围", "PROCESSING"),
    DATA_ENRICHMENT("数据增强", "增强数据的维度和信息", "PROCESSING"),
    DATA_ANONYMIZATION("数据匿名化", "对敏感数据进行匿名处理", "PROCESSING"),

    // 特征处理
    FEATURE_EXTRACTION("特征提取", "从数据中提取特征", "FEATURE"),
    FEATURE_SELECTION("特征选择", "选择重要的特征", "FEATURE"),
    FEATURE_TRANSFORMATION("特征转换", "转换特征表示方式", "FEATURE"),
    FEATURE_VALIDATION("特征验证", "验证特征的有效性", "FEATURE"),

    // 数据验证
    DATA_VALIDATION("数据验证", "验证数据的完整性和准确性", "VALIDATION"),
    DATA_CONSISTENCY_CHECK("数据一致性检查", "检查数据的一致性", "VALIDATION"),
    DATA_COMPLETENESS_CHECK("数据完整性检查", "检查数据的完整性", "VALIDATION"),
    DATA_ACCURACY_CHECK("数据准确性检查", "检查数据的准确性", "VALIDATION"),

    // ==================== 批量处理类 ====================
    // 批量分析
    BATCH_ANALYSIS("批量分析", "批量分析多条记录的风险", "BATCH"),
    BATCH_ANALYSIS_SUBTASK("批量分析子任务", "批量分析的子任务单元", "BATCH"),
    BATCH_DATA_PROCESSING("批量数据处理", "批量处理数据记录", "BATCH"),
    BATCH_RISK_SCORING("批量风险评分", "批量计算风险评分", "BATCH"),
    BATCH_PATTERN_MATCHING("批量模式匹配", "批量匹配行为模式", "BATCH"),

    // 批量导入导出
    BATCH_DATA_IMPORT("批量数据导入", "批量导入数据文件", "BATCH"),
    BATCH_DATA_EXPORT("批量数据导出", "批量导出分析结果", "BATCH"),
    BATCH_REPORT_GENERATION("批量报告生成", "批量生成分析报告", "BATCH"),

    // ==================== 报告生成类 ====================
    // 报告生成
    GENERATE_RISK_REPORT("生成风险报告", "生成用户风险分析报告", "REPORT"),
    GENERATE_INVESTIGATION_REPORT("生成调查报告", "生成详细用户调查报告", "REPORT"),
    GENERATE_DAILY_REPORT("生成日报", "生成每日风险日报", "REPORT"),
    GENERATE_WEEKLY_REPORT("生成周报", "生成每周风险周报", "REPORT"),
    GENERATE_MONTHLY_REPORT("生成月报", "生成每月风险月报", "REPORT"),
    GENERATE_AD_HOC_REPORT("生成专项报告", "生成专项分析报告", "REPORT"),

    // 告警生成
    GENERATE_RISK_ALERT("生成风险告警", "生成风险告警通知", "ALERT"),
    GENERATE_FRAUD_ALERT("生成欺诈告警", "生成欺诈行为告警", "ALERT"),
    GENERATE_SYSTEM_ALERT("生成系统告警", "生成系统异常告警", "ALERT"),
    GENERATE_PERFORMANCE_ALERT("生成性能告警", "生成系统性能告警", "ALERT"),

    // 摘要生成
    GENERATE_SUMMARY("生成摘要", "生成分析结果摘要", "SUMMARY"),
    GENERATE_EXECUTIVE_SUMMARY("生成执行摘要", "生成给管理层的摘要", "SUMMARY"),
    GENERATE_ACTION_ITEMS("生成行动项", "生成需要采取的行动项", "SUMMARY"),

    // 可视化生成
    GENERATE_VISUALIZATION("生成可视化", "生成数据可视化图表", "VISUALIZATION"),
    GENERATE_DASHBOARD("生成仪表盘", "生成数据仪表盘", "VISUALIZATION"),
    GENERATE_HEATMAP("生成热力图", "生成行为热力图", "VISUALIZATION"),
    GENERATE_TIMELINE("生成时间线", "生成事件时间线图", "VISUALIZATION"),

    // ==================== 规则引擎类 ====================
    // 规则配置
    RULE_CONFIGURATION("规则配置", "配置风险检测规则", "RULES"),
    RULE_VALIDATION("规则验证", "验证规则的有效性", "RULES"),
    RULE_OPTIMIZATION("规则优化", "优化现有规则", "RULES"),
    RULE_DEPLOYMENT("规则部署", "部署新的风险规则", "RULES"),
    RULE_TESTING("规则测试", "测试规则的效果", "RULES"),

    // 规则执行
    RULE_EXECUTION("规则执行", "执行风险检测规则", "RULES"),
    RULE_EVALUATION("规则评估", "评估规则的命中情况", "RULES"),
    COMPLEX_RULE_EXECUTION("复杂规则执行", "执行复杂的组合规则", "RULES"),
    REAL_TIME_RULE_EXECUTION("实时规则执行", "实时执行风险规则", "RULES"),

    // ==================== 系统运维类 ====================
    // 系统检查
    SYSTEM_HEALTH_CHECK("系统健康检查", "检查系统运行状态", "SYSTEM"),
    PERFORMANCE_MONITORING("性能监控", "监控系统性能指标", "SYSTEM"),
    RESOURCE_MONITORING("资源监控", "监控系统资源使用", "SYSTEM"),
    SECURITY_AUDIT("安全审计", "执行安全审计检查", "SYSTEM"),

    // 数据管理
    DATA_BACKUP("数据备份", "备份系统数据", "DATA_MGMT"),
    DATA_RESTORE("数据恢复", "恢复系统数据", "DATA_MGMT"),
    DATA_ARCHIVING("数据归档", "归档历史数据", "DATA_MGMT"),
    DATA_CLEANUP("数据清理", "清理过期数据", "DATA_MGMT"),
    DATA_MIGRATION("数据迁移", "迁移数据到新系统", "DATA_MGMT"),

    // 系统维护
    SYSTEM_UPDATE("系统更新", "更新系统组件和配置", "MAINTENANCE"),
    DEPENDENCY_UPDATE("依赖更新", "更新系统依赖库", "MAINTENANCE"),
    CONFIGURATION_UPDATE("配置更新", "更新系统配置", "MAINTENANCE"),

    // ==================== 交互通信类 ====================
    // 通知发送
    SEND_EMAIL_NOTIFICATION("发送邮件通知", "发送邮件通知给用户", "NOTIFICATION"),
    SEND_SMS_NOTIFICATION("发送短信通知", "发送短信通知给用户", "NOTIFICATION"),
    SEND_PUSH_NOTIFICATION("发送推送通知", "发送推送通知", "NOTIFICATION"),
    SEND_IN_APP_NOTIFICATION("发送应用内通知", "发送应用内消息", "NOTIFICATION"),
    SEND_WEBHOOK_NOTIFICATION("发送Webhook通知", "发送Webhook通知到外部系统", "NOTIFICATION"),

    // 用户交互
    REQUEST_USER_CONFIRMATION("请求用户确认", "请求用户确认操作", "INTERACTION"),
    COLLECT_USER_FEEDBACK("收集用户反馈", "收集用户的反馈意见", "INTERACTION"),
    USER_VERIFICATION("用户验证", "验证用户身份", "INTERACTION"),
    CAPTCHA_VALIDATION("验证码验证", "验证用户输入的验证码", "INTERACTION"),

    // ==================== 实时监控类 ====================
    // 实时监控
    REAL_TIME_MONITORING("实时监控", "实时监控用户行为", "MONITORING"),
    REAL_TIME_CHAT_MONITORING("实时聊天监控", "实时监控聊天内容", "MONITORING"),
    REAL_TIME_TRANSACTION_MONITORING("实时交易监控", "实时监控交易行为", "MONITORING"),
    REAL_TIME_SYSTEM_MONITORING("实时系统监控", "实时监控系统状态", "MONITORING"),

    // 事件处理
    REAL_TIME_EVENT_PROCESSING("实时事件处理", "处理实时事件", "MONITORING"),
    REAL_TIME_ALERT_PROCESSING("实时告警处理", "处理实时告警", "MONITORING"),
    REAL_TIME_DATA_STREAMING("实时数据流处理", "处理实时数据流", "MONITORING"),

    // ==================== 用户调查类 ====================
    // 调查任务
    USER_INVESTIGATION("用户调查", "完整的用户调查流程", "INVESTIGATION"),
    COMPREHENSIVE_INVESTIGATION("全面调查", "全面调查用户所有维度", "INVESTIGATION"),
    TARGETED_INVESTIGATION("定向调查", "针对特定问题的调查", "INVESTIGATION"),
    CROSS_REFERENCE_INVESTIGATION("交叉引用调查", "交叉验证用户信息", "INVESTIGATION"),

    // 证据收集
    EVIDENCE_COLLECTION("证据收集", "收集调查证据", "INVESTIGATION"),
    EVIDENCE_ANALYSIS("证据分析", "分析收集到的证据", "INVESTIGATION"),
    EVIDENCE_VALIDATION("证据验证", "验证证据的真实性", "INVESTIGATION"),

    // ==================== 工具执行类 ====================
    // 工具执行
    EXECUTE_EXTERNAL_TOOL("执行外部工具", "执行外部工具或服务", "TOOL"),
    EXECUTE_SCRIPT("执行脚本", "执行预定义脚本", "TOOL"),
    EXECUTE_API_CALL("执行API调用", "调用外部API服务", "TOOL"),
    EXECUTE_DATABASE_QUERY("执行数据库查询", "执行数据库查询操作", "TOOL"),

    // ==================== 控制流类 ====================
    // 流程控制
    WAIT_CONDITION("等待条件", "等待特定条件满足", "CONTROL"),
    DECISION_POINT("决策点", "根据条件选择执行路径", "CONTROL"),
    LOOP_TASK("循环任务", "循环执行子任务", "CONTROL"),
    PARALLEL_EXECUTION("并行执行", "并行执行多个任务", "CONTROL"),
    SEQUENTIAL_EXECUTION("顺序执行", "顺序执行多个任务", "CONTROL"),

    // ==================== 复合任务类 ====================
    // 复合任务（这些任务会被分解）
    COMPOSITE_INVESTIGATION("复合调查", "包含多个子任务的调查", "COMPOSITE"),
    COMPOSITE_ANALYSIS("复合分析", "包含多个分析步骤的任务", "COMPOSITE"),
    COMPOSITE_MONITORING("复合监控", "包含多个监控维度的任务", "COMPOSITE"),
    COMPOSITE_REPORTING("复合报告", "包含多个报告组件的任务", "COMPOSITE"),

    // ==================== 测试调试类 ====================
    // 测试任务
    TEST_EXECUTION("测试执行", "执行测试用例", "TEST"),
    DEBUG_TASK("调试任务", "用于调试的特殊任务", "TEST"),
    PERFORMANCE_TESTING("性能测试", "测试系统性能", "TEST"),
    INTEGRATION_TESTING("集成测试", "测试系统集成", "TEST"),

    // ==================== 通用任务类 ====================
    GENERIC_TASK("通用任务", "处理通用请求", "GENERIC"),
    CUSTOM_TASK("自定义任务", "用户自定义的任务类型", "GENERIC"),
    AD_HOC_TASK("临时任务", "临时创建的一次性任务", "GENERIC"),

    // ==================== 权限安全类 ====================
    PERMISSION_CHECK("权限检查", "检查用户权限", "SECURITY"),
    ACCESS_CONTROL("访问控制", "控制数据访问权限", "SECURITY"),
    ENCRYPTION_DECRYPTION("加解密操作", "数据加解密操作", "SECURITY"),
    TOKEN_VALIDATION("令牌验证", "验证访问令牌", "SECURITY"),

    // ==================== 集成接口类 ====================
    THIRD_PARTY_INTEGRATION("第三方集成", "与第三方系统集成", "INTEGRATION"),
    WEBHOOK_HANDLING("Webhook处理", "处理外部Webhook请求", "INTEGRATION"),
    API_GATEWAY("API网关", "API网关代理任务", "INTEGRATION"),

    // ==================== 机器学习运维类 ====================
    ML_OPS_TRAINING("MLOps训练", "机器学习模型训练运维", "ML_OPS"),
    ML_OPS_DEPLOYMENT("MLOps部署", "机器学习模型部署运维", "ML_OPS"),
    ML_OPS_MONITORING("MLOps监控", "机器学习模型监控运维", "ML_OPS"),
    ML_OPS_RETRAINING("MLOps重训练", "机器学习模型重新训练", "ML_OPS"),

    // ==================== 报告生成任务类型 ====================
    COLLECT_REPORT_DATA("收集报告数据","收集报告数据", "collect_report_data"),
    ANALYZE_REPORT_DATA("分析报告数据","分析报告数据", "analyze_report_data"),
    GENERATE_REPORT_CONTENT("生成报告内容","生成报告内容", "generate_report_content"),
    FORMAT_REPORT("格式化报告","格式化报告", "format_report"),
    EXPORT_REPORT("导出报告","导出报告", "export_report"),
    AGGREGATE_RESULTS("汇总分析结果","汇总分析结果", "aggregate_results"),


    // ==================== 未分类 ====================
    UNKNOWN_TASK("未知任务", "未定义的任务类型", "UNKNOWN");

    private final String displayName;
    private final String description;
    private final String category;

    TaskTypeEnum(String displayName, String description, String category) {
        this.displayName = displayName;
        this.description = description;
        this.category = category;
    }

    // ==================== 分类判断方法 ====================
    public boolean isQueryType() {
        return "QUERY".equals(category);
    }

    public boolean isAnalysisType() {
        return "ANALYSIS".equals(category) ||
                "DETECTION".equals(category) ||
                "ML".equals(category);
    }

    public boolean isProcessingType() {
        return "PROCESSING".equals(category) ||
                "FEATURE".equals(category) ||
                "VALIDATION".equals(category);
    }

    public boolean isBatchType() {
        return "BATCH".equals(category);
    }

    public boolean isReportType() {
        return "REPORT".equals(category) ||
                "ALERT".equals(category) ||
                "SUMMARY".equals(category) ||
                "VISUALIZATION".equals(category);
    }

    public boolean isRuleType() {
        return "RULES".equals(category);
    }

    public boolean isSystemType() {
        return "SYSTEM".equals(category) ||
                "DATA_MGMT".equals(category) ||
                "MAINTENANCE".equals(category);
    }

    public boolean isNotificationType() {
        return "NOTIFICATION".equals(category) ||
                "INTERACTION".equals(category);
    }

    public boolean isMonitoringType() {
        return "MONITORING".equals(category);
    }

    public boolean isInvestigationType() {
        return "INVESTIGATION".equals(category);
    }

    public boolean isToolType() {
        return "TOOL".equals(category);
    }

    public boolean isControlType() {
        return "CONTROL".equals(category);
    }

    public boolean isCompositeType() {
        return "COMPOSITE".equals(category) ||
                this == USER_INVESTIGATION ||
                this == BATCH_ANALYSIS ||
                this == REAL_TIME_MONITORING;
    }

    public boolean isSecurityType() {
        return "SECURITY".equals(category);
    }

    public boolean isIntegrationType() {
        return "INTEGRATION".equals(category);
    }

    public boolean isMLOpsType() {
        return "ML_OPS".equals(category);
    }

    public boolean isTestType() {
        return "TEST".equals(category);
    }

    // ==================== 静态工具方法 ====================

    /**
     * 根据名称查找任务类型
     */
    public static TaskTypeEnum fromName(String name) {
        try {
            return TaskTypeEnum.valueOf(name);
        } catch (IllegalArgumentException e) {
            return UNKNOWN_TASK;
        }
    }

    /**
     * 根据分类获取所有任务类型
     */
    public static List<TaskTypeEnum> getByCategory(String category) {
        return Arrays.stream(values())
                .filter(type -> type.category.equals(category))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有查询类任务
     */
    public static List<TaskTypeEnum> getAllQueryTypes() {
        return getByCategory("QUERY");
    }

    /**
     * 获取所有分析类任务
     */
    public static List<TaskTypeEnum> getAllAnalysisTypes() {
        List<TaskTypeEnum> result = new ArrayList<>();
        result.addAll(getByCategory("ANALYSIS"));
        result.addAll(getByCategory("DETECTION"));
        result.addAll(getByCategory("ML"));
        return result;
    }

    /**
     * 获取所有需要实时处理的任务
     */
    public static List<TaskTypeEnum> getRealTimeTypes() {
        return Arrays.stream(values())
                .filter(type -> type.displayName.contains("实时") ||
                        type.displayName.contains("Real Time"))
                .collect(Collectors.toList());
    }

    /**
     * 获取高风险任务（需要更多资源和监控）
     */
    public static List<TaskTypeEnum> getHighRiskTypes() {
        return Arrays.asList(
                USER_INVESTIGATION,
                COMPREHENSIVE_INVESTIGATION,
                REAL_TIME_MONITORING,
                ML_MODEL_TRAINING,
                DATA_MIGRATION,
                SYSTEM_UPDATE
        );
    }

    /**
     * 获取任务类型的预估时间（秒）
     */
    public int getEstimatedDuration() {
        switch (this) {
            // 快速任务（<10秒）
            case QUERY_USER_INFO:
            case QUERY_BLACKLIST:
            case PERMISSION_CHECK:
            case TOKEN_VALIDATION:
                return 5;

            // 中等任务（10-30秒）
            case QUERY_CHAT_HISTORY:
            case QUERY_TRANSACTION_HISTORY:
            case RISK_ANALYSIS:
            case DATA_VALIDATION:
                return 20;

            // 长时间任务（30-120秒）
            case USER_INVESTIGATION:
            case BATCH_ANALYSIS:
            case ML_ANALYSIS:
            case GENERATE_RISK_REPORT:
                return 60;

            // 超长时间任务（>120秒）
            case COMPREHENSIVE_INVESTIGATION:
            case BATCH_DATA_IMPORT:
            case ML_MODEL_TRAINING:
            case DATA_MIGRATION:
                return 300;

            // 持续任务
            case REAL_TIME_MONITORING:
            case REAL_TIME_CHAT_MONITORING:
                return 3600; // 1小时

            default:
                return 30;
        }
    }

    /**
     * 获取任务的默认超时时间（秒）
     */
    public int getDefaultTimeout() {
        int estimated = getEstimatedDuration();
        // 超时时间为预估时间的3倍，最少30秒
        return Math.max(estimated * 3, 30);
    }

    /**
     * 获取任务的默认优先级
     */
    public int getDefaultPriority() {
        if (isRealTimeType()) {
            return 9; // 实时任务高优先级
        }
        if (isInvestigationType() || isMonitoringType()) {
            return 8; // 调查和监控任务较高优先级
        }
        if (isSystemType() || isSecurityType()) {
            return 7; // 系统和安全任务中等偏高
        }
        if (isBatchType() || isReportType()) {
            return 5; // 批处理和报告任务中等
        }
        return 6; // 默认优先级
    }

    /**
     * 判断是否是实时任务
     */
    public boolean isRealTimeType() {
        return category.equals("MONITORING") ||
                displayName.contains("实时") ||
                displayName.contains("Real Time");
    }

    /**
     * 判断是否是资源密集型任务
     */
    public boolean isResourceIntensive() {
        return this == ML_MODEL_TRAINING ||
                this == BATCH_DATA_PROCESSING ||
                this == DATA_MIGRATION ||
                this == ML_ENSEMBLE_ANALYSIS;
    }

    /**
     * 获取任务需要的工具列表
     */
    public List<String> getRequiredTools() {
        List<String> tools = new ArrayList<>();

        switch (this) {
            // 查询类任务
            case QUERY_USER_INFO:
            case QUERY_CHAT_HISTORY:
            case QUERY_TRANSACTION_HISTORY:
                tools.add("DatabaseService");
                break;

            // 分析类任务
            case RISK_ANALYSIS:
            case BEHAVIOR_RISK_ANALYSIS:
                tools.add("RiskAnalyzer");
                tools.add("PatternMatcher");
                break;

            // 机器学习任务
            case ML_ANALYSIS:
            case ML_MODEL_PREDICTION:
                tools.add("MLModel");
                tools.add("FeatureEngine");
                break;

            // 报告生成任务
            case GENERATE_RISK_REPORT:
            case GENERATE_INVESTIGATION_REPORT:
                tools.add("ReportGenerator");
                tools.add("TemplateEngine");
                break;

            // 实时监控任务
            case REAL_TIME_MONITORING:
            case REAL_TIME_CHAT_MONITORING:
                tools.add("StreamProcessor");
                tools.add("AlertManager");
                break;

            // 批量处理任务
            case BATCH_ANALYSIS:
            case BATCH_DATA_PROCESSING:
                tools.add("BatchProcessor");
                tools.add("ResourceManager");
                break;

            default:
                tools.add("GenericExecutor");
        }

        return tools;
    }

    /**
     * 获取任务类别图标（用于UI显示）
     */
    public String getIcon() {
        switch (category) {
            case "QUERY":
                return "🔍";
            case "ANALYSIS":
            case "DETECTION":
            case "ML":
                return "📊";
            case "PROCESSING":
            case "FEATURE":
            case "VALIDATION":
                return "⚙️";
            case "BATCH":
                return "📦";
            case "REPORT":
            case "ALERT":
            case "SUMMARY":
            case "VISUALIZATION":
                return "📄";
            case "RULES":
                return "📜";
            case "SYSTEM":
            case "DATA_MGMT":
            case "MAINTENANCE":
                return "🖥️";
            case "NOTIFICATION":
            case "INTERACTION":
                return "📢";
            case "MONITORING":
                return "👁️";
            case "INVESTIGATION":
                return "🕵️";
            case "TOOL":
                return "🔧";
            case "CONTROL":
                return "🔄";
            case "SECURITY":
                return "🔒";
            case "INTEGRATION":
                return "🔗";
            case "ML_OPS":
                return "🤖";
            default:
                return "❓";
        }
    }

    /**
     * 获取任务颜色（用于UI显示）
     */
    public String getColor() {
        switch (category) {
            case "QUERY":
                return "#4CAF50"; // 绿色
            case "ANALYSIS":
                return "#2196F3"; // 蓝色
            case "DETECTION":
                return "#FF9800"; // 橙色
            case "ML":
                return "#9C27B0"; // 紫色
            case "BATCH":
                return "#795548"; // 棕色
            case "REPORT":
                return "#607D8B"; // 蓝灰色
            case "MONITORING":
                return "#F44336"; // 红色
            case "INVESTIGATION":
                return "#3F51B5"; // 靛蓝色
            case "SECURITY":
                return "#FF5722"; // 深橙色
            default:
                return "#9E9E9E"; // 灰色
        }
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", displayName, category);
    }
}
