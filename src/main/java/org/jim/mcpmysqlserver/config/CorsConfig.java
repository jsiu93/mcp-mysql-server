//package org.jim.mcpmysqlserver.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.cors.CorsConfiguration;
//import org.springframework.web.cors.CorsConfigurationSource;
//import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
//import org.springframework.web.servlet.config.annotation.CorsRegistry;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
///**
// * CORS跨域配置
// * 允许前端页面访问SSE接口
// *
// * @author yangxin
// */
//@Configuration
//public class CorsConfig implements WebMvcConfigurer {
//
//    @Override
//    public void addCorsMappings(CorsRegistry registry) {
//        registry.addMapping("/mcp/**")
//                .allowedOriginPatterns("*")  // 允许所有来源
//                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
//                .allowedHeaders("*")
//                .allowCredentials(true)  // 允许携带凭证
//                .maxAge(3600);  // 预检请求缓存时间
//
//        // 为了支持EventSource，需要特别配置SSE端点
//        registry.addMapping("/**")
//                .allowedOriginPatterns("*")
//                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
//                .allowedHeaders("*")
//                .allowCredentials(true)
//                .maxAge(3600);
//    }
//
//    @Bean
//    public CorsConfigurationSource corsConfigurationSource() {
//        CorsConfiguration configuration = new CorsConfiguration();
//
//        // 允许所有来源（开发环境）
//        configuration.addAllowedOriginPattern("*");
//
//        // 允许的HTTP方法
//        configuration.addAllowedMethod("*");
//
//        // 允许的请求头
//        configuration.addAllowedHeader("*");
//
//        // 允许携带凭证
//        configuration.setAllowCredentials(true);
//
//        // 预检请求缓存时间
//        configuration.setMaxAge(3600L);
//
//        // 暴露的响应头（SSE需要）
//        configuration.addExposedHeader("Content-Type");
//        configuration.addExposedHeader("Cache-Control");
//        configuration.addExposedHeader("Connection");
//
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", configuration);
//
//        return source;
//    }
//}
