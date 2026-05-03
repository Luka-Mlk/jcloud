package me.jcloud.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenSessionService sessionService;
    private final HandlerExceptionResolver resolver;

    private static final List<String> EXACT_MATCH_PATHS = List.of(
            "/", "/login", "/register", "/dashboard", "/buckets");

    public JwtAuthenticationFilter(JwtService jwtService,
                                   TokenSessionService sessionService,
                                   @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();

        boolean isWhitelisted = EXACT_MATCH_PATHS.contains(path);
        boolean isObjectPath = path.endsWith("/objects");
        boolean isAuthPath = path.startsWith("/api/v1/auth/");

        boolean isStaticResource = path.startsWith("/css/") ||
                path.startsWith("/js/") ||
                path.startsWith("/images/") ||
                path.startsWith("/favicon.ico") ||
                path.endsWith(".css") ||
                path.endsWith(".js") ||
                path.endsWith(".png") ||
                path.endsWith(".jpg");

        return isWhitelisted || isObjectPath || isAuthPath || isStaticResource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new me.jcloud.app.exception.UnauthorizedException("Missing or invalid Authorization header");
            }

            String jwt = authHeader.substring(7);
            jwtService.validateToken(jwt);

            if (!sessionService.isSessionActive(jwt)) {
                throw new me.jcloud.app.exception.UnauthorizedException("Session has expired or is invalid");
            }

            sessionService.refreshSession(jwt, sessionService.getSessionTtl());

            String userId = jwtService.extractUserId(jwt);
            request.setAttribute("authenticatedUserId", UUID.fromString(userId));

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, Collections.emptyList());

            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            resolver.resolveException(request, response, null, ex);
        }
    }
}
