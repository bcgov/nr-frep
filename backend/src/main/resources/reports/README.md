# Report templates (`classpath:reports/`)

JasperReports `.jrxml` templates for **template (PDF) reports** only. The report
feature has two kinds of report, routed by URL:

| Kind | Endpoint | Service | Data | Output | Template? |
|---|---|---|---|---|---|
| **CSV data extract** | `POST /api/v1/reports/csv/{reportName}` | `CSVReportService` | ref-cursor proc → `ReportExtractRepository` | CSV (Apache Commons CSV) | **no** |
| **Jasper template** | `POST /api/v1/reports/{reportId}` | `ReportService` | JRXML's own SQL, filled vs. a JDBC connection | PDF / CSV (Jasper) | **yes — here** |

This directory is for the second kind. The biodiversity **Data Extract** reports
(FREPRPT001–005) are the first kind — they have **no** template; their CSV is
written straight from the proc cursor, faithful to the legacy CSV-only reports.

## Adding a CSV data-extract report (no template)

1. Add a constant to `service/report/ReportDefinition.java` with its proc, e.g.
   `RIPARIAN_EXTRACT_OPENING("riparian-extract-opening", "Riparian_Extract_Opening", "freprpt_rip_opening")`.
2. Map the proc's argument list in `ReportExtractRepository.bindArgs`. Each report's proc takes a
   different positional list (after the OUT ref-cursor), so the binding is per-definition — the
   biodiversity 001–005 default is `(p_org_unit_code, p_opening, p_start_year, p_resource_val)`; CHR
   FREPRPT022 adds a checklist-status filter, repeats the master-list year and appends the user id.
   The proc must be granted to the app's Oracle user.
3. Add a matching entry to the frontend `src/pages/Reports/reportDefinitions.ts`
   (`availableFormats: ['csv']`) — the form POSTs to `/csv/{id}`.

## Adding a Jasper template report (PDF)

1. Add a `ReportDefinition` constant with `procName = null`.
2. Drop `reports/<NAME>.jrxml` here; the JRXML carries its own SQL. When porting a legacy
   JCRS report, **flatten it** so it compiles standalone: strip `com.jaspersoft.jasperserver.api.*`
   params, `repo:` logo images, and the `FREPRPT_HEADER*` / `SUBREPORT_EXT` machinery (inline the
   title + parameter echo instead). See `CHECKLIST_COMPLETION_STATUS.jrxml` (012) as the model.
3. Map its parameters in `service/report/ReportParameterProvider`.
4. Add the frontend entry with `availableFormats: ['pdf']` (or `['pdf','csv']`).

### Embedded subreports

A report can't always be fully flattened — FREPRPT018 has a per-row detail subreport that runs its
own proc. For that, keep the subreport JRXML here (e.g. `CHECKLIST_REJECTION_REASON_subreport.jrxml`),
set the main report's `subreportExpression` to a `$P{..._SUBREPORT}` parameter of class
`net.sf.jasperreports.engine.JasperReport`, and compile + inject it in `ReportParameterProvider`
(`compileSubreport`). The subreport reuses the fill's `REPORT_CONNECTION`. List any such subreport in
`ReportTemplateCompileTest#everyEmbeddedSubreportCompiles` so it's compiled in the build.

`ReportTemplateCompileTest` compiles every template report's JRXML (and listed subreports) in the build.
