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

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Builder
	public GithubAccount(Users user, Long githubUserId, String githubUsername) {
		this.user = user;
		this.githubUserId = githubUserId;
		this.githubUsername = githubUsername;
	}

	public void reconnectUser(Users user, String githubUsername) {
		this.user = user;
		this.githubUsername = githubUsername;
	}
}
