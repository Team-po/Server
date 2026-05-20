package team.po.feature.teamspace.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "github_installation")
@NoArgsConstructor
@Getter
public class GithubInstallation {
	public static final String ORGANIZATION_ACCOUNT_TYPE = "Organization";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "installation_id", nullable = false)
	private Long installationId;

	@Column(name = "account_id", nullable = false)
	private Long accountId;

	@Column(name = "account_login", nullable = false)
	private String accountLogin;

	@Column(name = "account_type", nullable = false)
	private String accountType;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Builder
	public GithubInstallation(
		Long installationId,
		Long accountId,
		String accountLogin,
		String accountType
	) {
		this.installationId = installationId;
		this.accountId = accountId;
		this.accountLogin = accountLogin;
		this.accountType = accountType;
	}

	public boolean isOrganization() {
		return ORGANIZATION_ACCOUNT_TYPE.equals(accountType);
	}

	public void updateAccount(String accountLogin, String accountType) {
		this.accountLogin = accountLogin;
		this.accountType = accountType;
	}

	public void softDelete(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}
}
