# teampo dev Terraform

이 환경은 초기 부트스트랩 단계에서 local Terraform state를 사용합니다.

생성 대상:

- EC2: Spring Boot API와 Redis 컨테이너 실행
- RDS MySQL: 애플리케이션 데이터베이스
- S3: 프로필 이미지 업로드 저장소
- ECR: API Docker 이미지 저장소
- CloudWatch Logs: 애플리케이션/Redis 로그 저장소
- Security Group/IAM: 위 리소스를 연결하기 위한 기본 인프라

네트워크는 AWS 계정의 Default VPC와 Default Subnet을 사용합니다.

## 사전 준비

- AWS CLI profile `teampo-terraform` 설정이 필요합니다.
- `teampo-terraform`은 Terraform 배포용 IAM Role을 Assume해야 합니다.
- Terraform이 로컬에 설치되어 있어야 합니다.
- S3 backend를 사용하기 전에 `infra/bootstrap`을 먼저 적용해야 합니다.

## 명령어

```bash
cd infra/envs/dev
AWS_PROFILE=teampo-terraform terraform init
AWS_PROFILE=teampo-terraform terraform fmt
AWS_PROFILE=teampo-terraform terraform validate
AWS_PROFILE=teampo-terraform terraform plan -var 'db_password=<secure-password>'
```

`terraform apply`는 plan 결과를 확인한 뒤에만 실행하세요.

```bash
AWS_PROFILE=teampo-terraform terraform apply -var 'db_password=<secure-password>'
```

## 주의사항

- local state 파일은 커밋하면 안 됩니다.
- 이 환경은 S3 backend를 사용합니다. `infra/bootstrap` 적용 전에는 `terraform init`이 실패합니다.
- 애플리케이션 EC2에는 S3 업로드/삭제용 IAM Role이 연결됩니다. 운영 배포에서는 장기 S3 Access Key를 주입하지 말고 인스턴스 프로파일 기반 인증을 사용하세요.
- EC2는 SSH 22번 포트를 엽니다. `app_key_name`에 AWS key pair 이름을 지정해야 SSH 접속이 가능합니다.
- RDS는 public 접근을 막고 EC2 보안 그룹에서만 MySQL 접속을 허용합니다. 다만 subnet은 Default VPC의 default subnet을 사용합니다.
- Redis는 EC2 내부 Docker 컨테이너로 실행되며 외부 포트를 열지 않습니다.
- `app_ingress_cidr_blocks` 기본값은 dev 편의를 위해 전체 공개입니다. 운영 전에는 필요한 CIDR로 제한하세요.
- `ssh_ingress_cidr_blocks` 기본값도 전체 공개입니다. 실제 적용 전에는 본인 IP `/32`로 제한하는 것을 권장합니다.
- S3 프로필 이미지 버킷은 객체 조회(`s3:GetObject`)를 public으로 허용합니다. 업로드/삭제 권한은 public으로 열지 않습니다.
- EC2 security group은 외부 HTTP 포트 `80`과 HTTPS 포트 `443`을 엽니다. Nginx reverse proxy가 외부 포트를 점유하고, API 컨테이너는 host `127.0.0.1:8080`과 Docker `teampo` 네트워크에만 노출한 뒤 Nginx가 `teampo-api:8080`으로 프록시하도록 구성하세요.
- RDS master password는 `db_password` 변수로 주입합니다. `terraform.tfvars`는 Git에 커밋하지 마세요.
