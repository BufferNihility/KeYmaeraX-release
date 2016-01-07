/**
* Copyright (c) Carnegie Mellon University.
* See LICENSE.txt for the conditions of this license.
*/

import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.parser.StringConverter._
import edu.cmu.cs.ls.keymaerax.tactics.PosInExpr
import org.scalatest.{Matchers, FlatSpec}
import edu.cmu.cs.ls.keymaerax.tactics.Context
import edu.cmu.cs.ls.keymaerax.tactics.FormulaConverter._

/**
 * Created by smitsch on 3/26/15.
 * @author Stefan Mitsch
 */
class FormulaConverterTests extends FlatSpec with Matchers {

  "Context extraction" should "extract context from conjunction" in {
    val f = "a=b & x=y".asFormula
    val result = f.extractContext(PosInExpr(1::Nil))
    result shouldBe (new Context(And("a=b".asFormula, DotFormula)), "x=y".asFormula)
    result._1(result._2) shouldBe f
  }

  it should "extract context from universal quantifier" in {
    val f = "\\forall x x=y".asFormula
    val result = f.extractContext(PosInExpr(0::Nil))
    result should be (new Context(Forall(Variable("x", None, Real)::Nil, DotFormula)), "x=y".asFormula)
    result._1(result._2) shouldBe f
  }

  it should "extract context from existential quantifier" in {
    val f = "\\exists x x=y".asFormula
    val result = f.extractContext(PosInExpr(0::Nil))
    result should be (new Context(Exists(Variable("x", None, Real)::Nil, DotFormula)), "x=y".asFormula)
    result._1(result._2) shouldBe f
  }

  it should "extract context from nested quantifiers" in {
    val f = "\\forall x \\exists y x=y".asFormula
    val result = f.extractContext(PosInExpr(0::0::Nil))
    result should be
      (new Context(Forall(Variable("x", None, Real)::Nil, Exists(Variable("y", None, Real)::Nil, DotFormula))),
       "x=y".asFormula)
    result._1(result._2) shouldBe f
  }

  it should "extract context for terms" in {
    val f = "a=b & x=y".asFormula
    val result = f.extractContext(PosInExpr(1::0::Nil))
    result shouldBe (new Context(And("a=b".asFormula, Equal(DotTerm, "y".asTerm))), "x".asTerm)
    result._1(result._2) shouldBe f
  }
}