package harness

import in.rcard.yaes.Raise

/** The loop state machine for one US, ported from `harness/loop.sh` iterate(). */
object Machine:

  /** One driver tick: folds the infra-fault channel to LoopExit.InfraFault (rc 50) and emits
    * the terminal DONE status event, exactly like the bash driver.
    */
  def runOnce(n: Int)(using
      Config,
      GitHub,
      Git,
      AgentDispatch,
      GateRunner,
      StatusLog,
      Notify,
      HarnessFs,
      Clock
  ): LoopExit =
    Raise.fold(iterate(n))(_ => LoopExit.InfraFault)(identity)

  /** One US, start to terminal. Infra faults short-circuit via Raise[InfraFault]: no code
    * past a raise can spend repair budget or dispatch a FIX.
    */
  def iterate(n: Int)(using
      Config,
      GitHub,
      Git,
      AgentDispatch,
      GateRunner,
      StatusLog,
      Notify,
      HarnessFs,
      Clock,
      Raise[InfraFault]
  ): LoopExit =
    val fs = summon[HarnessFs]
    // STOP.md is a MANUAL kill-switch only: the loop never writes it itself.
    if fs.stopRequested() then return LoopExit.ManualStop
    ???
