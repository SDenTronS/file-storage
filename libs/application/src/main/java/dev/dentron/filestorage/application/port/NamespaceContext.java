package dev.dentron.filestorage.application.port;

public record NamespaceContext(String serviceId) {
    public String root() { return "svc/" + serviceId; }
    public String serviceId() { return serviceId; }
}
