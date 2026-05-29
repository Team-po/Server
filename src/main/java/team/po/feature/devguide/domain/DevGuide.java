package team.po.feature.devguide.domain;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
	@JoinColumn(name = "project_group_id", nullable = false, unique = true)
	private ProjectGroup projectGroup;

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

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private LocalDateTime deletedAt;

	@Builder
	private DevGuide(
		ProjectGroup projectGroup,
		String overview,
		List<DevGuideContent.TechStackItem> techStack,
		List<DevGuideContent.MvpPriority> mvpPriorities,
		List<DevGuideContent.DecisionPoint> decisionPoints,
		List<DevGuideContent.Milestone> milestones
	) {
		this.projectGroup = projectGroup;
		this.overview = overview;
		this.techStack = techStack;
		this.mvpPriorities = mvpPriorities;
		this.decisionPoints = decisionPoints;
		this.milestones = milestones;
	}

	public static DevGuide create(ProjectGroup projectGroup, DevGuideContent content) {
		content.validate();

		return DevGuide.builder()
			.projectGroup(projectGroup)
			.overview(content.overview())
			.techStack(content.techStack())
			.mvpPriorities(content.mvpPriorities())
			.decisionPoints(content.decisionPoints())
			.milestones(content.milestones())
			.build();
	}

	public void update(DevGuideContent content) {
		content.validate();

		this.overview = content.overview();
		this.techStack = content.techStack();
		this.mvpPriorities = content.mvpPriorities();
		this.decisionPoints = content.decisionPoints();
		this.milestones = content.milestones();
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
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
