output "profile_images_bucket_name" {
  description = "프로필 이미지 업로드용 S3 버킷 이름."
  value       = aws_s3_bucket.profile_images.bucket
}

output "ecr_repository_url" {
  description = "Spring Boot API 이미지를 올릴 ECR repository URL."
  value       = aws_ecr_repository.app.repository_url
}

output "cloudwatch_log_group_name" {
  description = "API 로그를 저장할 CloudWatch Log Group 이름."
  value       = aws_cloudwatch_log_group.app.name
}

output "app_instance_public_ip" {
  description = "API EC2 public IP."
  value       = aws_instance.app.public_ip
}

output "app_instance_id" {
  description = "API EC2 instance ID."
  value       = aws_instance.app.id
}

output "db_endpoint" {
  description = "RDS MySQL endpoint."
  value       = aws_db_instance.app.endpoint
}
