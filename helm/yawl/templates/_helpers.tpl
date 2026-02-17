{{/*
Expand the name of the chart.
*/}}
{{- define "yawl.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "yawl.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "yawl.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "yawl.labels" -}}
helm.sh/chart: {{ include "yawl.chart" . }}
{{ include "yawl.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/component: engine
{{- with .Values.global.labels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "yawl.selectorLabels" -}}
app.kubernetes.io/name: {{ include "yawl.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: yawl
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "yawl.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "yawl.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Return the proper image name
*/}}
{{- define "yawl.image" -}}
{{- $registryName := .Values.image.registry -}}
{{- $repositoryName := .Values.image.repository -}}
{{- $tag := .Values.image.tag | default .Chart.AppVersion | toString -}}
{{- if $registryName }}
{{- printf "%s/%s:%s" $registryName $repositoryName $tag -}}
{{- else }}
{{- printf "%s:%s" $repositoryName $tag -}}
{{- end }}
{{- end }}

{{/*
Return the appropriate apiVersion for ingress
*/}}
{{- define "yawl.ingress.apiVersion" -}}
networking.k8s.io/v1
{{- end }}

{{/*
Return the appropriate apiVersion for HPA
*/}}
{{- define "yawl.hpa.apiVersion" -}}
autoscaling/v2
{{- end }}

{{/*
Return the database host
*/}}
{{- define "yawl.databaseHost" -}}
{{- if .Values.postgresql.enabled -}}
{{- printf "%s-postgresql" (include "yawl.fullname" .) -}}
{{- else if .Values.cloudsqlProxy.enabled -}}
{{- printf "%s-cloudsql-proxy" (include "yawl.fullname" .) -}}
{{- else if .Values.awsRdsProxy.enabled -}}
{{- .Values.awsRdsProxy.endpoint -}}
{{- else -}}
{{- .Values.externalDatabase.host -}}
{{- end -}}
{{- end }}

{{/*
Return the database port
*/}}
{{- define "yawl.databasePort" -}}
{{- if .Values.postgresql.enabled -}}
5432
{{- else if .Values.cloudsqlProxy.enabled -}}
{{- .Values.cloudsqlProxy.proxyPort -}}
{{- else if .Values.awsRdsProxy.enabled -}}
{{- .Values.awsRdsProxy.port -}}
{{- else -}}
{{- .Values.externalDatabase.port -}}
{{- end -}}
{{- end }}

{{/*
Return the database name
*/}}
{{- define "yawl.databaseName" -}}
{{- if .Values.postgresql.enabled -}}
{{- .Values.postgresql.auth.database -}}
{{- else -}}
{{- .Values.externalDatabase.database -}}
{{- end -}}
{{- end }}

{{/*
Return the database URL
*/}}
{{- define "yawl.databaseUrl" -}}
{{- $host := include "yawl.databaseHost" . -}}
{{- $port := include "yawl.databasePort" . -}}
{{- $name := include "yawl.databaseName" . -}}
{{- printf "jdbc:postgresql://%s:%s/%s" $host $port $name -}}
{{- end }}

{{/*
Return the Redis host
*/}}
{{- define "yawl.redisHost" -}}
{{- if .Values.redis.enabled -}}
{{- printf "%s-redis-master" (include "yawl.fullname" .) -}}
{{- else -}}
{{- .Values.externalRedis.host -}}
{{- end -}}
{{- end }}

{{/*
Return the Redis port
*/}}
{{- define "yawl.redisPort" -}}
{{- if .Values.redis.enabled -}}
6379
{{- else -}}
{{- .Values.externalRedis.port -}}
{{- end -}}
{{- end }}

{{/*
Create the namespace
*/}}
{{- define "yawl.namespace" -}}
{{- default .Values.global.namespace .Release.Namespace -}}
{{- end }}

{{/*
Return true if a database secret should be created
*/}}
{{- define "yawl.createDatabaseSecret" -}}
{{- if and .Values.secrets.database.create (not .Values.externalDatabase.existingSecret) -}}
true
{{- end }}
{{- end }}

{{/*
Return the secret name for database credentials
*/}}
{{- define "yawl.databaseSecretName" -}}
{{- if .Values.externalDatabase.existingSecret -}}
{{- .Values.externalDatabase.existingSecret -}}
{{- else if .Values.secrets.database.name -}}
{{- .Values.secrets.database.name -}}
{{- else -}}
{{- printf "%s-db-credentials" (include "yawl.fullname" .) -}}
{{- end -}}
{{- end }}

{{/*
Return the secret name for API keys
*/}}
{{- define "yawl.apiKeysSecretName" -}}
{{- if .Values.secrets.apiKeys.name -}}
{{- .Values.secrets.apiKeys.name -}}
{{- else -}}
{{- printf "%s-api-keys" (include "yawl.fullname" .) -}}
{{- end -}}
{{- end }}

{{/*
Return the secret name for encryption key
*/}}
{{- define "yawl.encryptionKeySecretName" -}}
{{- if .Values.secrets.encryptionKey.name -}}
{{- .Values.secrets.encryptionKey.name -}}
{{- else -}}
{{- printf "%s-encryption-key" (include "yawl.fullname" .) -}}
{{- end -}}
{{- end }}

{{/*
Return the storage class
*/}}
{{- define "yawl.storageClass" -}}
{{- if .Values.global.storageClass -}}
{{- .Values.global.storageClass -}}
{{- else if .Values.persistence.storageClass -}}
{{- .Values.persistence.storageClass -}}
{{- else -}}
""
{{- end -}}
{{- end }}

{{/*
Build JVM options string
*/}}
{{- define "yawl.jvmOpts" -}}
{{- $opts := .Values.jvm.opts -}}
{{- if .Values.jvm.extraOpts -}}
{{- printf "%s %s" $opts .Values.jvm.extraOpts -}}
{{- else -}}
{{- $opts -}}
{{- end -}}
{{- end }}

{{/*
Build OpenTelemetry resource attributes
*/}}
{{- define "yawl.otelResourceAttributes" -}}
{{- printf "service.name=%s,service.version=%s" .Values.opentelemetry.serviceName .Values.yawlVersion -}}
{{- end }}

{{/*
Pod labels
*/}}
{{- define "yawl.podLabels" -}}
app.kubernetes.io/name: {{ include "yawl.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: engine
app.kubernetes.io/part-of: yawl
{{- with .Values.podLabels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{/*
Annotations for deployments
*/}}
{{- define "yawl.deploymentAnnotations" -}}
{{- with .Values.global.annotations }}
{{ toYaml . }}
{{- end }}
{{- with .Values.podAnnotations }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{/*
Cloud provider specific annotations
*/}}
{{- define "yawl.cloudAnnotations" -}}
{{- if eq .Values.cloudProvider "gcp" -}}
{{- with .Values.serviceAccount.annotations }}
{{ toYaml . }}
{{- end }}
{{- else if eq .Values.cloudProvider "aws" -}}
{{- with .Values.serviceAccount.annotations }}
{{ toYaml . }}
{{- end }}
{{- else if eq .Values.cloudProvider "azure" -}}
{{- with .Values.serviceAccount.annotations }}
{{ toYaml . }}
{{- end }}
{{- else if eq .Values.cloudProvider "oracle" -}}
{{- with .Values.serviceAccount.annotations }}
{{ toYaml . }}
{{- end }}
{{- else if eq .Values.cloudProvider "ibm" -}}
{{- with .Values.serviceAccount.annotations }}
{{ toYaml . }}
{{- end }}
{{- end -}}
{{- end }}
