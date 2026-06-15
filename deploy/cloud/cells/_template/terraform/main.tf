# Terraform: generic Cell VM (spec 011 Phase 0)
# Phase 0: no cloud API — IPs from tfvars; outputs feed Ansible inventory.

terraform {
  required_version = ">= 1.5.0"
}

module "cell" {
  source = "../../modules/cell-vm/generic"

  cell_id             = var.cell_id
  server_private_ip   = var.server_private_ip
  web_private_ip      = var.web_private_ip
  web_public_ip       = var.web_public_ip
}
