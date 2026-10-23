# Spatial extraction regression: consolidate duplicate directory navigation

Completed evidence: PR source 38f0ebf, tested merge 6b02617 into R3 base 822a668b. Source/JVM #39934215521 SUCCESS. Emulator #39934215567 FAILURE. Full suite: 176 tests, six failures, zero errors/skips. Four independent projection attempts: 22/22 passed.

Two failures (rotation and scale long-press parameter extraction) stop before extraction: verifySpatialParameterExtraction cannot locate tool-POSITION after searching and closing the search IME. It retained a duplicate directory driver even after maintained openTool was updated in 5be6bc45 to select a filtered result with IME open. Shear happened not to fail in this run; this is not proof the duplicate path is safe.

Change only that duplicate navigation block to openTool("POSITION"). Keep the explicit actual POSITION host wait, parameter section, long-press drag, birdseye/projection controls, scene geometry, document and Undo assertions. No product source, tool IDs, domain behavior or test suppression. Proposed IME-close/dialog dismissal explanation remains a hypothesis until the new run; no claim that two failures are fixed or that the remaining count is four.

Other failures are retained: inspector draft/rotation hideKeyboard timeout, spatial unpin not displayed, batch Preview above IME assertion, and Transform shear field disposed during SetText. These need separate investigation.

Local git diff whitespace and Actions artifact-policy checks passed. Gradle/device execution unavailable locally; external validation pending. UI native visual parity, actual tablet/IME and real renderer acceptance remain separate. PR #149/#148 stay Draft.

UIGS Intake: known search-IME navigation condition, now found in a duplicate driver. Existing task records and prior fix cover the reusable principle; no duplicate Candidate or Canonical promotion.
