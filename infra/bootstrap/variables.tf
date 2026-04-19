variable "aws_region" {
  description = "Terraform state backend를 생성할 AWS 리전."
  type        = string
  default     = "ap-northeast-2"
}

variable "project" {
  description = "AWS 리소스 이름과 태그에 사용할 프로젝트 이름."
  type        = string
  default     = "teampo"
}

variable "state_bucket_name" {
  description = "Terraform remote state를 저장할 S3 버킷 이름."
  type        = string
  default     = "teampo-terraform-state-ap-northeast-2"
}

variable "lock_table_name" {
  description = "Terraform state lock에 사용할 DynamoDB 테이블 이름."
  type        = string
  default     = "teampo-terraform-locks"
}
