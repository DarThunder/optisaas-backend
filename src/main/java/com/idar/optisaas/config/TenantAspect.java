package com.idar.optisaas.config;

import com.idar.optisaas.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TenantAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.idar.optisaas.repository.*.*(..))")
    public void enableTenantFilter() {
        Long branchId = TenantContext.getCurrentBranch();
        if (branchId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("branchFilter").setParameter("branchId", branchId);
        }
    }
}