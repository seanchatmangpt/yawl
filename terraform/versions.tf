terraform {
  required_version = ">= 1.6.0"

  required_providers {
    # Google Cloud Provider
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }

    # Amazon Web Services Provider
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }

    # Microsoft Azure Provider
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }

    # Oracle Cloud Infrastructure Provider
    oci = {
      source  = "hashicorp/oci"
      version = "~> 5.0"
    }

    # IBM Cloud Provider
    ibm = {
      source  = "IBM-Cloud/ibm"
      version = "~> 1.0"
    }

    # Random provider for unique naming
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }

    # Time provider for resource timing
    time = {
      source  = "hashicorp/time"
      version = "~> 0.9"
    }

    # Helm provider for Kubernetes deployments
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.0"
    }

    # Kubernetes provider
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
  }
}
