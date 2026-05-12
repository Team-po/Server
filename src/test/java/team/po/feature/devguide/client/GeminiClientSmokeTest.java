package team.po.feature.devguide.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import team.po.feature.devguide.config.GeminiProperties;
import team.po.feature.devguide.dto.DevGuideContent;
import team.po.feature.devguide.prompt.DevGuidePromptBuilder;
import team.po.feature.devguide.prompt.DevGuideSchema;

/**
 * 실제 Gemini API와 연동되는지 수동으로 검증하는 테스트.
 * Spring 컨텍스트 없이 필요한 빈만 직접 생성하여 가볍게 실행한다.
 * 응답은 DB에 저장될 JSON 형태 그대로 출력하여 구조 검증을 돕는다.
 * CI에서 자동 실행되지 않도록 @Disabled 처리되어 있다.
 */
// @Disabled("실제 Gemini API 호출 - 수동 실행 전용")
class GeminiClientSmokeTest {

	private GeminiClient geminiClient;
	private DevGuidePromptBuilder promptBuilder;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		// 환경변수에서 API 키 직접 읽기
		String apiKey = System.getenv("GEMINI_API_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("GEMINI_API_KEY 환경변수가 설정되지 않았습니다.");
		}

		GeminiProperties properties = new GeminiProperties(
			apiKey,
			"https://generativelanguage.googleapis.com/v1beta",
			"gemini-3-flash-preview",
			30
		);

		this.geminiClient = new GeminiClient(properties);
		this.promptBuilder = new DevGuidePromptBuilder();

		this.objectMapper = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT);
	}

	@Test
	void team_po_프로젝트_가이드라인_생성() {
		String prompt = promptBuilder.build(
			"Team-po",
			"초보 개발자를 위한 팀 매칭 및 프로젝트 라이프사이클 관리 플랫폼",
			"매칭 기능, 팀 스페이스, 개발 가이드라인 자동 생성"
		);

		DevGuideContent content = geminiClient.generateDevGuide(prompt, DevGuideSchema.RESPONSE_SCHEMA);

		printAsJson("Team-po", content);
	}

	@Test
	void 헬스장_PT_매칭앱_가이드라인_생성() {
		String prompt = promptBuilder.build(
			"헬스장 PT 매칭 앱",
			"트레이너와 회원을 매칭하고 PT 일정을 관리하는 모바일 앱",
			"트레이너 프로필 조회, PT 예약, 일정 관리"
		);

		DevGuideContent content = geminiClient.generateDevGuide(prompt, DevGuideSchema.RESPONSE_SCHEMA);

		printAsJson("헬스장 PT 매칭 앱", content);
	}

	@Test
	void 독서모임_커뮤니티_가이드라인_생성() {
		String prompt = promptBuilder.build(
			"북클럽",
			"지역 기반 독서 모임을 매칭하고 모임별 토론 기록을 남기는 웹 서비스",
			"독서 모임 생성/검색, 모임 가입, 토론 게시판"
		);

		DevGuideContent content = geminiClient.generateDevGuide(prompt, DevGuideSchema.RESPONSE_SCHEMA);

		printAsJson("북클럽", content);
	}

	/**
	 * DevGuideContent를 들여쓰기된 JSON 문자열로 콘솔에 출력한다.
	 * 실패 시에도 테스트는 계속 진행되도록 RuntimeException으로 감싼다.
	 */
	private void printAsJson(String label, DevGuideContent content) {
		try {
			String json = objectMapper.writeValueAsString(content);
			System.out.println("\n========== [" + label + "] DevGuide JSON ==========");
			System.out.println(json);
			System.out.println("===================================================\n");
		} catch (Exception e) {
			throw new RuntimeException("DevGuideContent JSON 직렬화 실패", e);
		}
	}
}