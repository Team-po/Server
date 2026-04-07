package team.po.feature.projectgroup.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.po.feature.user.domain.Users;

@Entity
@Table(name = "project_group_member")
@NoArgsConstructor
@Getter
public class ProjectGroupMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private Users user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_group_id", nullable = false)
	private ProjectGroup projectGroup;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false)
	private MemberRole memberRole;

	@Enumerated(EnumType.STRING)
	@Column(name = "group_role", nullable = false)
	private GroupRole groupRole;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	public ProjectGroupMember(
		ProjectGroup projectGroup,
		Users user,
		MemberRole memberRole,
		GroupRole groupRole
	) {
		this.projectGroup = projectGroup;
		this.user = user;
		this.memberRole = memberRole;
		this.groupRole = groupRole;
	}
}
