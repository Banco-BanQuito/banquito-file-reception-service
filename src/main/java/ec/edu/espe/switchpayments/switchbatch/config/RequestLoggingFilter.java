package ec.edu.espe.switchpayments.switchbatch.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        log.debug("HTTP request context captured: method={}, path={}", request.getMethod(), request.getRequestURI());
        log.debug("HTTP request trace initialized: method={}, path={}", request.getMethod(), request.getRequestURI());
        log.debug("HTTP request started: method={}, path={}", request.getMethod(), request.getRequestURI());
        try {
            log.debug("HTTP request handed to application chain: method={}, path={}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            logCompletion(request, response, (System.nanoTime() - startedAt) / 1_000_000);
        } catch (ServletException | IOException | RuntimeException exception) {
            log.error("HTTP request failed: method={}, path={}", request.getMethod(), request.getRequestURI(), exception);
            throw exception;
        }
    }
    private void logCompletion(HttpServletRequest request, HttpServletResponse response, long durationMs) {
        int status = response.getStatus();
        log.debug("HTTP response status inspection: method={}, path={}, status={}", request.getMethod(), request.getRequestURI(), status);
        log.info("HTTP response available for audit: method={}, path={}, durationMs={}", request.getMethod(), request.getRequestURI(), durationMs);
        if (durationMs >= 1_000) log.warn("HTTP request exceeded observation threshold: method={}, path={}, durationMs={}", request.getMethod(), request.getRequestURI(), durationMs);
        if (status >= 500) log.error("HTTP request completed with server error: method={}, path={}, status={}, durationMs={}", request.getMethod(), request.getRequestURI(), status, durationMs);
        else if (status >= 400) log.warn("HTTP request completed with client error: method={}, path={}, status={}, durationMs={}", request.getMethod(), request.getRequestURI(), status, durationMs);
        else if (status >= 300) log.info("HTTP redirection observed: method={}, path={}, status={}, durationMs={}", request.getMethod(), request.getRequestURI(), status, durationMs);
        else if (status >= 200) log.info("HTTP request completed successfully: method={}, path={}, status={}, durationMs={}", request.getMethod(), request.getRequestURI(), status, durationMs);
        else log.warn("HTTP response status below success range: method={}, path={}, status={}, durationMs={}", request.getMethod(), request.getRequestURI(), status, durationMs);
        log.info("HTTP request lifecycle recorded: method={}, path={}, status={}", request.getMethod(), request.getRequestURI(), status);
    }
}
