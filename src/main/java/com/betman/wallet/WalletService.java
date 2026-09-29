package com.betman.wallet;

import com.betman.common.error.InsufficientBalanceException;
import com.betman.common.error.LimitExceededException;
import com.betman.common.error.NotFoundException;
import com.betman.common.money.Money;
import com.betman.common.web.PageResponse;
import com.betman.config.LimitsProperties;
import com.betman.wallet.dto.WalletResponse;
import com.betman.wallet.dto.WalletTransactionResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single place where a wallet balance changes. Every change writes exactly one
 * {@link WalletTransaction} in the same database transaction.
 */
@Slf4j
@Service
public class WalletService {

	private final WalletRepository walletRepository;
	private final WalletTransactionRepository transactionRepository;
	private final LimitsProperties limits;
	private final Clock clock;

	public WalletService(WalletRepository walletRepository, WalletTransactionRepository transactionRepository,
			LimitsProperties limits, Clock clock) {
		this.walletRepository = walletRepository;
		this.transactionRepository = transactionRepository;
		this.limits = limits;
		this.clock = clock;
	}

	/** Creates the wallet of a new user credited with the configured welcome bonus. */
	@Transactional
	public Wallet createWallet(Long userId) {
		Wallet wallet = walletRepository.save(Wallet.builder()
				.userId(userId)
				.balance(Money.round(BigDecimal.ZERO))
				.updatedAt(clock.instant())
				.build());
		BigDecimal bonus = Money.round(limits.welcomeBonus());
		if (bonus.signum() > 0) {
			apply(wallet, WalletTransactionType.WELCOME_BONUS, bonus, null);
		}
		return wallet;
	}

	@Transactional(readOnly = true)
	public WalletResponse get(Long userId) {
		return WalletResponse.from(requireWallet(userId));
	}

	@Transactional(readOnly = true)
	public BigDecimal getBalance(Long userId) {
		return requireWallet(userId).getBalance();
	}

	@Transactional
	public WalletResponse deposit(Long userId, BigDecimal rawAmount) {
		BigDecimal amount = Money.round(rawAmount);
		if (amount.compareTo(limits.minDeposit()) < 0 || amount.compareTo(limits.maxDeposit()) > 0) {
			throw new LimitExceededException("O depósito deve estar entre " + Money.format(limits.minDeposit())
					+ " e " + Money.format(limits.maxDeposit()) + ".");
		}
		Wallet wallet = requireWallet(userId);
		apply(wallet, WalletTransactionType.DEPOSIT, amount, null);
		log.info("Deposit made userId={} amount={} balance={}", userId, amount, wallet.getBalance());
		return WalletResponse.from(wallet);
	}

	@Transactional
	public WalletResponse withdraw(Long userId, BigDecimal rawAmount) {
		BigDecimal amount = Money.round(rawAmount);
		if (amount.compareTo(limits.minWithdraw()) < 0) {
			throw new LimitExceededException("O saque mínimo é " + Money.format(limits.minWithdraw()) + ".");
		}
		Wallet wallet = requireWallet(userId);
		if (wallet.getBalance().compareTo(amount) < 0) {
			throw new InsufficientBalanceException(wallet.getBalance(), amount, "o valor do saque");
		}
		apply(wallet, WalletTransactionType.WITHDRAW, amount, null);
		log.info("Withdraw made userId={} amount={} balance={}", userId, amount, wallet.getBalance());
		return WalletResponse.from(wallet);
	}

	/** Debits the stake of a bet. Returns the balance after the debit. */
	@Transactional
	public BigDecimal debitStake(Long userId, BigDecimal stake, Long betId) {
		Wallet wallet = requireWallet(userId);
		if (wallet.getBalance().compareTo(stake) < 0) {
			throw new InsufficientBalanceException(wallet.getBalance(), stake, "o valor da aposta");
		}
		apply(wallet, WalletTransactionType.BET_STAKE, stake, betId);
		return wallet.getBalance();
	}

	/** Credits the payout of a winning bet. Returns the balance after the credit. */
	@Transactional
	public BigDecimal creditPayout(Long userId, BigDecimal payout, Long betId) {
		Wallet wallet = requireWallet(userId);
		apply(wallet, WalletTransactionType.BET_PAYOUT, payout, betId);
		return wallet.getBalance();
	}

	@Transactional(readOnly = true)
	public PageResponse<WalletTransactionResponse> transactions(Long userId, Pageable pageable) {
		Wallet wallet = requireWallet(userId);
		return PageResponse.from(transactionRepository.findByWalletId(wallet.getId(), pageable),
				WalletTransactionResponse::from);
	}

	private Wallet requireWallet(Long userId) {
		return walletRepository.findByUserId(userId).orElseThrow(() -> NotFoundException.user(userId));
	}

	/**
	 * Applies a movement to the wallet and records the matching transaction. The amount must be
	 * positive; the type decides the direction. Optimistic locking ({@code @Version}) protects
	 * against concurrent updates of the same wallet.
	 */
	private WalletTransaction apply(Wallet wallet, WalletTransactionType type, BigDecimal amount, Long referenceId) {
		if (amount.signum() <= 0) {
			throw new IllegalArgumentException("Wallet movement amount must be positive: " + amount);
		}
		Instant now = clock.instant();
		BigDecimal newBalance = type.isCredit() ? wallet.getBalance().add(amount) : wallet.getBalance().subtract(amount);
		if (newBalance.signum() < 0) {
			throw new InsufficientBalanceException(wallet.getBalance(), amount, "o valor da operação");
		}
		wallet.setBalance(Money.round(newBalance));
		wallet.setUpdatedAt(now);
		walletRepository.save(wallet);
		WalletTransaction tx = transactionRepository.save(WalletTransaction.builder()
				.walletId(wallet.getId())
				.type(type)
				.amount(amount)
				.balanceAfter(wallet.getBalance())
				.referenceId(referenceId)
				.createdAt(now)
				.build());
		log.debug("Wallet movement walletId={} type={} amount={} balanceAfter={} referenceId={}", wallet.getId(),
				type, amount, wallet.getBalance(), referenceId);
		return tx;
	}
}
