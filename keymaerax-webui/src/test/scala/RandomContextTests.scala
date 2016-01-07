/**
* Copyright (c) Carnegie Mellon University.
* See LICENSE.txt for the conditions of this license.
*/

import edu.cmu.cs.ls.keymaerax.btactics.{Augmentors, Context}
import edu.cmu.cs.ls.keymaerax.tactics.{PosInExpr, Position}
import edu.cmu.cs.ls.keymaerax.tools.KeYmaera
import testHelper.KeYmaeraXTestTags.{SlowTest, UsualTest, SummaryTest, CheckinTest}

import scala.collection.immutable._
import Augmentors._
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.parser._
import org.scalatest.{PrivateMethodTester, Matchers, FlatSpec}

import test.RandomFormula

/**
 * Tests the context splitting on randomly generated formulas
 * @author Andre Platzer
 */
class RandomContextTests extends FlatSpec with Matchers {
  KeYmaera.init(Map.empty)
  val randomTrials = 400
  val randomReps = 10
  val randomComplexity = 6
  val rand = new RandomFormula()


  def contextShouldBe[T<:Formula](origin: T, pos: PosInExpr): Boolean = {
    val (ctx,e) = try { origin.at(pos) } catch {
      case e: IllegalArgumentException => println("\nInput:      " + origin +
      "\nIllposition: " + pos); (Context(DotFormula), origin)
      case e: SubstitutionClashException => println("\nInput:      " + origin +
        "\nIllposition: " + pos); (Context(DotFormula), origin)
    }
    println("\n" +
        "\nInput:      " + origin +
        "\nPosition:   " + pos +
        "\nContext:    " + ctx +
        "\nArgument:   " + e)
    val reassemble = try { Some(ctx(e)) }
    catch {
      case e: SubstitutionClashException => println("Clashes can happen when reassembling ill-defined:\n" + e); None
    }
    println(
        "\nReassemble: " + reassemble.getOrElse(Variable("undefined")) +
        "\nExpected:  " + origin)
    if (reassemble.isDefined && !noCtx(ctx)) reassemble.get shouldBe origin
    true
  }

  private def noCtx(r:Context[Expression]): Boolean =
     StaticSemantics.signature(r.ctx).contains(noContext) || StaticSemantics.signature(r.ctx).contains(noContextD)

  //@todo DotProgram would in a sense be the appropriate context
  private val noContext = ProgramConst("noctx")
  private val noContextD = DifferentialProgramConst("noctxD")


  "The positioning" should "consistently split formulas (checkin)" taggedAs(CheckinTest) in {test(5,2)}
  it should "consistently split formulas (summary)" taggedAs(SummaryTest) in {test(20,8)}
  it should "consistently split formulas (usual)" taggedAs(UsualTest) in {test(50,10)}
  it should "consistently split formulas (slow)" taggedAs(SlowTest) in {test()}

  private def test(randomTrials: Int = randomTrials, randomReps: Int = randomReps, randomComplexity: Int = randomComplexity) =
    for (i <- 1 to randomTrials) {
      val f = rand.nextFormula(randomComplexity)
      for (j <- 1 to randomReps) {
        val pos = rand.nextPosition(randomComplexity).inExpr
        contextShouldBe(f, pos)
      }
    }

}
