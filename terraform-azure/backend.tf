# Backend configuration for remote state management
# Uncomment and configure to use remote state storage in Azure

# terraform {
#   backend "azurerm" {
#     resource_group_name  = "rg-terraform-state"
#     storage_account_name = "tfstate"
#     container_name       = "state"
#     key                  = "yawl/terraform.tfstate"
#   }
# }

# To use this backend:
# 1. Create the storage account and container manually or via Azure CLI
# 2. Uncomment the backend block above
# 3. Run: terraform init
#
# Create storage account:
# az storage account create --name tfstate --resource-group rg-terraform-state --location eastus --sku Standard_LRS
#
# Create container:
# az storage container create --name state --account-name tfstate
