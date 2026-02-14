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
Return the proper service image name
*/}}
{{- define "yawl.serviceImage" -}}
{{- $registryName := .Values.image.registry -}}
{{- $repositoryName := .service.image.repository -}}
{{- $tag := .service.image.tag | default .Values.image.tag | default $.Chart.AppVersion | toString -}}
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
{{- if semverCompare ">=1.19-0" .Capabilities.KubeVersion.GitVersion -}}
networking.k8s.io/v1
{{- else if semverCompare ">=1.14-0" .Capabilities.KubeVersion.GitVersion -}}
networking.k8s.io/v1beta1
{{- else -}}
extensions/v1beta1
{{- end }}
{{- end }}

{{/*
Return the appropriate apiVersion for HPA
*/}}
{{- define "yawl.hpa.apiVersion" -}}
{{- if semverCompare ">=1.23-0" .Capabilities.KubeVersion.GitVersion -}}
autoscaling/v2
{{- else -}}
autoscaling/v2beta2
{{- end }}
{{- end }}

{{/*
Return the database host
*/}}
{{- define "yawl.databaseHost" -}}
{{- if .Values.postgresql.enabled -}}
{{- if eq .Values.postgresql.architecture "replication" -}}
{{- printf "%s-primary" (include "yawl.fullname" .Subcharts.postgresql) -}}
{{- else -}}
{{- include "yawl.fullname" .Subcharts.postgresql -}}
{{- end -}}
{{- else if .Values.externalDatabase.existingSecret -}}
{{- .Values.externalDatabase.host -}}
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
{{- printf "%s-master" (include "yawl.fullname" .Subcharts.redis) -}}
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
Return true if a secret object should be created
*/}}
{{- define "yawl.createSecret" -}}
{{- if .Values.secrets.database.create -}}
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
Labels for a service deployment
*/}}
{{- define "yawl.serviceLabels" -}}
app.kubernetes.io/name: {{ include "yawl.name" . }}-{{ .serviceName }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: {{ .serviceName }}
app.kubernetes.io/part-of: yawl
{{- with .context.Values.global.labels }}
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
{{- end }}

{{/*
Pod labels for a service
*/}}
{{- define "yawl.podLabels" -}}
app.kubernetes.io/name: {{ include "yawl.name" . }}-{{ .serviceName }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: {{ .serviceName }}
app.kubernetes.io/part-of: yawl
{{- end }}

{{/*
Probe configuration
*/}}
{{- define "yawl.probe" -}}
httpGet:
  path: {{ .path }}
  port: {{ .port }}
initialDelaySeconds: {{ .initialDelaySeconds | default 60 }}
periodSeconds: {{ .periodSeconds | default 10 }}
timeoutSeconds: {{ .timeoutSeconds | default 5 }}
failureThreshold: {{ .failureThreshold | default 3 }}
{{- end }}

{{/*
Resource configuration
*/}}
{{- define "yawl.resources" -}}
requests:
  cpu: {{ .requests.cpu }}
  memory: {{ .requests.memory }}
limits:
  cpu: {{ .limits.cpu }}
  memory: {{ .limits.memory }}
{{- end }}

{{/*
Service URL helper
*/}}
{{- define "yawl.serviceUrl" -}}
{{- printf "http://%s-%s:%d" (include "yawl.name" .context) .serviceName .port -}}
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
