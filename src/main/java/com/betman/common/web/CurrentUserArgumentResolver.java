package com.betman.common.web;

import com.betman.common.error.RequestValidationException;
import com.betman.user.UserService;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

	private final UserService userService;

	public CurrentUserArgumentResolver(UserService userService) {
		this.userService = userService;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(CurrentUser.class)
				&& Long.class.equals(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		String header = webRequest.getHeader(RequestContextFilter.USER_ID_HEADER);
		if (header == null || header.isBlank()) {
			throw new RequestValidationException(RequestContextFilter.USER_ID_HEADER,
					"O header X-User-Id é obrigatório.");
		}
		long userId;
		try {
			userId = Long.parseLong(header.trim());
		} catch (NumberFormatException ex) {
			throw new RequestValidationException(RequestContextFilter.USER_ID_HEADER,
					"O header X-User-Id deve ser um id numérico.");
		}
		userService.ensureExists(userId);
		return userId;
	}
}
