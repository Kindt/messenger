# Generic Cell VM module (spec 011) — Phase 0 passthrough, no cloud API.

variable "cell_id" {
  type = string
}

variable "server_private_ip" {
  type = string
}

variable "web_private_ip" {
  type = string
}

variable "web_public_ip" {
  type = string
}

output "cell_id" {
  value = var.cell_id
}

output "server_private_ip" {
  value = var.server_private_ip
}

output "web_private_ip" {
  value = var.web_private_ip
}

output "web_public_ip" {
  value = var.web_public_ip
}
