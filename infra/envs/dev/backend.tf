terraform {
  backend "s3" {
    bucket         = "teampo-terraform-state-ap-northeast-2"
    key            = "envs/dev/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "teampo-terraform-locks"
    encrypt        = true
  }
}
