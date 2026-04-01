package team.po.feature.match.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.po.feature.match.enums.Role;
import team.po.feature.match.enums.Status;
import team.po.feature.user.domain.Users;

import java.time.Instant;

@Entity
@Table(name = "project_request")
@NoArgsConstructor
@Getter
public class ProjectRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Users user;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "project_title")
    private String projectTitle;

    @Column(name = "project_description", columnDefinition = "TEXT")
    private String projectDescription;

    @Column(name = "project_mvp", columnDefinition = "TEXT")
    private String projectMvp;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Builder
    public ProjectRequest(Users user, Role role, String projectTitle,
                          String projectDescription, String projectMvp) {
        this.user = user;
        this.role = role;
        this.projectTitle = projectTitle;
        this.projectDescription = projectDescription;
        this.projectMvp = projectMvp;
        this.status = Status.WAITING; // default
    }

    public void cancel() {
        this.status = Status.CANCELED;
        this.canceledAt = Instant.now();
    }
}
