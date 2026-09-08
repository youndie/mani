# One gate, and CI runs exactly this target.
#
# A local check set that differs from the CI one turns "green here, red there" into the normal state
# of affairs, and then neither is read. So: whatever is not in `make check` is not a gate, and
# whatever is in it runs the same way in both places.
#
# Documentation checks only. Building and testing the product lives in Gradle (see README.md) and is
# deliberately not pulled in here: pretending `make` is the entry point to the build would create a
# second place where the tasks are listed, and it would drift from the first.

DOCS ?= docs
REPOS ?= ..
PY ?= python3

.PHONY: check gate report fix help

help:
	@echo "make check   - the gate: blocking checks, exactly what CI runs"
	@echo "make report  - non-blocking reports: BDD coverage, code anchors"
	@echo "make fix     - append the coverage-map lines that are missing"

check: gate report

# Blocking. Any of these failing means the documentation contradicts itself or the code, which is a
# defect rather than a matter of taste.
#
# `backlog_index.py` is absent because the repository has no backlog (commit b00fafe). Add one and
# you take the script from the `docs-bootstrap` skill and put it on the first line of this target.
gate:
	$(PY) scripts/docs_check.py --docs $(DOCS)
	$(PY) scripts/coverage_map.py --check --docs $(DOCS)

# Non-blocking, and deliberately so.
#
# bdd_report counts scenarios; demanding a percentage is meaningless while part of the acceptance is
# manual. code_anchors goes stale because of a rename in the code, and a machine cannot tell a live
# path from one quoted as obsolete. Both are read by a person.
report:
	$(PY) scripts/bdd_report.py --docs $(DOCS) --repos $(REPOS)
	$(PY) scripts/code_anchors.py --docs $(DOCS) --repos $(REPOS)

fix:
	$(PY) scripts/coverage_map.py --fix --docs $(DOCS)
