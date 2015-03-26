package casestudies

import java.io.File

import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.tactics.TacticLibrary._
import edu.cmu.cs.ls.keymaera.tactics.TacticLibrary.locateAnte
import edu.cmu.cs.ls.keymaera.tactics.TacticLibrary.locateSucc
import edu.cmu.cs.ls.keymaera.tactics.Tactics.PositionTactic
import edu.cmu.cs.ls.keymaera.tactics.Interpreter
import edu.cmu.cs.ls.keymaera.tactics.BranchLabels
import edu.cmu.cs.ls.keymaera.tactics.Tactics
import edu.cmu.cs.ls.keymaera.tactics._
import edu.cmu.cs.ls.keymaera.tests.ProvabilityTestHelper
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import testHelper.ParserFactory._
import edu.cmu.cs.ls.keymaera.tactics.ODETactics.diffSolution
import edu.cmu.cs.ls.keymaera.tactics.HybridProgramTacticsImpl.wipeContextInductionT
import edu.cmu.cs.ls.keymaera.tactics.SearchTacticsImpl._
import testHelper.StringConverter._
import BranchLabels._

/**
 * Created by ran on 3/24/15.
 */
class BouncingBall extends FlatSpec with Matchers with BeforeAndAfterEach {
  val helper = new ProvabilityTestHelper((x) => println(x))
  val mathematicaConfig : Map[String, String] = Map("linkName" -> "/Applications/Mathematica.app/Contents/MacOS/MathKernel")

  override def beforeEach() = {
    Tactics.KeYmaeraScheduler = new Interpreter(KeYmaera)
    Tactics.MathematicaScheduler = new Interpreter(new Mathematica)
    Tactics.MathematicaScheduler.init(mathematicaConfig)
    Tactics.KeYmaeraScheduler.init(Map())
  }

  override def afterEach() = {
    Tactics.MathematicaScheduler.shutdown()
    Tactics.KeYmaeraScheduler.shutdown()
    Tactics.MathematicaScheduler = null
    Tactics.KeYmaeraScheduler = null
  }

  def ls(t: PositionTactic) = locateSucc(t)
  def la(t: PositionTactic) = locateAnte(t)

  "bouncing ball tout" should "be provable" in {

    val file = new File("examples/simple/bouncing-ball/bouncing-ball-tout.key")
    val s = parseToSequent(file)

    val tactic = ls(ImplyRightT) & (la(AndLeftT)*) & ls(wipeContextInductionT(Some("v^2<=2*g()*(H-h) & h>=0".asFormula))) & onBranch(
      (indInitLbl, debugT("Base Case") & ls(AndRightT) &&
        (AxiomCloseT,
         AxiomCloseT)),
      (indUseCaseLbl, debugT("Use Case") & ls(ImplyRightT) & (la(AndLeftT)*) & ls(AndRightT) &&
         (AxiomCloseT,
          arithmeticT)),
      (indStepLbl, debugT("Step") & ls(ImplyRightT) & (la(AndLeftT)*) & ls(boxSeqT) &
        ls(diffSolution(None)) & debugT("open goal")
        & ls(ImplyRightT) & (la(AndLeftT)*) &
        ls(boxChoiceT) & ls(AndRightT) &&
          (ls(boxTestT)  & ls(ImplyRightT) & ls(AndRightT) &&
             (arithmeticT,
              AxiomCloseT),
           ls(boxSeqT) & ls(boxTestT) & ls(ImplyRightT) & ls(boxAssignT) & ls(AndRightT) &&
             (arithmeticNoHideT,
              AxiomCloseT)
          )
        )
    )

    helper.runTactic(tactic, new RootNode(s)) shouldBe 'closed
  }


  "bouncing ball tout 2" should "be provable" in {

    val file = new File("examples/simple/bouncing-ball/bouncing-ball-tout2.key")
    val s = parseToSequent(file)

    val tactic = ls(ImplyRightT) & (la(AndLeftT)*) & ls(wipeContextInductionT(Some("v^2<=2*g()*(H-h) & h>=0 & h<=H".asFormula))) & onBranch(
      (indInitLbl, debugT("Base Case") & ls(AndRightT) &&
        (ls(AndRightT) &&
          (AxiomCloseT,
            AxiomCloseT),
          AxiomCloseT)),
      (indUseCaseLbl, debugT("Use Case") & ls(ImplyRightT) & (la(AndLeftT)*) & ls(AndRightT) &&
        (AxiomCloseT,
          AxiomCloseT)),
      (indStepLbl, debugT("Step") & ls(ImplyRightT) & (la(AndLeftT)*) & ls(boxSeqT) &
        ls(diffSolution(None)) & ls(ImplyRightT) & (la(AndLeftT)*) &
        ls(boxChoiceT) & ls(AndRightT) &&
        (ls(boxTestT)  & ls(ImplyRightT) & ls(AndRightT) &&
          (ls(AndRightT) &&
            (arithmeticT,
              AxiomCloseT),
            arithmeticT),
          ls(boxSeqT) & ls(boxTestT) & ls(ImplyRightT) & ls(boxAssignT) & ls(AndRightT) &&
            (ls(AndRightT) &&
              (arithmeticNoHideT,
                arithmeticT),
              arithmeticT
              )
          )
        )
    )

    helper.runTactic(tactic, new RootNode(s)) shouldBe 'closed
  }


}
