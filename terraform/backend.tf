#------------------------------------------------------------------------------
# YAWL Multi-Cloud Terraform Backend Configuration
# Version: 1.0.0
#
# Configure remote state backend based on cloud provider
# Uncomment the appropriate backend block for your deployment
#------------------------------------------------------------------------------

#------------------------------------------------------------------------------
# GCS Backend (for GCP deployments)
#------------------------------------------------------------------------------

# terraform {
#   backend "gcs" {
#     bucket = "yawl-terraform-state"
#     prefix = "terraform/state"
#
#     # Optional: Enable encryption
#     encryption_key = "projects/my-project/locations/global/keyRings/my-keyring/cryptoKeys/my-key"
#   }
# }

#------------------------------------------------------------------------------
# S3 Backend (for AWS deployments)
#------------------------------------------------------------------------------

# terraform {
#   backend "s3" {
#     bucket         = "yawl-terraform-state"
#     key            = "terraform.tfstate"
#     region         = "us-east-1"
#     encrypt        = true
#     dynamodb_table = "yawl-terraform-locks"
#
#     # Optional: Use KMS encryption
#     kms_key_id = "alias/terraform-state-key"
#   }
# }

#------------------------------------------------------------------------------
# Azure Storage Backend (for Azure deployments)
#------------------------------------------------------------------------------

# terraform {
#   backend "azurerm" {
#     resource_group_name  = "yawl-terraform-state"
#     storage_account_name = "yawltfstate"
#     container_name       = "tfstate"
#     key                  = "terraform.tfstate"
#   }
# }

#------------------------------------------------------------------------------
# OCI Object Storage Backend (for Oracle Cloud deployments)
# Note: OCI uses the generic HTTP backend with OCI Object Storage
#------------------------------------------------------------------------------

# terraform {
#   backend "http" {
#     address = "https://objectstorage.us-phoenix-1.oraclecloud.com/n/namespace/b/yawl-terraform-state/o/terraform.tfstate"
#     # Requires OCI CLI authentication or pre-authenticated request
#   }
# }

#------------------------------------------------------------------------------
# IBM Cloud Object Storage Backend (for IBM Cloud deployments)
# Note: IBM COS uses S3-compatible API
#------------------------------------------------------------------------------

# terraform {
#   backend "s3" {
#     endpoint                    = "s3.us-south.cloud-object-storage.appdomain.cloud"
#     bucket                      = "yawl-terraform-state"
#     key                         = "terraform.tfstate"
#     region                      = "us-south"
#     skip_credentials_validation = true
#     skip_metadata_api_check     = true
#     skip_region_validation      = true
#     force_path_style            = true
#   }
# }

#------------------------------------------------------------------------------
# Local Backend (default for development)
#------------------------------------------------------------------------------

terraform {
  backend "local" {
    path = "terraform.tfstate"
  }
}

#------------------------------------------------------------------------------
# State Management Variables
#------------------------------------------------------------------------------

variable "state_bucket_name" {
  description = "Name of the state storage bucket"
  type        = string
  default     = "yawl-terraform-state"
}

variable "state_lock_table" {
  description = "Name of the state lock table (DynamoDB for AWS)"
  type        = string
  default     = "yawl-terraform-locks"
}

variable "state_encryption_key" {
  description = "KMS key for state encryption"
  type        = string
  default     = ""
}
