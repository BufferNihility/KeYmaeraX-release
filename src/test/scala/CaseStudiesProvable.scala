import java.io.File

import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.tactics.Tactics.{PositionTactic, Tactic}
import edu.cmu.cs.ls.keymaera.tactics._
import edu.cmu.cs.ls.keymaera.tests.ProvabilityTestHelper
import org.scalatest.{PrivateMethodTester, BeforeAndAfterEach, Matchers, FlatSpec}
import scala.collection.immutable.Map
import testHelper.ParserFactory._
import testHelper.StringConverter._
import TacticLibrary._

/**
 * Created by ran on 2/4/15.
 * @author Ran Ji
 * @author Stefan Mitsch
 */
class CaseStudiesProvable extends FlatSpec with Matchers with BeforeAndAfterEach with PrivateMethodTester {
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

  "AxiomClose" should "be provable" in {
    val file = new File("examples/dev/t/tactics/AxiomClose.key")
    val s = parseToSequent(file)

    helper.runTactic(default, new RootNode(s)).isClosed() should be (true)
  }

  "DecomposeQuant" should "be provable" in {
    val file = new File("examples/dev/t/tactics/DecomposeQuant.key")
    val s = parseToSequent(file)

    helper.runTactic(default, new RootNode(s)).isClosed() should be (true)
  }

  "EqualityRewriting" should "be provable" in {
    val file = new File("examples/dev/t/tactics/EqualityRewriting.key")
    val s = parseToSequent(file)

    helper.runTactic(default, new RootNode(s)).isClosed() should be (true)
  }

  "ETCS-essentials-noloop" should "be provable" in {
    val file = new File("examples/dev/t/tactics/ETCS-essentials-noloop.key")
    val s = parseToSequent(file)

    helper.runTactic(default, new RootNode(s)).isClosed() should be (true)
  }

  "ETCS-essentials" should "be provable" in {
    val file = new File("examples/dev/t/tactics/ETCS-essentials.key")
    val s = parseToSequent(file)

    helper.runTactic(master(new Generate("v^2<=2*b*(m-z)".asFormula), true), new RootNode(s)).isClosed() should be (true)
  }

  "Stuttering" should "be provable" in {
    import scala.language.postfixOps
    import edu.cmu.cs.ls.keymaera.tactics.BranchLabels.{indInitLbl,indStepLbl,indUseCaseLbl}
    import edu.cmu.cs.ls.keymaera.tactics.SearchTacticsImpl.onBranch
    import edu.cmu.cs.ls.keymaera.tactics.HybridProgramTacticsImpl.wipeContextInductionT

    val tactic = ls(ImplyRightT) &
      ls(wipeContextInductionT(Some("x <= y".asFormula))) &
      onBranch(
        (indInitLbl, AxiomCloseT(AntePosition(0), SuccPosition(0))),
        (indStepLbl, debugT("step") & ls(ImplyRightT) &
          ls(boxChoiceT) & ls(AndRightT) &
          ((ls(boxSeqT) ~ ls(boxAssignT))*) & arithmeticT),
        (indUseCaseLbl, ls(ImplyRightT) & AxiomCloseT(AntePosition(0), SuccPosition(0)))
      )

    helper.runTactic(tactic, new RootNode(parseToSequent(new File("examples/dev/t/tactics/Stuttering-allwrites.key")))) shouldBe 'closed
    // loops where not all branches write the same variables are not yet supported
//    helper.runTactic(tactic, new RootNode(parseToSequent(new File("examples/dev/t/tactics/Stuttering.key")))) shouldBe 'closed
  }

  "ETCS-safety-allwrites" should "be provable with explicit strategy" in {
    import edu.cmu.cs.ls.keymaera.tactics.BranchLabels.{indInitLbl,indStepLbl,indUseCaseLbl}
    import edu.cmu.cs.ls.keymaera.tactics.SearchTacticsImpl.onBranch
    import Tactics.NilT

    import scala.language.postfixOps
    val file = new File("examples/dev/t/tactics/ETCS-safety-allwrites.key")
    val s = parseToSequent(file)

    // sub strategies for SB cases
    def subsubtactic(testTactic: Tactic) = (ls(boxSeqT) ~
      ((ls(boxTestT) & ls(ImplyRightT)) | ls(boxAssignT) | ls(boxNDetAssign))*) &
      (la(AndLeftT)*) & ls(AndRightT) &&
      ((List(3,4,23) ++ (7 to 19) ++ (25 to 29)).sortWith(_ > _).foldLeft(NilT)((t, i) => t & eqLeft(exhaustive = true)(AntePosition(i)) & hideT(AntePosition(i))) & testTactic & arithmeticT & debugT("result 1"),
       (List(3,4,23) ++ (7 to 19) ++ (25 to 29)).sortWith(_ > _).foldLeft(NilT)((t, i) => t & eqLeft(exhaustive = true)(AntePosition(i)) & hideT(AntePosition(i))) & AxiomCloseT & debugT("result 2"))

    val subtactic = ((ls(boxSeqT) ~
      ((ls(boxTestT) & ls(ImplyRightT)) | ls(boxNDetAssign)))*) &
      la(AndLeftT) ~ ls(boxAssignT) & ls(boxSeqT) & ls(boxChoiceT) &
      ls(AndRightT) && (debugT("choice 2.2.*.1") & subsubtactic(la(OrLeftT)),
                                debugT("choice 2.2.*.2") & subsubtactic(la(NotLeftT) & ls(OrRightT) & arithmeticT))

    // main strategy
    val tactic = ls(ImplyRightT) & (la(AndLeftT)*) &
      ls(inductionT(Some("v^2-d^2 <= 2*b*(m-z) & d>=0".asFormula))) &
      onBranch(
        (indInitLbl, debugT("base case") & ls(AndRightT) && (AxiomCloseT(AntePosition(4), SuccPosition(0)),
                                       AxiomCloseT(AntePosition(3), SuccPosition(0)))),
        (indStepLbl, debugT("step") & ls(ImplyRightT) & la(AndLeftT) & ls(boxChoiceT) & ls(AndRightT) &&
          (debugT("choice 1") & ((ls(boxSeqT) ~ (ls(boxAssignT) | ls(boxNDetAssign)))*) &
                                ls(boxTestT) & ls(ImplyRightT) & (la(AndLeftT)*) &
                                ls(AndRightT) && (/* v,z not written without self-assignment */ arithmeticT,
                                                  AxiomCloseT),
            debugT("choice 2") & ls(boxChoiceT) & ls(AndRightT) &&
              /* {state:=brake} */
              (debugT("choice 2.1") & ((ls(boxSeqT) ~ ls(boxAssignT))*) &
                ls(AndRightT) && /* v,z,d,m, etc. not written without self-assignment */
                  /* explicit equality rewriting, just for demo purposes -> see eqLeft above for alternative */
                  /* numbering in positions: 0 -> lhs, 1 -> rhs
                   * e.g. in v^2-d^2 <= 2*b*(m-z) 0::0 refers to v^2, 0::0::0 to v, 0::0::1 to 2, 0::1::0 to d
                   *                              1::1::0 to m, 1::1::1 to z, 1::0 to 2*b
                   */
                  (/* v_2 */equalityRewriting(AntePosition(9), SuccPosition(0, PosInExpr(0::0::0::Nil))) & hideT(SuccPosition(0)) &
                    /* d_1 */equalityRewriting(AntePosition(16), SuccPosition(0, PosInExpr(0::1::0::Nil))) & hideT(SuccPosition(0)) &
                    /* m_1 */equalityRewriting(AntePosition(14), SuccPosition(0, PosInExpr(1::1::0::Nil))) & hideT(SuccPosition(0)) &
                    /* z_2 */ equalityRewriting(AntePosition(10), SuccPosition(0, PosInExpr(1::1::1::Nil))) & hideT(SuccPosition(0)) &
                    AxiomCloseT(AntePosition(5), SuccPosition(0)),
                   equalityRewriting(AntePosition(16), SuccPosition(0, PosInExpr(0::Nil))) & hideT(SuccPosition(0)) &
                     AxiomCloseT(AntePosition(6), SuccPosition(0))
                  ),
                debugT("choice 2.2") & ((ls(boxSeqT) ~ ls(boxAssignT))*) &
                  ls(boxChoiceT) & ls(AndRightT) &&
                  (debugT("choice 2.2.1") & subtactic,
                    debugT("choice 2.2.2") & subtactic
                  )
              )
            )),
        (indUseCaseLbl, debugT("use case") & ls(ImplyRightT) & la(AndLeftT) & arithmeticT))

    helper.runTactic(tactic, new RootNode(s)) shouldBe 'closed
  }

  it should "be provable with master strategy" in {
    val file = new File("examples/dev/t/tactics/ETCS-safety-allwrites.key")
    val s = parseToSequent(file)
    helper.runTactic(master(new Generate("v^2-d^2 <= 2*b*(m-z) & d>=0".asFormula), true), new RootNode(s)) shouldBe 'closed
  }

//  "ETCS-safety" should "be provable with master strategy" in {
//    val file = new File("examples/dev/t/tactics/ETCS-safety.key")
//    val s = parseToSequent(file)
//    helper.runTactic(master(new Generate("v^2-d^2 <= 2*b*(m-z) & d>=0".asFormula), true), new RootNode(s)) shouldBe 'closed
//  }

  "Saturable" should "be provable" in {
    val file = new File("examples/dev/t/tactics/Saturable.key")
    val s = parseToSequent(file)

    helper.runTactic(default, new RootNode(s)).isClosed() should be (true)
  }

  ignore should "prove SimpleDiff" in {
    val file = new File("examples/dev/t/tactics/SimpleDiff.key")
    val s = parseToSequent(file)

    helper.runTactic(default, new RootNode(s)).isClosed() should be (true)
  }

  "Simple car" should "be provable" in {
    import scala.language.postfixOps
    import edu.cmu.cs.ls.keymaera.tactics.BranchLabels.{indInitLbl,indStepLbl,indUseCaseLbl}
    import edu.cmu.cs.ls.keymaera.tactics.SearchTacticsImpl.onBranch
    import edu.cmu.cs.ls.keymaera.tactics.HybridProgramTacticsImpl.wipeContextInductionT
    import Tactics.NilT

    val file = new File("examples/dev/t/casestudies/tutorial/simplecar.key")
    val s = parseToSequent(file)

    val plantTactic = debugT("plant") & ls(boxSeqT) & ls(boxTestT) & ls(ImplyRightT) & ls(diffSolutionT) & arithmeticT

    val tactic = ls(ImplyRightT) & la(AndLeftT) &
      ls(wipeContextInductionT(Some("v>=0".asFormula))) &
      onBranch(
        (indInitLbl, debugT("init") & AxiomCloseT),
        (indStepLbl, debugT("step") & ls(ImplyRightT) &
          ls(boxSeqT) & ls(boxChoiceT) & ls(AndRightT) && (
            ls(boxAssignT) & plantTactic,
            ls(boxAssignT) & plantTactic
          )
          ),
        (indUseCaseLbl, debugT("use") & ls(ImplyRightT) & AxiomCloseT)
      )

    helper.runTactic(tactic, new RootNode(s)) shouldBe 'closed
  }
}
