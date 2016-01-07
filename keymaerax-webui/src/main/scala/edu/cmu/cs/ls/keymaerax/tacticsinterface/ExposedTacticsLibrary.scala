/**
* Copyright (c) Carnegie Mellon University.
* See LICENSE.txt for the conditions of this license.
*/
package edu.cmu.cs.ls.keymaerax.tacticsinterface

import edu.cmu.cs.ls.keymaerax.btactics.NoneGenerate
import edu.cmu.cs.ls.keymaerax.core.Formula
import edu.cmu.cs.ls.keymaerax.tactics.{HybridProgramTacticsImpl, Tactics, TacticLibrary}
import edu.cmu.cs.ls.keymaerax.tactics.Tactics.{PositionTactic, Tactic}

/**
 * These are tactics which are exposed to the tactics interface.
 *
 * All methods which take arguments must take a single argument of type Option[Formula].
 * For example, debugT isn't currently supported.
 *
 * There is no coherent naming convention. The first grouping of tactics is what's necessary for the paper's contents to
 * work out, whereas the second follow the naming of the rest of the tactics library (to the extent that the naming there
 * is consistent).
 *
 * Created by nfulton on 2/26/15.
 * @see [[edu.cmu.cs.ls.keymaerax.tactics.TactixLibrary]]
 */
object ExposedTacticsLibrary {
  // Utility Tactics
  def NilT : Tactic = Tactics.NilT

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Tactics used in the paper.
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Plain tactics
  def ImplyRight              : Tactic = TacticLibrary.locate(TacticLibrary.ImplyRightT)
  def Master                  : Tactic = TacticLibrary.master(new NoneGenerate(), true, "Mathematica")
//  def SpecificMaster(toolId : String) : Tactic = TacticLibrary.master(new NoneGenerate(), true, toolId)
  def Seq                     : Tactic = TacticLibrary.locate(TacticLibrary.boxSeqT)
  def Choice                  : Tactic = TacticLibrary.locate(TacticLibrary.boxChoiceT)
  def AndRight                : Tactic = TacticLibrary.locate(TacticLibrary.AndRightT)
  def Assign                  : Tactic = TacticLibrary.locate(TacticLibrary.boxAssignT)
  def ODESolve                : Tactic = TacticLibrary.locate(TacticLibrary.diffSolutionT)
  def Test                    : Tactic = TacticLibrary.locate(TacticLibrary.boxTestT)
  def AndLeft                 : Tactic = TacticLibrary.locate(TacticLibrary.AndLeftT)
  def NDetAssign              : Tactic = TacticLibrary.locate(TacticLibrary.boxNDetAssign)
  def ForallRight             : Tactic = TacticLibrary.locateSucc(TacticLibrary.skolemizeT)
  def DiffWeaken              : Tactic = TacticLibrary.locateSucc(TacticLibrary.diffWeakenT)
  def DiffConstify            : Tactic = TacticLibrary.locateSucc(TacticLibrary.diffConstifyT)
  def DiffInvariant           : Tactic = TacticLibrary.locate(TacticLibrary.diffInvariant)

  //Tactics with input
  def Loop(inv : Option[Formula]) : Tactic = TacticLibrary.locate(TacticLibrary.inductionT(inv))
  def DiffCut(input : Option[Formula]) : Tactic = TacticLibrary.locate(TacticLibrary.diffCutT(input.getOrElse(throw new Exception("need a defined input"))))

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Additional Tactics.
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  def CutT(inv : Option[Formula])  : Tactic         = TacticLibrary.cutT(inv)
  def ImplyRightT                  : PositionTactic = TacticLibrary.ImplyRightT
  def ImplyLeftT                   : PositionTactic = TacticLibrary.ImplyLeftT
  def AndRightT                    : PositionTactic = TacticLibrary.AndRightT
  def OrRightT                     : PositionTactic = TacticLibrary.OrRightT
  def ArithmeticT                  : Tactic         = TacticLibrary.arithmeticT
//  def SpecificArithmeticT(toolId : String) : Tactic         = TacticLibrary.arithmeticT(toolId)
  def AxiomCloseT                  : Tactic         = TacticLibrary.AxiomCloseT
}

/*
At least this should work:

main = ImplyRight & Loop("v>=0") & onLabel(
("base case", Master),
("induction step", ImplyRight & Seq & Choice & AndRight &&
(Assign & ODESolve & Master,
Assign & ODESolve & Master) ),
("use case", Master)
)
 */