package com.mienmien.business.management.infrastructure.web;

import com.mienmien.business.management.domain.repository.BusinessSessionRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class BusinessWebSecurityConfiguration {

    @Bean
    public FilterRegistrationBean<BusinessSessionAuthFilter> businessSessionAuthFilter(
            BusinessSessionRepository businessSessionRepository) {
        BusinessSessionAuthFilter filter = new BusinessSessionAuthFilter(businessSessionRepository);
        FilterRegistrationBean<BusinessSessionAuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
        reg.addUrlPatterns("/api/v1/business/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }
}
