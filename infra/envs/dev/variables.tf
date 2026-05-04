variable "aws_region" {
  description = "dev 환경에서 사용할 AWS 리전."
  type        = string
  default     = "ap-northeast-2"
}

variable "project" {
  description = "AWS 리소스 이름과 태그에 사용할 프로젝트 이름."
  type        = string
  default     = "teampo"
}

variable "environment" {
  description = "배포 환경 이름."
  type        = string
  default     = "dev"
}

variable "profile_images_bucket_name" {
  description = "프로필 이미지 S3 버킷 이름. null이면 랜덤 suffix로 고유한 이름을 생성한다."
  type        = string
  default     = null
}

variable "profile_image_allowed_origins" {
  description = "브라우저 기반 presigned POST 업로드를 허용할 origin 목록."
  type        = list(string)
  default = [
    "http://localhost:3000",
    "http://localhost:5173",
    "https://team-po.cloud"
  ]
}

variable "log_retention_days" {
  description = "애플리케이션 로그를 CloudWatch Logs에 보관할 기간."
  type        = number
  default     = 14
}

variable "app_port" {
  description = "EC2에서 외부에 열어둘 HTTP 포트. 현재는 Nginx reverse proxy가 외부 80/443을 점유하고 API는 localhost:8080 및 Docker teampo 네트워크에만 노출한다."
  type        = number
  default     = 80
}

variable "app_ingress_cidr_blocks" {
  description = "API HTTP/HTTPS 포트 접근을 허용할 CIDR 목록. dev 기본값은 전체 공개이므로 운영 전에는 제한해야 한다."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "ssh_ingress_cidr_blocks" {
  description = "SSH 22번 포트 접근을 허용할 CIDR 목록. 가능하면 본인 IP/32로 제한한다."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "app_key_name" {
  description = "EC2 SSH 접속에 사용할 AWS key pair 이름. null이면 key pair를 연결하지 않는다."
  type        = string
}

variable "app_ami_id" {
  description = "API EC2에 사용할 AMI ID. null이면 최신 Amazon Linux 2023 AMI를 사용하므로 기존 인스턴스 교체가 발생할 수 있다."
  type        = string
  default     = "ami-0c003e98ceffee43e"
}

variable "app_instance_type" {
  description = "API와 Redis 컨테이너를 실행할 EC2 인스턴스 타입."
  type        = string
  default     = "t3.micro"
}

variable "app_root_volume_size" {
  description = "API EC2 root EBS 볼륨 크기(GB)."
  type        = number
  default     = 20
}

variable "db_name" {
  description = "RDS MySQL에 생성할 기본 데이터베이스 이름."
  type        = string
  default     = "teampo"
}

variable "db_username" {
  description = "RDS MySQL master username."
  type        = string
  default     = "teampoadmin"
}

variable "db_password" {
  description = "RDS MySQL master password. terraform.tfvars 또는 CLI 변수로 주입하고 Git에 커밋하지 않는다."
  type        = string
  sensitive   = true
}

variable "db_engine_version" {
  description = "RDS MySQL engine version."
  type        = string
  default     = "8.4"
}

variable "db_instance_class" {
  description = "RDS MySQL instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "RDS 초기 스토리지 크기(GB)."
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "RDS autoscaling 최대 스토리지 크기(GB)."
  type        = number
  default     = 100
}

variable "db_backup_retention_days" {
  description = "RDS 백업 보관 기간."
  type        = number
  default     = 0
}

variable "db_deletion_protection" {
  description = "RDS 삭제 방지 활성화 여부. dev는 기본 false, 운영은 true 권장."
  type        = bool
  default     = false
}

variable "db_skip_final_snapshot" {
  description = "RDS 삭제 시 final snapshot 생략 여부. dev는 기본 true, 운영은 false 권장."
  type        = bool
  default     = true
}
