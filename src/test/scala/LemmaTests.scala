import java.math.BigDecimal

import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.tactics.{RootNode, Interpreter, TacticLibrary, Config}
import org.scalatest.{BeforeAndAfterEach, Matchers, FlatSpec}

import scala.Equals
import scala.collection.immutable.{Nil, Map}

/**
 * Created by smitsch on 3/5/15.
 * @author Stefan Mitsch
 */
class LemmaTests extends FlatSpec with Matchers with BeforeAndAfterEach {

  val mathematicaConfig : Map[String, String] = Map("linkName" -> "/Applications/Mathematica.app/Contents/MacOS/MathKernel")
  var math: Mathematica = null

  val x = Variable("x", None, Real)
  val y = Variable("y", None, Real)
  val zero = Number(new BigDecimal("0"))
  val one = Number(new BigDecimal("1"))

  val xgeq0 = GreaterEqual(Real, x, zero)
  val xgt0 = GreaterThan(Real, x, zero)
  val xplus1 = Add(Real, x, one)
  val xplus1gtx = GreaterThan(Real, xplus1, x)

  override def beforeEach() = {
    math = new Mathematica
    math.init(mathematicaConfig)
  }

  override def afterEach() = {
    math.shutdown()
    math = null
  }

  "Tactics (Lemma)" should "learn a lemma from (x > 0 & y > x) -> x >= 0" in {
    val f = TacticLibrary.universalClosure(Imply(And(xgt0, GreaterThan(Real, y, x)), xgeq0))
    LookupLemma.addRealArithLemma(math, f) match {
      case Some((file, id, res)) =>
        (res match {
          case Equiv(_, True) => true
          case _ => false
        }) should be (true)
        val r = new RootNode(new Sequent(Nil, Vector(), Vector()))
        val t = LookupLemma(file,id)
        val nr = r.apply(t).subgoals.head
        nr.sequent.ante(nr.sequent.ante.length-1) should be (res)
      case None => "Lemma creation" should be ("successful")
    }
  }

  it should "learn a lemma from (x > 0 & y = x+1 & y > x) -> (x >= 0 & y > 0)" in {
    val f = TacticLibrary.universalClosure(Imply(And(And(xgt0, Equals(Real, y, xplus1)), GreaterThan(Real, y, x)), And(xgeq0, GreaterThan(Real, y, zero))))
    LookupLemma.addRealArithLemma(math, f) match {
      case Some((file, id, res)) =>
        (res match {
          case Equiv(_, True) => true
          case _ => false
        }) should be (true)
        val r = new RootNode(new Sequent(Nil, Vector(), Vector()))
        val t = LookupLemma(file,id)
        val nr = r.apply(t).subgoals.head
        nr.sequent.ante(nr.sequent.ante.length-1) should be (res)
      case None => "Lemma creation" should be ("successful")
    }
  }

  it should "learn a lemma from (x > 0 & y = x+1 & y > x) -> (y > 0)" in {
    val f = TacticLibrary.universalClosure(Imply(And(And(xgt0, Equals(Real, y, xplus1)), GreaterThan(Real, y, x)), GreaterThan(Real, y, zero)))
    LookupLemma.addRealArithLemma(math, f) match {
      case Some((file, id, res)) =>
        (res match {
          case Equiv(_, True) => true
          case _ => false
        }) should be (true)
        val r = new RootNode(new Sequent(Nil, Vector(), Vector()))
        val t = LookupLemma(file,id)
        val nr = r.apply(t).subgoals.head
        nr.sequent.ante(nr.sequent.ante.length-1) should be (res)
      case None => "Lemma creation" should be ("successful")
    }
  }

  it should "learn a lemma from (x > 0 & y = x+1 & x+1 > x) -> (x+1 > 0)" in {
    val f = TacticLibrary.universalClosure(Imply(And(And(xgt0, Equals(Real, y, xplus1)), GreaterThan(Real, xplus1, x)), GreaterThan(Real, xplus1, zero)))
    LookupLemma.addRealArithLemma(math, f) match {
      case Some((file, id, res)) =>
        (res match {
          case Equiv(_, True) => true
          case _ => false
        }) should be (true)
        val r = new RootNode(new Sequent(Nil, Vector(), Vector()))
        val t = LookupLemma(file,id)
        val nr = r.apply(t).subgoals.head
        nr.sequent.ante(nr.sequent.ante.length-1) should be (res)
      case None => "Lemma creation" should be ("successful")
    }
  }
}
