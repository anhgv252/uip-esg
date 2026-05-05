# Partner Extensions

This directory contains partner-specific extensions for the UIP Smart City platform.

## Creating a New Partner Extension

1. Create a new Maven module: `partner-extensions/partner-{name}/`
2. Add `pom.xml` with dependency on `com.uip:backend` (scope provided)
3. Implement the `EsgReportExportPort` interface for custom report formats
4. Register as Spring `@Component` — auto-discovered via `@ConditionalOnProperty`

## Implementing EsgReportExportPort

```java
@Component
public class MyFormatExportAdapter implements EsgReportExportPort {
    @Override
    public String getFormatId() { return "my-format"; }

    @Override
    public String getContentType() { return "application/octet-stream"; }

    @Override
    public String getFileExtension() { return "bin"; }

    @Override
    public byte[] export(EsgReportData data) {
        // Custom export logic
    }
}
```

## Naming Convention

Partner IDs follow `kebab-case`: `energy-optimizer`, `citizen-first`, `smart-grid`.
Directory names must match: `partner-energy-optimizer/`.
