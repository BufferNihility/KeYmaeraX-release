/**
* Copyright (c) Carnegie Mellon University. CONFIDENTIAL
* See LICENSE.txt for the conditions of this license.
*/
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.parser.StringConverter._
import edu.cmu.cs.ls.keymaerax.tools.JLinkMathematicaLink
import org.scalatest.{BeforeAndAfterEach, Matchers, FlatSpec}
import testHelper.ProvabilityTestHelper

import scala.collection.immutable.Map

/**
 * Tests the JLink Mathematica implementation.
 * @author Stefan Mitsch
 */
class JLinkMathematicaLinkTests extends FlatSpec with Matchers with BeforeAndAfterEach {

  val helper = new ProvabilityTestHelper((x) => println(x))
  val mathematicaConfig: Map[String, String] = helper.mathematicaConfig
  private var link: JLinkMathematicaLink = null

  private val x = Variable("x", None, Real)
  private val y = Variable("y", None, Real)
  private val z = Variable("z", None, Real)
  private val t = Variable("t", None, Real)
  private val x0 = Function("x0", None, Unit, Real)
  private val y0 = Function("y0", None, Unit, Real)
  private val one = Number(BigDecimal(1))

  override def beforeEach() = {
    link = new JLinkMathematicaLink
    link.init(mathematicaConfig("linkName"), None)
  }

  override def afterEach() = {
    link.shutdown()
    link = null
  }

  "x'=1" should "x=x0+y*t with AtomicODE" in {
    val eq = AtomicODE(DifferentialSymbol(x), one)
    val expected = Some("x=x0()+t".asFormula)
    link.diffSol(eq, t,  Map(x->x0)) should be (expected)
  }

  "x'=y, y'=z" should "y=y0+z*t and x=x0+y0*t+z/2*t^2 with ContProduct" in {
    val eq = DifferentialProduct(
      AtomicODE(DifferentialSymbol(x), y),
      AtomicODE(DifferentialSymbol(y), z))
    val expected = Some("x=1/2*(2*x0() + 2*y0()*t + t^2*z) & y=y0() + t*z".asFormula)
    link.diffSol(eq, t, Map(x->x0, y->y0)) should be (expected)
  }

  "x'=y, t'=1" should "x=x0+y*t with ContProduct" in {
    // special treatment of t for now
    val eq = DifferentialProduct(
      AtomicODE(DifferentialSymbol(x), y),
      AtomicODE(DifferentialSymbol(t), one))
    val expected = Some("x=x0() + t*y".asFormula)
    link.diffSol(eq, t, Map(x->x0)) should be (expected)
  }

  "abs(-5) > 4" should "be provable with QE" in {
    link.qe("abs(-5) > 4".asFormula) shouldBe True
  }

  "min(1,3) = 1" should "be provable with QE" in {
    link.qe("min(1,3) = 1".asFormula) shouldBe True
  }

  "max(1,3) = 3" should "be provable with QE" in {
    link.qe("max(1,3) = 3".asFormula) shouldBe True
  }
}
