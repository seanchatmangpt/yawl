<#
.SYNOPSIS
    YAWL Workflow Engine Azure Deployment Script

.DESCRIPTION
    Automated ARM template deployment script with comprehensive validation
    and post-deployment configuration for YAWL on Azure

.PARAMETER Environment
    Deployment environment: development, staging, or production

.PARAMETER Region
    Azure region for deployment (default: eastus)

.PARAMETER SkipValidation
    Skip template validation

.EXAMPLE
    .\deploy.ps1 -Environment production -Region eastus

.NOTES
    Requires: PowerShell 7+, Azure CLI, and appropriate Azure permissions
#>

param (
    [ValidateSet('development', 'staging', 'production')]
    [string]$Environment = 'production',

    [string]$Region = 'eastus',

    [switch]$SkipValidation,

    [switch]$DryRun
)

# Configuration
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TemplateFile = Join-Path $ScriptDir "azuredeploy.json"
$ParametersFile = Join-Path $ScriptDir "parameters.json"
$ProjectName = "yawl"
$ResourceGroupName = "$ProjectName-$Environment"
$DeploymentName = "$ProjectName-deployment-$(Get-Date -Format 'yyyyMMddHHmmss')"

# Color output functions
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# Helper functions
function Test-Prerequisites {
    Write-Info "Checking prerequisites..."

    $prerequisites = @(
        @{ Name = "Azure CLI"; Command = "az" },
        @{ Name = "jq"; Command = "jq"; Required = $false }
    )

    foreach ($prereq in $prerequisites) {
        if (Get-Command $prereq.Command -ErrorAction SilentlyContinue) {
            Write-Success "$($prereq.Name) found"
        } elseif ($prereq.Required -ne $false) {
            Write-Error "$($prereq.Name) is not installed"
            exit 1
        } else {
            Write-Warning "$($prereq.Name) not found (optional)"
        }
    }

    # Test Azure authentication
    try {
        $null = az account show 2>$null
        Write-Success "Azure authentication verified"
    } catch {
        Write-Error "Not authenticated to Azure. Run 'az login' first"
        exit 1
    }
}

function Test-TemplateFiles {
    Write-Info "Validating template files..."

    if (-not (Test-Path $TemplateFile)) {
        Write-Error "Template file not found: $TemplateFile"
        exit 1
    }

    if (-not (Test-Path $ParametersFile)) {
        Write-Error "Parameters file not found: $ParametersFile"
        exit 1
    }

    Write-Success "Template files validated"
}

function Get-DatabasePassword {
    Write-Info "Enter PostgreSQL admin password requirements:"
    Write-Host "  - Minimum 8 characters"
    Write-Host "  - Must contain uppercase letters"
    Write-Host "  - Must contain lowercase letters"
    Write-Host "  - Must contain numbers"
    Write-Host "  - Must contain special characters"
    Write-Host ""

    do {
        $password = Read-Host "Enter password" -AsSecureString
        $passwordConfirm = Read-Host "Confirm password" -AsSecureString

        $passwordString = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [System.Runtime.InteropServices.Marshal]::SecureStringToCoTaskMemUnicode($password)
        )
        $passwordConfirmString = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [System.Runtime.InteropServices.Marshal]::SecureStringToCoTaskMemUnicode($passwordConfirm)
        )

        if ($passwordString -ne $passwordConfirmString) {
            Write-Warning "Passwords do not match"
            continue
        }

        if ($passwordString.Length -lt 8) {
            Write-Warning "Password must be at least 8 characters"
            continue
        }

        if ($passwordString -notmatch '[A-Z]') {
            Write-Warning "Password must contain uppercase letters"
            continue
        }

        if ($passwordString -notmatch '[a-z]') {
            Write-Warning "Password must contain lowercase letters"
            continue
        }

        if ($passwordString -notmatch '[0-9]') {
            Write-Warning "Password must contain numbers"
            continue
        }

        if ($passwordString -notmatch '[!@#$%^&*()_+\-=\[\]{};:,.<>?]') {
            Write-Warning "Password must contain special characters"
            continue
        }

        break
    } while ($true)

    return $passwordString
}

function Get-SubscriptionInfo {
    Write-Info "Getting Azure subscription information..."

    $subscription = az account show --query "{name:name, id:id}" | ConvertFrom-Json

    Write-Info "Current subscription: $($subscription.name) ($($subscription.id))"

    $confirm = Read-Host "Continue with this subscription? (yes/no)"
    if ($confirm -ne "yes") {
        Write-Error "Deployment cancelled"
        exit 1
    }
}

function New-AzureResourceGroup {
    Write-Info "Creating resource group: $ResourceGroupName"

    # Check if resource group exists
    $exists = az group exists --name $ResourceGroupName | ConvertFrom-Json

    if ($exists) {
        Write-Warning "Resource group $ResourceGroupName already exists"
    } else {
        az group create `
            --name $ResourceGroupName `
            --location $Region `
            --tags `
                environment=$Environment `
                application=$ProjectName `
                managed-by="arm-template" `
                deployed="$(Get-Date -Format 'yyyy-MM-dd')" `
            > $null

        Write-Success "Resource group created"
    }
}

function Test-ArmTemplate {
    Write-Info "Validating ARM template..."

    $validation = az deployment group validate `
        --resource-group $ResourceGroupName `
        --template-file $TemplateFile `
        --parameters $ParametersFile `
        --parameters `
            environment=$Environment `
            location=$Region `
            projectName=$ProjectName `
            databaseAdminPassword=$dbPassword `
        2>&1

    if ($validation -match "Valid") {
        Write-Success "Template validation passed"
        return $true
    } else {
        Write-Error "Template validation failed"
        Write-Host $validation
        return $false
    }
}

function Start-ArmDeployment {
    param(
        [string]$DatabasePassword
    )

    Write-Info "Starting ARM template deployment..."
    Write-Info "Resource Group: $ResourceGroupName"
    Write-Info "Region: $Region"
    Write-Info "Environment: $Environment"
    Write-Info "Deployment Name: $DeploymentName"
    Write-Host ""

    $confirm = Read-Host "Do you want to proceed with the deployment? (yes/no)"
    if ($confirm -ne "yes") {
        Write-Error "Deployment cancelled"
        exit 1
    }

    $startTime = Get-Date

    if ($DryRun) {
        Write-Info "Running in DRY-RUN mode (no resources will be created)"

        $output = az deployment group what-if `
            --resource-group $ResourceGroupName `
            --template-file $TemplateFile `
            --parameters $ParametersFile `
            --parameters `
                environment=$Environment `
                location=$Region `
                projectName=$ProjectName `
                databaseAdminPassword=$DatabasePassword

        Write-Host $output
    } else {
        $output = az deployment group create `
            --resource-group $ResourceGroupName `
            --template-file $TemplateFile `
            --parameters $ParametersFile `
            --parameters `
                environment=$Environment `
                location=$Region `
                projectName=$ProjectName `
                databaseAdminPassword=$DatabasePassword `
            --name $DeploymentName

        if ($LASTEXITCODE -eq 0) {
            $endTime = Get-Date
            $duration = $endTime - $startTime
            Write-Success "Deployment completed in $($duration.TotalSeconds) seconds"
            return $true
        } else {
            Write-Error "Deployment failed"
            return $false
        }
    }
}

function Get-DeploymentOutputs {
    Write-Info "Retrieving deployment outputs..."

    Write-Host ""
    Write-Host "=== Deployment Outputs ===" -ForegroundColor Blue

    $outputs = az deployment group show `
        --resource-group $ResourceGroupName `
        --name $DeploymentName `
        --query properties.outputs

    Write-Host $outputs

    Write-Host ""
    Write-Info "Resources deployed:"

    az resource list `
        --resource-group $ResourceGroupName `
        --query "[].{Name:name, Type:type}" `
        -o table
}

function Save-DeploymentOutputs {
    Write-Info "Saving deployment outputs to file..."

    $outputFile = Join-Path $ScriptDir "deployment-outputs-$Environment.json"

    az deployment group show `
        --resource-group $ResourceGroupName `
        --name $DeploymentName `
        --query properties.outputs `
        -o json | Out-File -FilePath $outputFile

    Write-Success "Outputs saved to $outputFile"
}

function Show-PostDeploymentInfo {
    Write-Host ""
    Write-Host "=== Next Steps ===" -ForegroundColor Blue

    Write-Host "1. Verify deployment in Azure Portal"
    Write-Host "   https://portal.azure.com"
    Write-Host ""

    Write-Host "2. Configure Application Gateway backend:"
    Write-Host "   az network application-gateway address-pool update \"
    Write-Host "     --resource-group $ResourceGroupName \"
    Write-Host "     --gateway-name <appgw-name> \"
    Write-Host "     --name appGatewayBackendPool \"
    Write-Host "     --servers <app-service-ip>"
    Write-Host ""

    Write-Host "3. Deploy YAWL application:"
    Write-Host "   az webapp deployment container config \"
    Write-Host "     --resource-group $ResourceGroupName \"
    Write-Host "     --name <app-service-name>"
    Write-Host ""

    Write-Host "4. Monitor application:"
    Write-Host "   az webapp log tail \"
    Write-Host "     --resource-group $ResourceGroupName \"
    Write-Host "     --name <app-service-name>"
    Write-Host ""
}

# Main execution
function Main {
    Write-Host ""
    Write-Host "╔════════════════════════════════════════════════╗" -ForegroundColor Blue
    Write-Host "║   YAWL Workflow Engine - Azure Deployment     ║" -ForegroundColor Blue
    Write-Host "║                                                ║" -ForegroundColor Blue
    Write-Host "║   Environment: $Environment" -ForegroundColor Blue
    Write-Host "║   Region: $Region" -ForegroundColor Blue
    Write-Host "╚════════════════════════════════════════════════╝" -ForegroundColor Blue
    Write-Host ""

    try {
        Test-Prerequisites
        Test-TemplateFiles
        Get-SubscriptionInfo

        $script:dbPassword = Get-DatabasePassword

        New-AzureResourceGroup

        if (-not $SkipValidation) {
            if (-not (Test-ArmTemplate)) {
                exit 1
            }
        }

        if (Start-ArmDeployment -DatabasePassword $dbPassword) {
            if (-not $DryRun) {
                Get-DeploymentOutputs
                Save-DeploymentOutputs
                Show-PostDeploymentInfo
                Write-Success "YAWL deployment completed successfully!"
            } else {
                Write-Success "Dry-run completed. Review the changes above."
            }
        } else {
            exit 1
        }
    }
    catch {
        Write-Error "An unexpected error occurred: $_"
        exit 1
    }
}

# Execute main function
Main
