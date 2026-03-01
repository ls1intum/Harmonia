package de.tum.cit.aet.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that forwards non-API, non-static requests to the SPA entry point ({@code /}).
 * This allows client-side routing to work correctly with HTML5 pushState.
 */
public class SpaWebFilter extends OncePerRequestFilter {

    /**
     * Forwards the request to {@code /} if it does not match a backend path or a static resource.
     * Backend paths ({@code /api}, {@code /actuator}, etc.) and paths containing a file extension
     * are passed through to the next filter in the chain.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!path.startsWith("/api")
                && !path.startsWith("/actuator")
                && !path.startsWith("/management")
                && !path.startsWith("/time")
                && !path.startsWith("/public")
                && !path.startsWith("/git")
                && !path.contains(".")
                && path.matches("/(.*)")) {
            request.getRequestDispatcher("/").forward(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
