resource "random_id" "bucket_suffix" {
  byte_length = 4
}

locals {
  name_prefix = "${var.project}-${var.environment}"

  common_tags = {
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  profile_images_bucket_name = coalesce(
    var.profile_images_bucket_name,
    "${local.name_prefix}-${random_id.bucket_suffix.hex}-profile-images"
  )
}

resource "aws_s3_bucket" "profile_images" {
  bucket = local.profile_images_bucket_name
}

resource "aws_s3_bucket_public_access_block" "profile_images" {
  bucket = aws_s3_bucket.profile_images.id

  block_public_acls       = true
  block_public_policy     = false
  ignore_public_acls      = true
  restrict_public_buckets = false
}

data "aws_iam_policy_document" "profile_images_public_read" {
  statement {
    sid    = "PublicReadObjectsOnly"
    effect = "Allow"

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.profile_images.arn}/*"]
  }
}

resource "aws_s3_bucket_policy" "profile_images_public_read" {
  bucket = aws_s3_bucket.profile_images.id
  policy = data.aws_iam_policy_document.profile_images_public_read.json

  depends_on = [aws_s3_bucket_public_access_block.profile_images]
}

resource "aws_s3_bucket_ownership_controls" "profile_images" {
  bucket = aws_s3_bucket.profile_images.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "profile_images" {
  bucket = aws_s3_bucket.profile_images.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "profile_images" {
  bucket = aws_s3_bucket.profile_images.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "profile_images" {
  bucket = aws_s3_bucket.profile_images.id

  rule {
    id     = "cleanup-incomplete-multipart-uploads"
    status = "Enabled"

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

resource "aws_s3_bucket_cors_configuration" "profile_images" {
  bucket = aws_s3_bucket.profile_images.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "POST"]
    allowed_origins = var.profile_image_allowed_origins
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

resource "aws_ecr_repository" "app" {
  name                 = "${local.name_prefix}-api"
  image_tag_mutability = "MUTABLE"

  encryption_configuration {
    encryption_type = "AES256"
  }

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep the latest 20 images only."
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 20
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/aws/${var.project}/${var.environment}/api"
  retention_in_days = var.log_retention_days
}
