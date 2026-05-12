package team.po.feature.devguide.prompt;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DevGuidePromptBuilder {

	private static final String PROMPT_TEMPLATE = """
		너는 캡스톤 디자인 프로젝트를 진행하는 초보 개발자 팀에게 개발 가이드라인을 제공하는 시니어 개발자다.
		아래 프로젝트 정보를 바탕으로, 8주 안에 4인 팀(백엔드 2명, 프론트엔드 1명, 디자인 1명)이 실제로 완성할 수 있는 현실적인 가이드라인을 작성한다.
		
		## 프로젝트 정보
		- 제목: %s
		- 설명: %s
		- MVP 기능: %s
		
		## 작성 규칙
		1. 한국어로 작성한다. 단, 기술 용어(프레임워크, 라이브러리, 프로토콜 이름 등)는 영문 원문을 유지한다.
		   - 올바른 예: "Spring Boot", "Redis", "JWT"
		   - 잘못된 예: "스프링 부트", "레디스", "제이더블유티"
		2. 초보 개발자가 캡스톤 8주 안에 학습하고 적용할 수 있는 수준의 기술만 추천한다. 과도하게 복잡한 아키텍처(MSA, Kubernetes 등)는 피한다.
		3. 추상적인 표현("열심히 한다", "잘 설계한다") 대신 구체적이고 실행 가능한 표현을 쓴다.
		
		## 필드별 작성 지침
		
		### overview
		- 2~3문장으로 프로젝트의 목적과 핵심 기능을 요약한다.
		
		### techStack (5~7개)
		- category는 Backend, Frontend, Database, Cache, Storage, Infra, CI/CD 등 역할 단위로 작성한다.
		- recommendation에는 구체적인 기술명을 적는다.
		- reason은 2문장 이상으로, 왜 이 기술이 초보자 4인 팀의 8주 캡스톤에 적합한지 설명한다. 학습 자료의 풍부함, 생태계, 러닝 커브를 근거로 든다.
		
		### mvpPriorities (정확히 3개)
		- 입력받은 MVP 기능 중에서 우선순위 1~3위를 정한다.
		- priority는 1, 2, 3을 각각 정확히 한 번씩 사용한다.
		- rationale은 왜 그 순위인지 의존성과 위험도 관점에서 설명한다.
		
		### decisionPoints (3~5개)
		- 팀이 개발을 시작하기 전에 합의해야 할 결정 사항을 적는다.
		- 반드시 "패키지 구조 컨벤션" 항목을 포함하고, 도메인 중심(feature 패키지)과 레이어 중심(controller/service/repository 패키지)을 옵션으로 제시한다.
		- 그 외 항목은 인증 방식, 브랜치 전략, 코드 컨벤션 등 프로젝트 특성에 맞게 선정한다.
		- options는 2~3개의 실질적인 선택지를 제시한다.
		- consideration은 각 선택지의 트레이드오프를 2문장 이상으로 설명한다.
		
		### milestones (정확히 8개)
		- week 1부터 week 8까지 각각 하나씩, 누락이나 중복 없이 작성한다.
		- week 1은 환경 구축과 설계, week 8은 발표 준비와 배포에 할당한다.
		- goal은 해당 주차의 핵심 목표를, deliverable은 주차 종료 시점에 만들어져 있어야 할 산출물을 구체적으로 작성한다.
		
		JSON 외의 어떤 텍스트도 포함하지 마라.
		""";

	public String build(String title, String description, String mvp) {
		return PROMPT_TEMPLATE.formatted(
			sanitize(title),
			sanitize(description),
			sanitize(mvp)
		);
	}

	private String sanitize(String input) {
		if (input == null)
			return "";
		return input.replace("```", "ʼʼʼ").trim();
	}
}