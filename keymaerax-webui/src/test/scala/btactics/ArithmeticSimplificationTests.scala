/*
 * Copyright (c) Carnegie Mellon University.
 * See LICENSE.txt for the conditions of this license.
 */

package btactics

import edu.cmu.cs.ls.keymaerax.bellerophon.{SequentialInterpreter, TheType}
import edu.cmu.cs.ls.keymaerax.parser.StringConverter._
import edu.cmu.cs.ls.keymaerax.btactics.{ArithmeticSimplification, TacticTestBase, TactixLibrary, ToolTactics}
import edu.cmu.cs.ls.keymaerax.core.Provable

/**
  * @author Nathan Fulton
  */
class ArithmeticSimplificationTests extends TacticTestBase {
  "smartHide" should "simplify x=1,y=1 ==> x=1 to x=1 ==> x=1" in {withMathematica(implicit qeTool => {
    val tactic = TactixLibrary.implyR(1) & TactixLibrary.andL(-1) & ArithmeticSimplification.smartHide
    val result = proveBy("x=1 & y=1 -> x=1".asFormula, tactic)
    result.subgoals(0).ante shouldBe result.subgoals(0).succ
  })}

  it should "not throw away transitivity info" in {withMathematica(implicit qeTool => {
    val tactic = TactixLibrary.implyR(1) & TactixLibrary.andL('L)*@(TheType()) & ArithmeticSimplification.smartHide
    val goal = "x=y & y=z & z > 0 -> x>0".asFormula
    val result = proveBy(goal, tactic)
    result.subgoals(0).ante.length shouldBe 3
    proveBy(goal, tactic & TactixLibrary.QE) shouldBe 'proved
  })}

  it should "forget useless stuff" in {withMathematica(implicit qeTool => {
    val tactic = TactixLibrary.implyR(1) & TactixLibrary.andL('L)*@(TheType()) & ArithmeticSimplification.smartHide
    val goal = "x>y & y>z & a > 0 & z > 0 -> x>0".asFormula
    val result = proveBy(goal, tactic)
    result.subgoals(0).ante.length shouldBe 3 //forget about a>0
    result.subgoals(0).ante.contains("a>0".asFormula) shouldBe false
    proveBy(goal, tactic & TactixLibrary.QE) shouldBe 'proved
  })}
}