package team.po.feature.match.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.po.feature.user.domain.Users;

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

    public enum Role {
        DESIGN, BE, FE
    }

    public enum Status {
        WAITING, MATCHING, MATCHED
    }
}
