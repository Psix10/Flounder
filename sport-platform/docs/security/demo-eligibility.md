# NF-02: eligibility-aware demo fixtures

## Scope

Demo preparation remains an allowlisted, authenticated organizer/admin operation for an empty
INDIVIDUAL/HEAT discipline in a DRAFT event, before competition units exist. Only TIME/ASC,
seedTimeSeconds and capacity 1..32 are supported. Count remains twice capacity (2..64), with
exactly one missing seed time. No protocol, payment or notification is created by preparation.

## Planning

DemoDisciplineConstraints now includes the actual discipline settings JSON. The protocol module
plans participants from its production eligibility definitions, combining global eligibility,
discipline gender, allowedCategoryCodes and selected category eligibility. Category selection
happens before unique-category classification, matching DisciplineProtocolAdmission.

ON_DATE uses completed years; BY_YEAR uses the difference of years. Birth dates are chosen from
intersections of inclusive date ranges and are never in the future. This demo implementation
uses dates from year 1 onwards and rejects configurations it cannot represent. The preferred
unrestricted birth date remains 2000-01-01. It does not change the real registration contract.

Only uniquely classified dates are used. Relevant categories with no unique representative,
unknown category selections, empty intersections and too many effective categories for the
fixed batch size block preparation. Incompatible gender/age categories are not forced into the
discipline. Each effective category is represented, with deterministic round-robin allocation.
Each generated snapshot is checked again by ProtocolEligibilityVerifier and ProtocolCategoryPolicy.

## Preview, provisioning and confirmation

Preview rows include birthDate, categoryCode and seedingGroupCode. Actual profile provisioning
receives the preview gender and birth date through DemoParticipantProvisioning.Fixture. The
service checks returned profile name/gender/date against the plan before creating registrations.
The receipt copies participant details from that same plan and adds registration IDs.

New datasetVersion values use SWIM_ELIGIBLE_V2:<sha256>. The digest binds the rule version,
capacity, count, discipline settings and preview rows. Creation recalculates this value under
existing locks and refuses a changed plan before reserving/provisioning a batch.

Existing SWIM_MIXED_V1 receipts remain readable; missing newly added fields are null. Exact
historical requests can replay their stored receipt after authorization, without fresh planning
or additional writes. A legacy dataset cannot create a new batch. Reusing a request ID with a
different dataset version is a conflict. Existing batches are not migrated or recreated.

## Verification required before closing NF-02

Run DemoParticipantProvisioningFixtureTest, DemoParticipantPlanningServiceTest,
DemoDataServiceTest and DemoSampleTest, then the full backend suite. Run frontend model tests
and the frontend build. Manually check previews for male, female and selected age-category
disciplines in a separate empty allowlisted draft scenario. Do not alter published/finalized data.

The identity fixture step is a prerequisite. No changes to NF-01 confirmation guards or server
role/ownership checks are part of this patch. Installers/backups are local tools, not source files.
