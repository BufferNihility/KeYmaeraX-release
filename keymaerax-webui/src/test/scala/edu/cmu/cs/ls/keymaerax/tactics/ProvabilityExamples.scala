/**
* Copyright (c) Carnegie Mellon University.
* See LICENSE.txt for the conditions of this license.
*/
package edu.cmu.cs.ls.keymaerax.tactics

import edu.cmu.cs.ls.keymaerax.tactics.{Interpreter, Tactics, TacticLibrary}
import edu.cmu.cs.ls.keymaerax.tools.{KeYmaera, Mathematica}
import edu.cmu.cs.ls.keymaerax.tactics.ProvabilityTestHelper
import org.scalatest.{BeforeAndAfterEach, Matchers, FlatSpec}

import scala.collection.immutable.Map

/**
 * Created by nfulton on 12/6/14.
 * @author Nathan Fulton
 */
class ProvabilityExamples extends FlatSpec with Matchers with BeforeAndAfterEach {
  val helper = new ProvabilityTestHelper()
  import helper._ // import helper methods for thests.

  //Mathematica
  val mathematicaConfig: Map[String, String] = helper.mathematicaConfig

  override def beforeEach() = {
    Tactics.KeYmaeraScheduler = new Interpreter(KeYmaera)
    Tactics.MathematicaScheduler = new Interpreter(new Mathematica)

    Tactics.KeYmaeraScheduler.init(Map())
    Tactics.MathematicaScheduler.init(mathematicaConfig)
  }

  override def afterEach() = {
    Tactics.KeYmaeraScheduler.shutdown()
    Tactics.MathematicaScheduler.shutdown()
    Tactics.KeYmaeraScheduler = null
    Tactics.MathematicaScheduler = null
  }


  "The Default Tactic" should "handle obvious proofs" in {
    val problem1 = parseFormula("x=1 -> x=1")
    tacticClosesProof(TacticLibrary.default, formulaToNode(problem1)) should be (true)
  }

  it should "handle assignments" in {
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
