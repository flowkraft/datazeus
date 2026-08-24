# ============================================================================
#  The Python koan runner.
# ============================================================================
#  Built once by `zeus koans python …` and cached by Docker after that. The learner never
#  types a docker command and never installs Python.
#
#  WHY DOCKER AND NOT A LOCAL PYTHON — decided 2026-08-24.
#  The JVM tracks can assume a JDK: `zeus koans` checks for java/JAVA_HOME and Maven arrives
#  via the bundled wrapper. Python has no equivalent — assuming a working local Python means
#  assuming their version, their PATH and their venv habits, and on Windows that is the most
#  likely thing to make somebody quit before koan one.
#
#  Docker is already a prerequisite for the rest of DataPallas: `zeus test` refuses to run
#  without it, and CloudBeaver, PostgreSQL and Jupyter are all containers. So this adds a
#  requirement the learner has already met.
#
#  WHY A TINY PURPOSE-BUILT IMAGE, not the Jupyter one we ship.
#  Reusing quay.io/jupyter/scipy-notebook would avoid a second download for anyone running
#  Jupyter — but it is 4-5GB, and somebody who never opens a notebook should not pull that to
#  run one koan. This is ~200MB, builds in seconds, and pins every version the koans assert
#  against. A koan whose expected answer shifts because pandas changed is a koan nobody trusts.
#
#  Base pinned to a full patch (verified on Docker Hub), not 3.13-slim, because "exact version
#  control" is the entire reason this image exists rather than a pip install on the host.
FROM python:3.13.15-slim

# Nothing here is optional and nothing here is a convenience: pytest runs the koans, and
# pandas / polars / duckdb are the three libraries the track's episodes actually assert on.
COPY requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt

# The koans are mounted at run time; the datasets read-only beside them. Nothing is baked in,
# so editing a koan never means rebuilding.
WORKDIR /koans
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1

ENTRYPOINT ["pytest"]
