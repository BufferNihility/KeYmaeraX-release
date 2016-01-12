/**
* Copyright (c) Carnegie Mellon University.
* See LICENSE.txt for the conditions of this license.
*/

package edu.cmu.cs.ls.keymaerax.tactics

import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.parser.StringConverter._
import edu.cmu.cs.ls.keymaerax.tactics.ODETactics._
import edu.cmu.cs.ls.keymaerax.tactics.TacticLibrary._
import edu.cmu.cs.ls.keymaerax.tactics.{RootNode, HybridProgramTacticsImpl, ODETactics}
import ProofFactory._
import testHelper.SequentFactory._
import edu.cmu.cs.ls.keymaerax.tags.ObsoleteTest

/**
 * Created by nfulton on 2/22/15.
 * @author Nathan Fulton
 * @author Stefan Mitsch
 */
@ObsoleteTest
class DiffInvIntegrationTests extends TacticTestSuite {

  "Assign" should "work" in {
    val f = "[a := 0;]a = 0".asFormula
    val node = helper.formulaToNode(f)
    helper.runTactic(helper.positionTacticToTactic(HybridProgramTacticsImpl.boxAssignT), node)
    helper.report(node)
    node.openGoals().flatMap(_.sequent.ante) should contain only "a_1=0".asFormula
    node.openGoals().flatMap(_.sequent.succ) should contain only "a_1=0".asFormula
  }

  "Diff Assign" should "work with 1 box" in {
    val f = "[a' := 1;]a'=1".asFormula
    val node = helper.formulaToNode(f)
    helper.runTactic(helper.positionTacticToTactic(HybridProgramTacticsImpl.boxDerivativeAssignT), node)
    node.openGoals().flatMap(_.sequent.ante) shouldBe empty
    node.openGoals().flatMap(_.sequent.succ) should contain only "1=1".asFormula
  }

  it should "work with 1 box and an unprimed occurance" in {
    val f = "[a' := 1;](a=1 -> a'=1)".asFormula
    val node = helper.formulaToNode(f)
    helper.runTactic(helper.positionTacticToTactic(HybridProgramTacticsImpl.boxDerivativeAssignT), node)
    node.openGoals().flatMap(_.sequent.ante) shouldBe empty
    node.openGoals().flatMap(_.sequent.succ) should contain only "a=1 -> 1=1".asFormula
  }

  "diff inv tactic" should "work" in {
    val s = sequent(Nil, "x>=0".asFormula::Nil, "[{x' = 2}]x>=0".asFormula :: Nil)
    val t = locateSucc(ODETactics.diffInvariantT)
    val n = helper.runTactic(t, new RootNode(s))
    n shouldBe 'closed
    // without arithmetic tactic at the end:
//    n.openGoals().flatMap(_.sequent.ante) should contain only "x>=0".asFormula
//    n.openGoals().flatMap(_.sequent.succ) should contain only "!true | 2>=0".asFormula
  }

  it should "work with conjunction in inv" in {
    val s = sequent(Nil, "x>=0 & x>=x".asFormula::Nil, "[{x' = 2}](x>=0 & x>=x)".asFormula :: Nil)
    val t = locateSucc(ODETactics.diffInvariantT)
    val n = helper.runTactic(t, new RootNode(s))
    n shouldBe 'closed
    // without arithmetic tactic at the end:
//    n.openGoals().flatMap(_.sequent.ante) should contain only "x>=0 & x>=x".asFormula
//    n.openGoals().flatMap(_.sequent.succ) should contain only "!true | (2>=0 & 2>=2)".asFormula
  }

  it should "work with disjunction in inv" in {
    val s = sequent(Nil, "x>=0 & x>=x".asFormula::Nil, "[{x' = 2}](x>=0 | x>=x)".asFormula :: Nil)
    val t = locateSucc(ODETactics.diffInvariantT)
    val n = helper.runTactic(t, new RootNode(s))
    n shouldBe 'closed
    // without arithmetic tactic at the end:
//    n.openGoals().flatMap(_.sequent.ante) should contain only "x>=0 & x>=x".asFormula
//    n.openGoals().flatMap(_.sequent.succ) should contain only "!true | (2>=0 & 2>=2)".asFormula
  }

  it should "work with implication in inv" in {
    val s = sequent(Nil, "x>=0 -> x>=x".asFormula::Nil, "[{x' = 2}](x>=0 -> x>=x)".asFormula :: Nil)
    val t = locateSucc(ODETactics.diffInvariantT)
    val n = helper.runTactic(t, new RootNode(s))
    n.openGoals() should have size 1
    n.openGoals().head.sequent.ante shouldBe empty
    n.openGoals().head.sequent.succ should contain only "\\forall x_0 ((x_0>=0 -> x_0>=x_0)&true&true -> 2<=0 & 2>=2)".asFormula
  }

  // Needed when we want to cut in universally quantified stuff
  ignore should "work with universal quantifier in inv" in {
    val s = sequent(Nil, "\\forall t 0<=t".asFormula::Nil, "[{x' = 2, t'=1 & 0<=t}](\\forall t 0<=t)".asFormula :: Nil)
    val t = locateSucc(ODETactics.diffInvariantT)
    val n = helper.runTactic(t, new RootNode(s))
    n.openGoals().flatMap(_.sequent.ante) should contain only ("\\forall t 0<=t".asFormula, "true".asFormula)
    n.openGoals().flatMap(_.sequent.succ) should contain only "2<=0 & 2>=2".asFormula
  }

  it should "derive constant symbols to 0" in {
    val s = sequent(Nil, "x>=0 & y()>=0".asFormula::Nil, "[{x' = 2}](x>=0 & y()>=0)".asFormula :: Nil)
    val t = locateSucc(ODETactics.diffInvariantT)
    val n = helper.runTactic(t, new RootNode(s))
    // 2>=0 && 0>=0
    n shouldBe 'closed
  }

  it should "derive multiplication" in {
    val s = sequent(Nil, "x>=0 & y()>=0".asFormula::Nil, "[{x' = 2}](x*y()>=0)".asFormula :: Nil)
    val t = locateSucc(ODETactics.diffInvariantT)
    val n = helper.runTactic(t, new RootNode(s))
    // x*0 + 2*y()>=0
    n shouldBe 'closed
  }

  it should "derive nested multiplication" in {
    val s = sequent(Nil, "x>=0 & y()>=0 & z>=0".asFormula::Nil, "[{x' = 2, z'=1}](x*y()*z>=0)".asFormula :: Nil)
    val t = locateSucc(ODETactics.diffInvariantT)
    val n = helper.runTactic(t, new RootNode(s))
    // x*(0*z + y()*1) + 2*(y()*z)>=0
    n shouldBe 'closed
  }

  it should "derive division" in {
    val s = sequent(Nil, "x>=0 & y()>0".asFormula::Nil, "[{x' = 2}](x/y()>=0)".asFormula :: Nil)
    val t = locateSucc(ODETactics.diffInvariantT)
    val n = helper.runTactic(t, new RootNode(s))
    // x*0 + 2*y()>=0
    n shouldBe 'closed
  }

  // infinite loop (might also be caused by pretty printer issue because nothing ever closes)
  ignore should "work with a complicated example" in {
    val s = sequent(Nil, Nil, "[{x' = y, y' = x & x^2 + y^2 = 4}]1=1".asFormula::Nil)
    val t = locateSucc(ODETactics.diffInvariantT)
    val n = helper.runTactic(t, new RootNode(s))
    n.openGoals().flatMap(_.sequent.ante) shouldBe empty
    n.openGoals().flatMap(_.sequent.succ) should contain only "!(x^2+y^2=4) | [x':=y;](!true | 0=0)".asFormula
  }
}
