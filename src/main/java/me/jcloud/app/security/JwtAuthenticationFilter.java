package me.jcloud.app.security;
 
 import jakarta.servlet.FilterChain;
 import jakarta.servlet.ServletException;
 import jakarta.servlet.http.HttpServletRequest;
 import jakarta.servlet.http.HttpServletResponse;
 import org.springframework.stereotype.Component;
 import org.springframework.web.filter.OncePerRequestFilter;
 
 import java.io.IOException;
 import java.util.UUID;
 
 @Component
 public class JwtAuthenticationFilter extends OncePerRequestFilter {
 
     private final JwtService jwtService;
     private final TokenSessionService sessionService;
 
     public JwtAuthenticationFilter(JwtService jwtService, TokenSessionService sessionService) {
         this.jwtService = jwtService;
         this.sessionService = sessionService;
     }
 
     @Override
     protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
             throws ServletException, IOException {
 
         String authHeader = request.getHeader("Authorization");
 
         if (authHeader != null && authHeader.startsWith("Bearer ")) {
             String jwt = authHeader.substring(7);
             if (jwtService.isTokenValid(jwt) && sessionService.isSessionActive(jwt)) {
                 String userId = jwtService.extractUserId(jwt);
                 request.setAttribute("authenticatedUserId", UUID.fromString(userId));
 
                 org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                         userId, null, java.util.Collections.emptyList());
                 org.springframework.security.core.context.SecurityContextHolder.getContext()
                         .setAuthentication(authentication);
             }
         }
 
         filterChain.doFilter(request, response);
     }
 }
