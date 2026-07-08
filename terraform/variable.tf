variable "ssh_public_key" {
  type = string
}

variable "allowed_ssh_eduVPN" {
  description = "IP range allowed to SSH (TUM eduVPN IP)"  
  type        = string
  default     = "129.187.0.0/16"
}