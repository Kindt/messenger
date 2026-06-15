variable "cell_id" {
  type        = string
  description = "Cell slug (matches manifest cell_id)"
}

variable "server_private_ip" {
  type        = string
  description = "Private IP for korus-server VM"
}

variable "web_private_ip" {
  type        = string
  description = "Private IP for korus-web VM"
}

variable "web_public_ip" {
  type        = string
  description = "Public IP for web ingress (E1 edge)"
}
