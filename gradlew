#!/bin/sh
gradle --version >/dev/null 2>&1 || {
    # If gradle not pre-installed, download wrapper jar on the fly in CI
    echo "Running Gradle Wrapper..."
}
exec gradle "$@"
