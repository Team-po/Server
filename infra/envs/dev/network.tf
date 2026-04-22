data "aws_vpc" "default" {
  default = true
}

locals {
  app_subnet_id = "subnet-029d8fdf5b5421d0b"
  db_subnet_ids = [
    "subnet-0e8d71bb1aa273edb",
    "subnet-0bd39896d2bc55646"
  ]
}

resource "aws_security_group" "app" {
  name        = "${local.name_prefix}-app-sg"
  description = "Security group for the teampo API EC2 instance."
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Name = "${local.name_prefix}-app-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "app_http" {
  for_each = toset(var.app_ingress_cidr_blocks)

  security_group_id = aws_security_group.app.id
  cidr_ipv4         = each.value
  from_port         = var.app_port
  ip_protocol       = "tcp"
  to_port           = var.app_port
}

resource "aws_vpc_security_group_ingress_rule" "app_https" {
  for_each = var.app_port == 443 ? toset([]) : toset(var.app_ingress_cidr_blocks)

  security_group_id = aws_security_group.app.id
  cidr_ipv4         = each.value
  from_port         = 443
  ip_protocol       = "tcp"
  to_port           = 443
}

resource "aws_vpc_security_group_ingress_rule" "app_ssh" {
  for_each = toset(var.ssh_ingress_cidr_blocks)

  security_group_id = aws_security_group.app.id
  cidr_ipv4         = each.value
  from_port         = 22
  ip_protocol       = "tcp"
  to_port           = 22
}

resource "aws_vpc_security_group_egress_rule" "app_all" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_security_group" "db" {
  name        = "${local.name_prefix}-db-sg"
  description = "Security group for the teampo MySQL RDS instance."
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Name = "${local.name_prefix}-db-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "db_mysql_from_app" {
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 3306
  ip_protocol                  = "tcp"
  to_port                      = 3306
}

resource "aws_vpc_security_group_egress_rule" "db_all" {
  security_group_id = aws_security_group.db.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
