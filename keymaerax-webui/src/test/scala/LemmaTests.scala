/**
* Copyright (c) Carnegie Mellon University. CONFIDENTIAL
* See LICENSE.txt for the conditions of this license.
*/
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.tactics.{RootNode, TacticLibrary}
import edu.cmu.cs.ls.keymaerax.tools.{Tool, Mathematica}
import testHelper.ProvabilityTestHelper
import testHelper.StringConverter._

import org.scalatest.{BeforeAndAfterEach, Matchers, FlatSpec}

import scala.collection.immutable.{Nil, Map}

/**
 * Created by smitsch on 3/5/15.
 * @author Stefan Mitsch
 */
class LemmaTests extends FlatSpec with Matchers with BeforeAndAfterEach {
  val helper = new ProvabilityTestHelper((x) => println(x))
  val mathematicaConfig: Map[String, String] = helper.mathematicaConfig
  var math: Tool with QETool = null

  override def beforeEach() = {
    math = new Mathematica
    math.init(mathematicaConfig)
  }

  override def afterEach() = {
    math.shutdown()
    math = null
  }

  "Tactics (Lemma)" should "learn a lemma from (x > 0 & y > x) -> x >= 0" in {
    val f = TacticLibrary.universalClosure("(x > 0 & y > x) -> x >= 0".asFormula)
    val lemmaDB = new FileLemmaDB
    val res = RCF.proveArithmetic(math, f)
    val id = LookupLemma.addLemma(lemmaDB, res)

    (res.fact.conclusion.succ.head match {
      case Equiv(_, True) => true
      case _ => false
    }) shouldBe true
    val r = new RootNode(new Sequent(Nil, Vector(), Vector(res.fact.conclusion.succ.head)))
    val t = LookupLemma(lemmaDB, id)
    r.apply(t) shouldBe 'closed
  }

  it should "learn a lemma from (x > 0 & y = x+1 & y > x) -> (x >= 0 & y > 0)" in {
    val f = TacticLibrary.universalClosure("(x > 0 & y = x+1 & y > x) -> (x >= 0 & y > 0)".asFormula)
    val lemmaDB = new FileLemmaDB
    val res = RCF.proveArithmetic(math, f)
    val id = LookupLemma.addLemma(lemmaDB, res)

    (res.fact.conclusion.succ.head match {
          case Equiv(_, True) => true
          case _ => false
    }) shouldBe true
    val r = new RootNode(new Sequent(Nil, Vector(), Vector(res.fact.conclusion.succ.head)))
    val t = LookupLemma(lemmaDB, id)
    r.apply(t) shouldBe 'closed
  }

  it should "learn a lemma from (x > 0 & y = x+1 & y > x) -> (y > 0)" in {
    val f = TacticLibrary.universalClosure("(x > 0 & y = x+1 & y > x) -> (y > 0)".asFormula)
    val lemmaDB = new FileLemmaDB
    val res = RCF.proveArithmetic(math, f)
    val id = LookupLemma.addLemma(lemmaDB, res)

    (res.fact.conclusion.succ.head match {
        case Equiv(_, True) => true
        case _ => false
    }) shouldBe true
    val r = new RootNode(new Sequent(Nil, Vector(), Vector(res.fact.conclusion.succ.head)))
    val t = LookupLemma(lemmaDB, id)
    r.apply(t) shouldBe 'closed
  }

  it should "learn a lemma from (x > 0 & y = x+1 & x+1 > x) -> (x+1 > 0)" in {
    val f = TacticLibrary.universalClosure("(x > 0 & y = x+1 & x+1 > x) -> (x+1 > 0)".asFormula)
    val lemmaDB = new FileLemmaDB
    val res = RCF.proveArithmetic(math, f)
    val id = LookupLemma.addLemma(lemmaDB, res)

    (res.fact.conclusion.succ.head match {
          case Equiv(_, True) => true
          case _ => false
    }) shouldBe true
    val r = new RootNode(new Sequent(Nil, Vector(), Vector(res.fact.conclusion.succ.head)))
    val t = LookupLemma(lemmaDB, id)
    r.apply(t) shouldBe 'closed
  }
}
