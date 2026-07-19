package harness

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import Script.*

/** The scenario matrix of harness/test/statemachine-test.sh (scenarios DRY, A-T) plus the
  * semantics bullets of the design doc, ported to in-memory scripted handlers.
  */
class ScenarioSpec extends AnyFlatSpec with Matchers:

  def runLoop(w: TestWorld, cfg: Config = Config()): LoopExit =
    Machine.runOnce(1)(using cfg, w.github, w.git, w.agents, w.gates, w.status, w.notifier, w.fs, w.clock)

  // ---- STOP.md ----------------------------------------------------------------------------

  "The machine" should "exit ManualStop (rc 10) when STOP.md is present, touching nothing" in {
    val w = TestWorld()
    w.stopFile = true

    val exit = runLoop(w)

    exit shouldBe LoopExit.ManualStop
    exit.rc shouldBe 10
    w.called("gh issue list") shouldBe false
    w.called("gh issue edit") shouldBe false
    w.calls shouldBe empty
  }
