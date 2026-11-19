package com.mycompany.orbitaacademica.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Protege las áreas por rol y rechaza POST enviados desde otros sitios
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object h) throws Exception {

                if ("POST".equals(req.getMethod())) {
                    String origen = req.getHeader("Origin");
                    String host = req.getHeader("Host");
                    if (origen != null && host != null && !origen.endsWith("://" + host)) {
                        res.sendError(403);
                        return false;
                    }
                }

                String ruta = req.getRequestURI();
                if (ruta.startsWith("/archivo-privado/")) {
                    HttpSession sesion = req.getSession(false);
                    if (sesion == null || sesion.getAttribute("usuario") == null) {
                        res.sendRedirect("/login");
                        return false;
                    }
                }
                String necesita = ruta.startsWith("/admin") ? "ADMIN"
                        : ruta.startsWith("/docente") ? "DOCENTE"
                        : ruta.startsWith("/estudiante") ? "ESTUDIANTE" : null;

                if (necesita != null) {
                    HttpSession s = req.getSession(false);
                    Object rol = s == null ? null : s.getAttribute("rol");
                    if (!necesita.equals(rol)) {
                        res.sendRedirect("/login");
                        return false;
                    }
                }
                return true;
            }
        }).addPathPatterns("/**").excludePathPatterns("/css/**", "/img/**", "/error");
    }
}
