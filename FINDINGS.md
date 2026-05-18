# Findings

## Untracked LogSpec files (2026-05-18)

`LogSpec.scala` and `LogSpecVerification.scala` exist in `src/test/scala/in/rcard/fes/utils/` but were never committed, even though GitHub issue #2 is closed. Issue #4 (happy path test) depends on `LogSpec`, so the iteration working on #4 must commit these files first.

## com.sun.net.httpserver is accessible without --add-exports (2026-05-18)

JDK's `com.sun.net.httpserver.HttpServer` works in this project without any `--add-exports` flags. The `jdk.httpserver` module is on the module path by default. Confirmed by `StubHttpServerSpecVerification` compiling and running cleanly against `scalacOptions += "-release:25"`.
