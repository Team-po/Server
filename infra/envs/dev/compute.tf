data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-kernel-6.1-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_instance" "app" {
  ami                         = coalesce(var.app_ami_id, data.aws_ami.amazon_linux_2023.id)
  instance_type               = var.app_instance_type
  subnet_id                   = local.app_subnet_id
  vpc_security_group_ids      = [aws_security_group.app.id]
  iam_instance_profile        = aws_iam_instance_profile.app.name
  associate_public_ip_address = true
  key_name                    = var.app_key_name
  user_data_replace_on_change = true

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    encrypted   = true
    volume_size = var.app_root_volume_size
    volume_type = "gp3"
  }

  user_data = templatefile("${path.module}/templates/ec2-user-data.sh.tftpl", {
    aws_region     = var.aws_region
    log_group_name = aws_cloudwatch_log_group.app.name
  })

  tags = {
    Name = "${local.name_prefix}-api"
  }
}
