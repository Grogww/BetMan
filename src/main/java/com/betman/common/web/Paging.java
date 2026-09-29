package com.betman.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Builds a {@link Pageable} from {@code ?page=&size=} enforcing the maximum page size. */
public final class Paging {

	public static final String DEFAULT_SIZE = "20";
	public static final int MAX_SIZE = 100;

	private Paging() {
	}

	public static Pageable of(int page, int size, Sort sort) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
		return PageRequest.of(safePage, safeSize, sort);
	}
}
