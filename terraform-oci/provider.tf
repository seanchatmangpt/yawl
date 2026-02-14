terraform {
  required_version = ">= 1.0"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.1"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Uncomment to use remote state in OCI Object Storage
  # backend "s3" {
  #   bucket         = "yawl-terraform-state"
  #   key            = "yawl-oci/terraform.tfstate"
  #   region         = "us-phoenix-1"
  #   endpoint       = "https://swiftobjectstorage.region.oraclecloud.com"
  #   skip_requesting_account_id = true
  #   skip_credentials_validation = true
  #   skip_region_validation = true
  # }
}

provider "oci" {
  tenancy_ocid     = var.oci_tenancy_ocid
  user_ocid        = var.oci_user_ocid
  fingerprint      = var.oci_fingerprint
  private_key      = var.oci_private_key
  region           = var.oci_region
}

provider "random" {
}

provider "tls" {
}
