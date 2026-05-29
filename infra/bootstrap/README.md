# teampo Terraform Bootstrap

이 디렉터리는 dev 환경의 S3 remote state backend를 만들기 위한 1회성 Terraform 구성입니다.

## 실행 순서

```bash
cd infra/bootstrap
AWS_PROFILE=teampo-terraform terraform init
AWS_PROFILE=teampo-terraform terraform plan
AWS_PROFILE=teampo-terraform terraform apply
```

그 다음 dev 환경을 S3 backend로 초기화합니다.

```bash
cd ../envs/dev
AWS_PROFILE=teampo-terraform terraform init -migrate-state
```

기존 local state가 있다면 `init -migrate-state`에서 S3 backend로 복사할지 묻습니다.
