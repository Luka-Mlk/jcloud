package me.jcloud.app.security;
 
 import jakarta.servlet.FilterChain;
 import jakarta.servlet.ServletException;
 import jakarta.servlet.http.HttpServletRequest;
 import jakarta.servlet.http.HttpServletResponse;
 import org.springframework.beans.factory.annotation.Qualifier;
 import org.springframework.stereotype.Component;
 import org.springframework.web.filter.OncePerRequestFilter;
 import org.springframework.web.servlet.HandlerExceptionResolver;
 
 import java.io.IOException;
 import java.util.UUID;
 
 @Component
 public class JwtAuthenticationFilter extends OncePerRequestFilter {
 
     private final JwtService jwtService;
     private final TokenSessionService sessionService;
     private final HandlerExceptionResolver resolver;
 
     public JwtAuthenticationFilter(JwtService jwtService, 
                                    TokenSessionService sessionService,
                                    @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
         this.jwtService = jwtService;
         this.sessionService = sessionService;
         this.resolver = resolver;
     }
 
     @Override
     protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
             throws ServletException, IOException {
 
         try {
            String path = request.getServletPath();
            if (path.startsWith("/api/v1/auth/")) {
                filterChain.doFilter(request, response);
                return;
            }

            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new me.jcloud.app.exception.UnauthorizedException("Missing or invalid Authorization header");
            }

            String jwt = authHeader.substring(7);
            jwtService.validateToken(jwt);
            if (!sessionService.isSessionActive(jwt)) {
                throw new me.jcloud.app.exception.UnauthorizedException("Session has expired or is invalid");
            }
            sessionService.refreshSession(jwt, TokenSessionService.SESSION_TTL);
            String userId = jwtService.extractUserId(jwt);
            request.setAttribute("authenticatedUserId", UUID.fromString(userId));

            org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    userId, null, java.util.Collections.emptyList());
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            resolver.resolveException(request, response, null, ex);
        }
    }
}
