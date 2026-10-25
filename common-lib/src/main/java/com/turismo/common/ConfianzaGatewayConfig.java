package com.turismo.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Cada microservicio la importa con @Import. Si seguridad.gateway-secret está vacío el filtro queda desactivado. */
@Configuration
public class ConfianzaGatewayConfig {

    @Bean
    FilterRegistrationBean<ConfianzaGatewayFilter> confianzaGateway(@Value("${seguridad.gateway-secret:}") String secreto) {
        FilterRegistrationBean<ConfianzaGatewayFilter> reg = new FilterRegistrationBean<>(new ConfianzaGatewayFilter(secreto));
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.setEnabled(!secreto.isBlank());
        return reg;
    }
}
