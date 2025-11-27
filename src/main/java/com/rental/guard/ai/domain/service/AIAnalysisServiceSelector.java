/**
 * @author qkcao
 * @date 2025/9/16 18:44
 */
package com.rental.guard.ai.domain.service;

import com.rental.guard.ai.config.AIConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * AI分析服务选择器 根据配置自动选择使用的AI服务
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class AIAnalysisServiceSelector {

    private final AIConfig aiConfig;
    private final ApplicationContext applicationContext;

    private AIAnalysisService selectedService;

    @PostConstruct
    public void init() {
        selectAIService();
    }

    /**
     * 获取当前选择的AI服务
     */
    public AIAnalysisService getAIAnalysisService() {
        if (selectedService == null || !selectedService.isAvailable()) {
            selectAIService();
        }
        return selectedService;
    }

    /**
     * 选择AI服务
     */
    private void selectAIService() {
        String provider = aiConfig.getProvider();

        try {
            // 根据配置选择服务
            AIAnalysisService preferredService = findServiceByProvider(provider);

            if (preferredService != null && preferredService.isAvailable()) {
                selectedService = preferredService;
                log.info("选择AI服务: {} ({})", selectedService.getServiceType(), provider);
                return;
            }

            // 如果首选服务不可用，尝试备用服务
            log.warn("首选AI服务不可用: {}, 尝试查找备用服务", provider);
            AIAnalysisService fallbackService = findAvailableService();

            if (fallbackService != null) {
                selectedService = fallbackService;
                log.info("选择备用AI服务: {}", selectedService.getServiceType());
            } else {
                log.error("没有可用的AI服务");
                selectedService = null;
            }

        } catch (Exception e) {
            log.error("选择AI服务失败", e);
            selectedService = null;
        }
    }

    /**
     * 根据provider查找服务
     */
    private AIAnalysisService findServiceByProvider(String provider) {
        try {
            switch (provider.toLowerCase()) {
                case "local":
                case "ollama":
                    if (applicationContext.containsBean("ollamaAnalysisServiceImpl")) {
                        return applicationContext.getBean("ollamaAnalysisServiceImpl", AIAnalysisService.class);
                    }
                    break;
                case "deepseek":
                    if (applicationContext.containsBean("deepseekAnalysisServiceImpl")) {
                        return applicationContext.getBean("deepseekAnalysisServiceImpl", AIAnalysisService.class);
                    }
                    break;
                default:
                    log.warn("未知的AI服务提供商: {}", provider);
                    return null;
            }
        } catch (Exception e) {
            log.debug("查找AI服务失败: {}", provider, e);
        }
        return null;
    }

    /**
     * 查找任何可用的服务
     */
    private AIAnalysisService findAvailableService() {
        try {
            // 获取所有AIAnalysisService实现
            java.util.Map<String, AIAnalysisService> services =
                    applicationContext.getBeansOfType(AIAnalysisService.class);

            for (AIAnalysisService service : services.values()) {
                if (service.isAvailable()) {
                    return service;
                }
            }
        } catch (Exception e) {
            log.error("查找可用AI服务失败", e);
        }

        return null;
    }

    /**
     * 检查当前服务是否可用
     */
    public boolean isAvailable() {
        return selectedService != null && selectedService.isAvailable();
    }

    /**
     * 获取当前服务类型
     */
    public String getServiceType() {
        return selectedService != null ? selectedService.getServiceType() : "none";
    }

    /**
     * 强制重新选择服务
     */
    public void refresh() {
        log.info("强制刷新AI服务选择");
        selectAIService();
    }
}

