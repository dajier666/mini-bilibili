package com.rfid.gateway.filter;

import com.rfid.gateway.utils.JwtTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

@Configuration
@Slf4j
public class JwtAuthenticationFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private static final String[] WHITE_LIST = {
        "/api/users/login",
        "/api/users/register",
    };

    public JwtAuthenticationFilter(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    @Bean
    @Order(-1)
    public GlobalFilter authenticationFilter() {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().value();

            // 白名单路径直接放行
            if (isWhiteList(path)) {
                return chain.filter(exchange);
            }

            // 获取Authorization头
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // 提取并验证token
            String token = authHeader.substring(7);
            try {
                if (jwtTokenUtil.isTokenExpired(token)) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                // 获取用户ID并添加到请求头
                Long userId = jwtTokenUtil.getUserIDFromToken(token);
                exchange.getRequest().mutate()
                    .header("X-User-ID", userId.toString())
                    .build();

                return chain.filter(exchange);
            } catch (Exception e) {
                log.error("JWT token validation failed: {}", e.getMessage());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
        };
    }

    // 检查是否为白名单路径
    private boolean isWhiteList(String path) {
        for (String whitePath : WHITE_LIST) {
            if (path.startsWith(whitePath)) {
                return true;
            }
        }
        return false;
    }
}