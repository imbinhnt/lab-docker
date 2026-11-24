package com.example.demo.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Middleware (Servlet Filter): gắn X-Request-Id cho mỗi request và log
 * method, URI, status, thời gian xử lý. Chạy trước mọi controller.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final String HEADER = "X-Request-Id";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || !SAFE_ID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }

        // Correlation ID đến từ gateway/client nếu hợp lệ; nếu không, dùng
        // chính requestId để mọi log của request truy vết được với nhau.
        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || !SAFE_ID.matcher(correlationId).matches()) {
            correlationId = requestId;
        }

        MDC.put("requestId", requestId);
        MDC.put("correlationId", correlationId);
        response.setHeader(HEADER, requestId);
        response.setHeader(CORRELATION_HEADER, correlationId);

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), System.currentTimeMillis() - start);
            MDC.remove("requestId");
            MDC.remove("correlationId");
        }
    }
}
