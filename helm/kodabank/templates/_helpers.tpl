{{/*
Expand the name of the chart.
*/}}
{{- define "kodabank.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "kodabank.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "kodabank.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Selector labels for a given component
Usage: {{ include "kodabank.selectorLabels" (dict "component" "core" "context" .) }}
*/}}
{{- define "kodabank.selectorLabels" -}}
app.kubernetes.io/name: {{ .component }}
app.kubernetes.io/instance: {{ .context.Release.Name }}
{{- end }}

{{/*
Image reference helper
Usage: {{ include "kodabank.image" (dict "name" "kodabank-core" "context" .) }}
*/}}
{{- define "kodabank.image" -}}
{{ .context.Values.imageRegistry }}/{{ .name }}:{{ .context.Values.imageTag }}
{{- end }}

{{/*
Internal service URL helper
Usage: {{ include "kodabank.serviceUrl" (dict "component" "core" "port" "8086" "context" .) }}
*/}}
{{- define "kodabank.serviceUrl" -}}
http://{{ include "kodabank.fullname" .context }}-{{ .component }}:{{ .port }}
{{- end }}

{{/*
Keycloak issuer URI (external URL used as JWT issuer)
*/}}
{{- define "kodabank.keycloakIssuerUri" -}}
{{- if .Values.ingress.tls.enabled -}}
https://{{ .Values.keycloakHost }}/realms/{{ .Values.keycloak.realm }}
{{- else -}}
http://{{ .Values.keycloakHost }}/realms/{{ .Values.keycloak.realm }}
{{- end -}}
{{- end }}

{{/*
Frontend URL (external)
*/}}
{{- define "kodabank.frontendUrl" -}}
{{- if .Values.ingress.tls.enabled -}}
https://{{ .Values.host }}
{{- else -}}
http://{{ .Values.host }}
{{- end -}}
{{- end }}

{{/*
BFF base URL (external, same host as frontend)
*/}}
{{- define "kodabank.bffBaseUrl" -}}
{{ include "kodabank.frontendUrl" . }}
{{- end }}

{{/*
Storage class helper
*/}}
{{- define "kodabank.storageClass" -}}
{{- if .Values.storageClass -}}
storageClassName: {{ .Values.storageClass }}
{{- else -}}
storageClassName: ""
{{- end -}}
{{- end }}
