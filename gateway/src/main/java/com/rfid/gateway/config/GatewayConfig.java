package com.rfid.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
public class GatewayConfig {

    /**
     * 配置跨域
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        // 创建跨域配置对象
        CorsConfiguration config = new CorsConfiguration();

        // 允许所有来源访问（生产环境建议指定具体域名）
        config.setAllowedOrigins(Arrays.asList("*"));

        // 允许的HTTP方法：GET, POST, PUT, DELETE, OPTIONS
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 允许所有请求头
        config.setAllowedHeaders(Arrays.asList("*"));

        // 暴露自定义响应头X-User-ID给前端
        config.setExposedHeaders(Arrays.asList("X-User-ID"));

        // 允许携带凭证（如cookies）
        config.setAllowCredentials(true);

        // 预检请求缓存时间（单位：秒），3600秒=1小时
        config.setMaxAge(3600L);

        // 创建基于URL的跨域配置源
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // 对所有路径/**应用上述跨域配置
        source.registerCorsConfiguration("/**", config);

        // 返回CorsWebFilter实例
        return new CorsWebFilter(source);
    }
}