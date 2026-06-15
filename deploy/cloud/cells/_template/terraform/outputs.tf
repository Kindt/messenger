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

output "ansible_inventory_snippet" {
  value = <<-EOT
    korus_server_lan_ip: "${var.server_private_ip}"
    korus_web_lan_ip: "${var.web_private_ip}"
    korus_browser_ws_host: "${var.web_public_ip}"
  EOT
}
