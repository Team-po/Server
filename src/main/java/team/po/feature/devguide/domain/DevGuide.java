package team.po.feature.devguide.domain;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.po.feature.devguide.dto.DevGuideContent;
import team.po.feature.projectgroup.domain.ProjectGroup;

@Entity
@Table(name = "project_devguide")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class DevGuide {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_group_id", nullable = false)
	private ProjectGroup projectGroup;

	@Column(name = "version_no", nullable = false)
	private int versionNo;

	@Enumerated(EnumType.STRING)
	@Column(name = "generation_type", nullable = false, length = 20)
	private DevGuideGenerationType generationType;

	@Column(name = "is_confirmed", nullable = false)
	private boolean isConfirmed;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String overview;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "json")
	private List<DevGuideContent.TechStackItem> techStack;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "json")
	private List<DevGuideContent.MvpPriority> mvpPriorities;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "json")
	private List<DevGuideContent.DecisionPoint> decisionPoints;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "json")
	private List<DevGuideContent.Milestone> milestones;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime deletedAt;

	@Builder
	private DevGuide(
		ProjectGroup projectGroup,
		int versionNo,
		DevGuideGenerationType generationType,
		boolean isConfirmed,
		String overview,
		List<DevGuideContent.TechStackItem> techStack,
		List<DevGuideContent.MvpPriority> mvpPriorities,
		List<DevGuideContent.DecisionPoint> decisionPoints,
		List<DevGuideContent.Milestone> milestones
	) {
		this.projectGroup = projectGroup;
		this.versionNo = versionNo;
		this.generationType = generationType;
		this.isConfirmed = isConfirmed;
		this.overview = overview;
		this.techStack = techStack;
		this.mvpPriorities = mvpPriorities;
		this.decisionPoints = decisionPoints;
		this.milestones = milestones;
	}

	public static DevGuide create(
		ProjectGroup projectGroup,
		DevGuideContent content,
		int versionNo,
		DevGuideGenerationType generationType,
		boolean isConfirmed
	) {
		content.validate();

		return DevGuide.builder()
			.projectGroup(projectGroup)
			.versionNo(versionNo)
			.generationType(generationType)
			.isConfirmed(isConfirmed)
			.overview(content.overview())
			.techStack(content.techStack())
			.mvpPriorities(content.mvpPriorities())
			.decisionPoints(content.decisionPoints())
			.milestones(content.milestones())
			.build();
	}

	public void unconfirm() {
		this.isConfirmed = false;
	}

	public void delete() {
		this.deletedAt = LocalDateTime.now();
	}

	public DevGuideContent toContent() {
		return new DevGuideContent(
			overview,
			techStack,
			mvpPriorities,
			decisionPoints,
			milestones
		);
	}

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
