package com.betman.user;

import com.betman.common.error.NotFoundException;
import com.betman.common.error.UsernameTakenException;
import com.betman.common.web.PageResponse;
import com.betman.user.dto.CreateUserRequest;
import com.betman.user.dto.UserResponse;
import com.betman.wallet.Wallet;
import com.betman.wallet.WalletService;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserService {

	private final UserRepository userRepository;
	private final WalletService walletService;
	private final Clock clock;

	public UserService(UserRepository userRepository, WalletService walletService, Clock clock) {
		this.userRepository = userRepository;
		this.walletService = walletService;
		this.clock = clock;
	}

	@Transactional
	public UserResponse create(CreateUserRequest request) {
		if (userRepository.existsByUsername(request.username())) {
			throw new UsernameTakenException(request.username());
		}
		User user = userRepository.save(User.builder()
				.username(request.username())
				.createdAt(clock.instant())
				.build());
		Wallet wallet = walletService.createWallet(user.getId());
		log.info("User created userId={} username={} balance={}", user.getId(), user.getUsername(),
				wallet.getBalance());
		return UserResponse.from(user);
	}

	@Transactional(readOnly = true)
	public PageResponse<UserResponse> list(Pageable pageable) {
		return PageResponse.from(userRepository.findAll(pageable), UserResponse::from);
	}

	@Transactional(readOnly = true)
	public UserResponse get(Long id) {
		return userRepository.findById(id).map(UserResponse::from).orElseThrow(() -> NotFoundException.user(id));
	}

	/** Throws {@link NotFoundException} when the user does not exist. */
	@Transactional(readOnly = true)
	public void ensureExists(Long id) {
		if (!userRepository.existsById(id)) {
			throw NotFoundException.user(id);
		}
	}
}
