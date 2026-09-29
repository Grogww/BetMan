package com.betman.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts {@code requestId} (from {@code X-Request-Id} or generated) and {@code userId} (from
 * {@code X-User-Id}, when present) into the MDC so every log line of the request carries them.
 * The request id is echoed back in the {@code X-Request-Id} response header.
 */
@Component("betManRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {

	public static final String REQUEST_ID = "requestId";
	public static final String USER_ID = "userId";
	public static final String REQUEST_ID_HEADER = "X-Request-Id";
	public static final String USER_ID_HEADER = "X-User-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String requestId = request.getHeader(REQUEST_ID_HEADER);
		if (requestId == null || requestId.isBlank()) {
			requestId = UUID.randomUUID().toString();
		}
		String userId = request.getHeader(USER_ID_HEADER);
		try {
			MDC.put(REQUEST_ID, requestId);
			if (userId != null && !userId.isBlank()) {
				MDC.put(USER_ID, userId.trim());
			}
			response.setHeader(REQUEST_ID_HEADER, requestId);
			chain.doFilter(request, response);
		} finally {
			MDC.remove(REQUEST_ID);
			MDC.remove(USER_ID);
		}
	}
}
