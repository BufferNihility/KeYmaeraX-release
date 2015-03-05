import edu.cmu.cs.ls.keymaera.core.Mathematica
import edu.cmu.cs.ls.keymaera.tactics.{Tactics, Config, TacticLibrary}
import edu.cmu.cs.ls.keymaera.tests.ProvabilityTestHelper
import org.scalatest.{BeforeAndAfterEach, Matchers, FlatSpec}

import scala.collection.immutable.Map

/**
 * Created by nfulton on 12/6/14.
 */
class ProvabilityExamples extends FlatSpec with Matchers with BeforeAndAfterEach {
  val helper = new ProvabilityTestHelper()
  import helper._ // import helper methods for thests.

  //Mathematica
  Config.maxCPUs = 1
  Config.mathlicenses = 1
  val mathematicaConfig: Map[String, String] = helper.mathematicaConfig

  override def beforeEach() = {
    Tactics.KeYmaeraScheduler.init(Map())
    Tactics.MathematicaScheduler.init(mathematicaConfig)
  }

  override def afterEach() = {
    Tactics.KeYmaeraScheduler.shutdown()
    Tactics.MathematicaScheduler.shutdown()
  }


  "The Default Tactic" should "handle assignments" in {
    val problem1 = parseFormula("[x:=1;]x=1")
    tacticClosesProof(TacticLibrary.default, formulaToNode(problem1)) should be (true)
  }

  it should "timeout" in {
    val problem = parseFormula("1782^12 + 1841^12 != 1922^12")
    //hopefully this is false because things time out
    tacticFinishesAndClosesProof(1, TacticLibrary.default, formulaToNode(problem)) should be (false)
    //but if we want to be very sure the timeout causes the false result, we can make sure the result is None vs. Some(not closed):
    runTacticWithTimeout(1, TacticLibrary.default, formulaToNode(problem)) should be (None)
  }

}
