package com.smartdesk.ticket.security;

import com.smartdesk.ticket.entity.Department;
import com.smartdesk.ticket.exception.ApiException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.UUID;

/** Lets any controller method declare a {@link Caller} parameter. */
@Configuration
public class CallerResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Caller.class;
    }

    @Override
    public Caller resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest request, WebDataBinderFactory f) {
        String id = request.getHeader("X-User-Id");
        String rawRole = request.getHeader("X-User-Role");
        if (id == null || id.isBlank() || rawRole == null || rawRole.isBlank()) {
            throw ApiException.unauthorized("Call through the API gateway");
        }

        Role role;
        String normalizedRole = rawRole.trim().toUpperCase().replace("ROLE_", "");
        try {
            role = Role.valueOf(normalizedRole);
        } catch (IllegalArgumentException e) {
            role = Role.EMPLOYEE;
        }

        String rawDept = request.getHeader("X-User-Dept");
        Department department = null;
        if (rawDept != null && !rawDept.isBlank()) {
            try {
                department = Department.valueOf(rawDept.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                department = Department.IT;
            }
        }

        return new Caller(UUID.fromString(id.trim()), request.getHeader("X-User-Email"), request.getHeader("X-User-Name"),
                role, department);
    }
}
