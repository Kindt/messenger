# Proxmox Cell VM module stub (spec 011 Phase 3 T01126 — backlog)

variable "cell_id" { type = string }
variable "server_private_ip" { type = string }
variable "web_private_ip" { type = string }
variable "web_public_ip" { type = string }

# TODO: proxmox_vm resource when provider credentials available

output "cell_id" { value = var.cell_id }
