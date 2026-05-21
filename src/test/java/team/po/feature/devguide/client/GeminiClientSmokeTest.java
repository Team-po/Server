package team.po.feature.devguide.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import team.po.config.GeminiProperties;
import team.po.feature.devguide.dto.DevGuideContent;
import team.po.feature.devguide.prompt.DevGuidePromptBuilder;
import team.po.feature.devguide.prompt.DevGuideSchema;

/**
 * 실제 Gemini API와 연동되는지 수동으로 검증하는 테스트.
 * CI에서 자동 실행되지 않도록 @Disabled 처리되어 있다.
 */
@Disabled("실제 Gemini API 호출 - 수동 실행 전용")
class GeminiClientSmokeTest {

	private GeminiClient geminiClient;
	private DevGuidePromptBuilder promptBuilder;

	@BeforeEach
	void setUp() {
		String apiKey = System.getenv("GEMINI_API_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("GEMINI_API_KEY 환경변수가 설정되지 않았습니다.");
		}

		GeminiProperties properties = new GeminiProperties(
			apiKey,
			"https://generativelanguage.googleapis.com/v1beta",
			"gemini-2.5-flash",
			5,
			90,
			8192,
			0.7
		);

		// 1. 테스트 환경용 RequestFactory 설정 (Timeout 적용)
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
		requestFactory.setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds()));

		// 2. GeminiClientConfig와 동일한 스펙의 RestClient 빌드
		RestClient testRestClient = RestClient.builder()
			.baseUrl(properties.baseUrl())
			.defaultHeader("x-goog-api-key", properties.apiKey())
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.requestFactory(requestFactory)
			.build();

		// 3. 주입
		this.geminiClient = new GeminiClient(testRestClient, properties);
		this.promptBuilder = new DevGuidePromptBuilder();
	}

	@Test
	void team_po_프로젝트_가이드라인_생성() {
		String prompt = promptBuilder.build(
			"Team-po",
			"초보 개발자를 위한 팀 매칭 및 프로젝트 라이프사이클 관리 플랫폼",
			"매칭 기능, 팀 스페이스, 개발 가이드라인 자동 생성"
		);

		DevGuideContent content = geminiClient.generateDevGuide(prompt, DevGuideSchema.RESPONSE_SCHEMA);
		printContent("Team-po", content);
	}

	@Test
	void 헬스장_PT_매칭앱_가이드라인_생성() {
		String prompt = promptBuilder.build(
			"헬스장 PT 매칭 앱",
			"트레이너와 회원을 매칭하고 PT 일정을 관리하는 모바일 앱",
			"트레이너 프로필 조회, PT 예약, 일정 관리"
		);

		DevGuideContent content = geminiClient.generateDevGuide(prompt, DevGuideSchema.RESPONSE_SCHEMA);
		printContent("헬스장 PT 매칭 앱", content);
	}

	@Test
	void 독서모임_커뮤니티_가이드라인_생성() {
		String prompt = promptBuilder.build(
			"북클럽",
			"지역 기반 독서 모임을 매칭하고 모임별 토론 기록을 남기는 웹 서비스",
			"독서 모임 생성/검색, 모임 가입, 토론 게시판"
		);

		DevGuideContent content = geminiClient.generateDevGuide(prompt, DevGuideSchema.RESPONSE_SCHEMA);
		printContent("북클럽", content);
	}

	private void printContent(String label, DevGuideContent c) {
		System.out.println("\n# " + label + " 개발 가이드라인\n");
		System.out.println("## Overview");
		System.out.println(c.overview());

		System.out.println("\n## Tech Stack");
		c.techStack().forEach(item -> {
			System.out.printf("- **[%s] %s**%n", item.category(), item.recommendation());
			System.out.printf("  %s%n", item.reason());
		});

		System.out.println("\n## MVP Priorities");
		c.mvpPriorities().forEach(p -> {
			System.out.printf("### %d. %s%n", p.priority(), p.feature());
			System.out.printf("**Rationale:** %s%n", p.rationale());
			System.out.println("**Sub-features:**");
			p.subFeatures().forEach(sf -> System.out.printf("- %s%n", sf));
			System.out.println();
		});

		System.out.println("## Decision Points");
		c.decisionPoints().forEach(d -> {
			System.out.printf("### %s%n", d.topic());
			System.out.println("**Options:**");
			d.options().forEach(opt -> System.out.printf("- %s%n", opt));
			System.out.printf("**Consideration:** %s%n%n", d.consideration());
		});

		System.out.println("## Milestones");
		c.milestones().forEach(m -> {
			System.out.printf("### Week %d — %s%n", m.week(), m.goal());
			System.out.printf("- **Backend:** %s%n", m.roleTasks().backend());
			System.out.printf("- **Frontend:** %s%n", m.roleTasks().frontend());
			System.out.printf("- **Design:** %s%n%n", m.roleTasks().design());
		});

		System.out.println("---\n");
	}
}