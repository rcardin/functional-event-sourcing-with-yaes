package in.rcard.fes.util

import in.rcard.yaes.Raise
import in.rcard.yaes.http.client.{Uri, UriParam}

object UriOps:
  extension (base: Uri)
    def /(segment: UriParam): Uri =
      // Safe: base is already a valid Uri, segment is URL-encoded via UriParam.
      // When promoted to YAES, replace with Uri.fromTrustedString.
      Raise.fold(Uri(s"${base.value}/${segment.encoded}")) { _ =>
        throw new AssertionError(s"URI construction failed — unreachable by construction")
      }(identity)
