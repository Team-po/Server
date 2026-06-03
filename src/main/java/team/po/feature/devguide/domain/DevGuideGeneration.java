package team.po.feature.devguide.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.po.feature.projectgroup.domain.ProjectGroup;

@Entity
@Table(name = "project_devguide_generation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class DevGuideGeneration {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_group_id", nullable = false, unique = true)
	private ProjectGroup projectGroup;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DevGuideStatus status;

	@Column(name = "max_regeneration_count", nullable = false)
	private int maxRegenerationCount;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private LocalDateTime deletedAt;

	public static DevGuideGeneration create(ProjectGroup projectGroup) {
		DevGuideGeneration entity = new DevGuideGeneration();
		entity.projectGroup = projectGroup;
		entity.status = DevGuideStatus.GENERATING;
		entity.maxRegenerationCount = 3;
		return entity;
	}

	public void startGenerating() {
		this.status = DevGuideStatus.GENERATING;
	}

	public void complete() {
		this.status = DevGuideStatus.COMPLETED;
	}

	public void fail() {
		this.status = DevGuideStatus.FAILED;
	}

	public boolean isGenerating() {
		return this.status == DevGuideStatus.GENERATING;
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