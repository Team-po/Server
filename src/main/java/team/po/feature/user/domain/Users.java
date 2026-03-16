package team.po.feature.user.domain;

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
@Table(name = "users")
@NoArgsConstructor
@Getter
public class Users {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String password;

	@Column(name = "profile_image")
	private String profileImage;

	private String description;

	@Column(unique = true)
	private String email;

	private String nickname;

	private Integer temperature;

	private Integer level;

	@Builder
	public Users(String password, String profileImage, String description, String email, String nickname, Integer temperature, Integer level) {
		this.password = password;
		this.profileImage = profileImage;
		this.description = description;
		this.email = email;
		this.nickname = nickname;
		this.temperature = temperature;
		this.level = level;
	}

	public void editNickname(String nickname) {
		this.nickname = nickname;
	}

	public void editDescription(String description) {
		this.description = description;
	}

	public void editTemperature(Integer temperature) {
		this.temperature = temperature;
	}

	public void editLevel(Integer level) {
		this.level = level;
	}

	public void editProfileImage(String profileImage) {
		this.profileImage = profileImage;
	}

	public void editPassword(String password) {
		this.password = password;
	}
}
