package com.betman.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "wallet_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "wallet_id", nullable = false)
	private Long walletId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 20)
	private WalletTransactionType type;

	/** Always positive; the type tells whether it was a credit or a debit. */
	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "balance_after", nullable = false, precision = 12, scale = 2)
	private BigDecimal balanceAfter;

	/** Bet id for BET_STAKE / BET_PAYOUT, null otherwise. */
	@Column(name = "reference_id")
	private Long referenceId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
}
