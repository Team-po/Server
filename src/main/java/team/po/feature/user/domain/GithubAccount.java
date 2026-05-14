package team.po.feature.user.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "github_account")
@NoArgsConstructor
@Getter
public class GithubAccount {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private Users user;

	@Column(name = "github_user_id", nullable = false)
	private Long githubUserId;

	@Column(name = "github_username", nullable = false)
	private String githubUsername;

	@Column(name = "access_token_ciphertext", columnDefinition = "TEXT")
	private String accessTokenCiphertext;

	@Column(name = "token_type", length = 50)
	private String tokenType;

	@Column(name = "github_scopes", length = 1000)
	private String githubScopes;

	@Column(name = "connected_at", nullable = false, insertable = false, updatable = false)
	private Instant connectedAt;

	@Column(name = "token_updated_at")
	private Instant tokenUpdatedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Builder
	public GithubAccount(
		Users user,
		Long githubUserId,
		String githubUsername,
		String accessTokenCiphertext,
		String tokenType,
		String githubScopes,
		Instant tokenUpdatedAt
	) {
		this.user = user;
		this.githubUserId = githubUserId;
		this.githubUsername = githubUsername;
		this.accessTokenCiphertext = accessTokenCiphertext;
		this.tokenType = tokenType;
		this.githubScopes = githubScopes;
		this.tokenUpdatedAt = tokenUpdatedAt;
	}

	public void updateAuthorization(
		String accessTokenCiphertext,
		String tokenType,
		String githubScopes,
		Instant tokenUpdatedAt
	) {
		this.accessTokenCiphertext = accessTokenCiphertext;
		this.tokenType = tokenType;
		this.githubScopes = githubScopes;
		this.tokenUpdatedAt = tokenUpdatedAt;
	}

	public void softDelete(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}
}
