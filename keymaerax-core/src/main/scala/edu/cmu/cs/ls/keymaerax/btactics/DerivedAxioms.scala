/**
 * Copyright (c) Carnegie Mellon University. CONFIDENTIAL
 * See LICENSE.txt for the conditions of this license.
 */
package edu.cmu.cs.ls.keymaerax.btactics

import edu.cmu.cs.ls.keymaerax.bellerophon.{USubstPatternTactic, SequentType, BelleExpr}
import edu.cmu.cs.ls.keymaerax.btactics.TactixLibrary._
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.lemma.LemmaDBFactory
import edu.cmu.cs.ls.keymaerax.parser.StringConverter._
import edu.cmu.cs.ls.keymaerax.tactics.{AntePosition, PosInExpr, Position, SuccPosition}
import edu.cmu.cs.ls.keymaerax.tools.ToolEvidence

import scala.annotation.switch
import scala.collection.immutable
import scala.collection.immutable._

/**
 * Derived Axioms.
 * @author Andre Platzer
 * @see [[edu.cmu.cs.ls.keymaerax.core.AxiomBase]]
 */
object DerivedAxioms {

  /** Database for derived axioms */
  val derivedAxiomDB = LemmaDBFactory.lemmaDB
  type LemmaID = String

  /** A Provable proving the derived axiom named id (convenience) */
  def derivedAxiom(name: String): Provable =
    Provable.startProof(Sequent(Nil, IndexedSeq(), IndexedSeq(derivedAxiomFormula(name).get)))(derivedAxiomR(name), 0)

  private val AUTO_INSERT = true

  /** Derive an axiom from the given provable, package it up as a Lemma and make it available */
  private[btactics] def derivedAxiom(name: String, fact: Provable): Lemma = {
    require(fact.isProved, "only proved Provables would be accepted as derived axioms: " + name + " got\n" + fact)
    // create evidence (traces input into tool and output from tool)
    val evidence = new ToolEvidence(immutable.Map("input" -> fact.toString, "output" -> "true")) :: Nil
    val lemmaName = axiom2lemmaName(name)
    val lemma = Lemma(fact, evidence, Some(lemmaName))
    if (!AUTO_INSERT) {
      lemma
    } else {
      // first check whether the lemma DB already contains identical lemma name
      val lemmaID = if (derivedAxiomDB.contains(lemmaName)) {
        // identical lemma contents with identical name, so reuse ID
        if (derivedAxiomDB.get(lemmaName).contains(lemma)) lemma.name.get
        else throw new IllegalStateException("Prover already has a different lemma filed under the same name " + derivedAxiomDB.get(lemmaName) + " (lemma " + name + " stored in file name " + lemmaName + ")")
      } else {
        derivedAxiomDB.add(lemma)
      }
      derivedAxiomDB.get(lemmaID).get
    }
  }

  /** Derive an axiom for the given derivedAxiom with the given tactic, package it up as a Lemma and make it available */
  private[btactics] def derivedAxiom(name: String, derived: Sequent, tactic: BelleExpr): Lemma =
    derivedAxiomDB.get(axiom2lemmaName(name)) match {
      case Some(lemma) => lemma
      case None =>
        val witness = TactixLibrary.proveBy(derived, tactic)
        assert(witness.isProved, "tactics proving derived axioms should produce proved Provables: " + name + " got\n" + witness)
        derivedAxiom(name, witness)
    }

  /** Package a Lemma for a derived axiom up as a rule */
  private[btactics] def derivedAxiomR(name: String): LookupLemma = {
    val lemmaName = axiom2lemmaName(name)
    require(derivedAxiomDB.contains(lemmaName), "Lemma has already been added")
    LookupLemma(derivedAxiomDB, lemmaName)
  }

  /** Package a Lemma for a derived axiom up as a tactic */
  private[btactics] def derivedAxiomT(lemma: Lemma): BelleExpr = {
    require(derivedAxiomDB.contains(lemma.name.get), "Lemma has already been added")
    val lemma2axiomName = axiom2lemmaName.map(_.swap)
    require(lemma2axiomName.contains(lemma.name.get), s"Lemma with name ${lemma.name.get} must prove an axiom")
    val axiomName = lemma2axiomName.get(lemma.name.get).get
    ProofRuleTactics.applyRule(derivedAxiomR(axiomName))
  }

  private val x = Variable("x_", None, Real)
  private val px = PredOf(Function("p_", None, Real, Bool), x)
  private val pany = PredOf(Function("p_", None, Real, Bool), Anything)
  private val qx = PredOf(Function("q_", None, Real, Bool), x)
  private val qany = PredOf(Function("q_", None, Real, Bool), Anything)
  private val fany = FuncOf(Function("f_", None, Real, Real), Anything)
  private val gany = FuncOf(Function("g_", None, Real, Real), Anything)
  private val ctxt = Function("ctx_", None, Real, Real) // function symbol
  private val ctxf = Function("ctx_", None, Real, Bool) // predicate symbol
  private val context = Function("ctx_", None, Bool, Bool) // predicational symbol

//  /**
//   * Looks up a tactic by name to derive an axiom.
//   * @note For interface stability reasons (see [[AxiomTactic.axiomLookupBaseT()]])
//   * @param name The name of the derived axiom.
//   * @return The tactic to apply the derived axiom, if found. None otherwise.
//   */
//  def derivedAxiomTactic(name: String): Option[BelleExpr] = derivedAxiomInfo(name) match {
//    case Some((_, t)) => Some(t)
//    case None => None
//  }
//
  /**
   * Looks up a derived axiom formula by name.
   * @param name The name of the derived axiom.
   * @return The axiom formula, if found. None otherwise.
   */
  def derivedAxiomFormula(name: String): Option[Formula] = derivedAxiomInfo(name) match {
    case Some((fml, _)) => Some(fml)
    case None => None
  }

  //@note enables consistent axiom names as well as valid file names on all platforms
  private val axiom2lemmaName: Map[String, String] = Map(
    "<-> reflexive" -> "equiv reflexive",
    "-> distributes over &" -> "imply distributes over and",
    "-> distributes over <->" -> "imply distributes over equiv",
    "-> weaken" -> "imply weaken",
    "!! double negation" -> "double negation",
    "exists dual" -> "exists dual",
    "!exists" -> "exists negate",
    "!all" -> "all negate",
    "![]" -> "box negate",
    "!<>" -> "diamond negate",
    "[] dual" -> "box dual",
    "K1" -> "K1",
    "K2" -> "K2",
    "[] split" -> "box split",
    "[] split left" -> "box split left",
    "[] split right" -> "box split right",
    "<> split" -> "diamond split",
    "<> split left" -> "diamond split left",
    "<:=> assign" -> "diamond assign",
    ":= assign dual" -> "assign dial",
    "[:=] assign equational" -> "box assign equational",
    "[:=] assign update" -> "box assign update",
    "[:=] vacuous assign" -> "box vacuous assign",
    "<:=> assign equational" -> "diamond assign equational",
    "<:=> assign update" -> "diamond assign update",
    "<:=> vacuous assign" -> "diamond vacuous assign",
    "<':=> differential assign" -> "diamond differential assign",
    "<:*> assign nondet" -> "diamond assign nondet",
    "<?> test" -> "diamond test",
    "<++> choice" -> "diamond choice",
    "<;> compose" -> "diamond compose",
    "<*> iterate" -> "diamond iterate",
    "<*> approx" -> "diamond approx",
    "[*] approx" -> "diamond approx",
    "exists generalize" -> "exists generalize",
    "all substitute" -> "all substitute",
    "vacuous exists quantifier" -> "vacuous exists quantifier",
    "V[:*] vacuous assign nondet" -> "V box vacuous assign nondet",
    "V<:*> vacuous assign nondet" -> "V diamond vacuous assign nondet",
    "DX diamond differential skip" -> "DX diamond differential skip",
    "DW differential weakening" -> "DW differential weakening",
    "DS differential equation solution" -> "DS differential equation solution",
    "Dsol& differential equation solution" -> "Dsol domain differential equation solution",
    "Dsol differential equation solution" -> "Dsol differential equation solution",
    "Domain Constraint Conjunction Reordering" -> "Domain Constraint Conjunction Reordering",
    "[] post weaken" -> "box post weaken",
    "& commute" -> "and commute",
    "& associative" -> "and associative",
    "& reflexive" -> "and reflexive",
    "!& deMorgan" -> "not and deMorgan",
    "!| deMorgan" -> "not or deMorgan",
    "!-> deMorgan" -> "not imply deMorgan",
    "!<-> deMorgan" -> "not equiv deMorgan",
    "-> expand" -> "imply expand",
    "-> self" -> "imply self",
    "PC1" -> "PC1",
    "PC2" -> "PC2",
    "PC3" -> "PC3",
    "PC9" -> "PC9",
    "PC10" -> "PC10",
    "-> tautology" -> "imply tautology",
    "->' derive imply" -> "derive imply",
    "\\forall->\\exists" -> "forall imply exists",
    "->true" -> "imply true",
    "true->" -> "true imply",
    "&true" -> "and true",
    "true&" -> "true and",
    "0*" -> "zero times",
    "0+" -> "zero plus",
    "x' derive variable" -> "derive variable",
    "' linear" -> "prime linear",
    "' linear right" -> "prime linear right",
    "DG differential pre-ghost" -> "DG differential pre-ghost",
    "= reflexive" -> "equal reflexive",
    "* commute" -> "times commute",
    "= commute" -> "equal commute",
    "<=" -> "lessEqual expand",
    "= negate" -> "equal negate",
    "!= negate" -> "notEqual negate",
    "! <" -> "less negate",
    "! <=" -> "lessEqual negate",
    "! >" -> "greater negate",
    ">= flip" -> "greaterEqual flip",
    "> flip" -> "greater flip",
    "<" -> "less normalize",
    ">" -> "greater flip",
    "abs" -> "abs",
    "min" -> "min",
    "max" -> "max",
    "<*> stuck" -> "diamond loop stuck",
    "<'> stuck" -> "diamond prime stuck",
    "+<= up" -> "interval plus up",
    "-<= up" -> "interval minus up",
    "*<= up" -> "interval times up",
    "1Div<= up" -> "interval 1divide up",
    "Div<= up" -> "interval divide up",
    "<=+ down" -> "interval plus down",
    "<=- down" -> "interval minus down",
    "<=* down" -> "interval times down",
    "<=1Div down" -> "interval 1divide down",
    "<=Div down" -> "interval divide down"
  )

  /**
   * Looks up information about a derived axiom by name.
   * @param name The name of the derived axiom.
   * @return The axiom formula and tactic, if found. None otherwise.
   * @note Central index for looking up derived axioms by names.
   */
  private def derivedAxiomInfo(name: String): Option[(Formula, BelleExpr)] = (name: @switch) match {
    //@note implemented as match rather than lookup in a map to retain lazy evaluation
    //@note Every entry should be added to derivedAxiomMap (we need a map when prepopulating the lemma database, so whenever adding a case to this case match also add an entry to the hashmap below.)
    case "<-> reflexive" => Some(equivReflexiveF, equivReflexiveT)
    case "-> distributes over &" => Some(implyDistAndF, implyDistAndT)
    case "-> distributes over <->" => Some(implyDistEquivF, implyDistEquivT)
    case "-> weaken" => Some(implWeakenF, implWeakenT)
    case "!! double negation" => Some(doubleNegationF, doubleNegationT)
    case "exists dual" => Some(existsDualF, existsDualT)
    case "!exists" => Some(notExistsF, notExistsT)
    case "!all" => Some(notAllF, notAllT)
    case "![]" => Some(notBoxF, notBoxT)
    case "!<>" => Some(notDiamondF, notDiamondT)
    case "[] dual" => Some(boxDualF, boxDualT)
    case "K1" => Some(K1F, K1T)
    case "K2" => Some(K2F, K2T)
    case "[] split" => Some(boxSplitF, boxSplitT)
    case "[] split left" => Some(boxSplitLeftF, boxSplitLeftT)
    case "[] split right" => Some(boxSplitRightF, boxSplitRightT)
    case "<> split" => Some(diamondSplitF, diamondSplitT)
    case "<> split left" => Some(diamondSplitLeftF, diamondSplitLeftT)
    case "<:=> assign" => Some(assigndF, assigndT)
    case ":= assign dual" => Some(assignDualF, assignDualT)
    case "[:=] assign equational" => Some(assignbEquationalF, assignbEquationalT)
    case "[:=] assign update" => Some(assignbUpdateF, assignbUpdateT)
    case "[:=] vacuous assign" => Some(vacuousAssignbF, vacuousAssignbT)
    case "<:=> assign equational" => ??? //Some(assigndEquationalF, assigndEquationalT)
    case "<:=> assign update" => Some(assigndUpdateF, assigndUpdateT)
    case "<:=> vacuous assign" => Some(vacuousAssigndF, vacuousAssigndT)
    case "<':=> differential assign" => Some(assignDF, assignDT)
    case "<:*> assign nondet" => Some(nondetassigndF, nondetassigndT)
    case "<?> test" => Some(testdF, testdT)
    case "<++> choice" => Some(choicedF, choicedT)
    case "<;> compose" => Some(composedF, composedT)
    case "<*> iterate" => Some(iteratedF, iteratedT)
    case "<*> approx" => Some(loopApproxdF, loopApproxdT)
    case "[*] approx" => Some(loopApproxbF, loopApproxbT)
    case "exists generalize" => Some(existsGeneralizeF, existsGeneralizeT)
    //case "exists eliminate" => Some(existsEliminateF, existsEliminateT)
    case "all substitute" => Some(allSubstituteF, allSubstituteT)
    case "vacuous exists quantifier" => Some(vacuousExistsF, vacuousExistsT)
    case "V[:*] vacuous assign nondet" => Some(vacuousBoxAssignNondetF, vacuousBoxAssignNondetT)
    case "V<:*> vacuous assign nondet" => Some(vacuousDiamondAssignNondetF, vacuousDiamondAssignNondetT)
    case "DX diamond differential skip" => Some(DskipdF, DskipdT)
    case "DW differential weakening" => Some(DWeakeningF, DWeakeningT)
    case "DS differential equation solution" => Some(DSnodomainF, DSnodomainT)
    case "Dsol& differential equation solution" => Some(DSddomainF, DSddomainT)
    case "Dsol differential equation solution" => Some(DSdnodomainF, DSdnodomainT)
    case "Domain Constraint Conjunction Reordering" => Some(domainCommuteF, domainCommuteT)
    case "[] post weaken" => Some(postconditionWeakenF, postconditionWeakenT)
    case "& commute" => Some(andCommuteF, andCommuteT)
    case "& associative" => Some(andAssocF, andAssocT)
    case "& reflexive" => Some(andReflexiveF, andReflexiveT)
    case "!& deMorgan" => Some(notAndF, notAndT)
    case "!| deMorgan" => Some(notOrF, notOrT)
    case "!-> deMorgan" => Some(notImplyF, notImplyT)
    case "!<-> deMorgan" => Some(notEquivF, notEquivT)
    case "-> expand" => Some(implyExpandF, implyExpandT)
    case "-> self" => Some(implySelfF, implySelfT)
    case "PC1" => Some(PC1F, PC1T)
    case "PC2" => Some(PC2F, PC2T)
    case "PC3" => Some(PC3F, PC3T)
    case "PC9" => Some(PC9F, PC9T)
    case "PC10" => Some(PC10F, PC10T)
    case "-> tautology" => Some(implyTautologyF, implyTautologyT)
    case "->' derive imply" => Some(DimplyF, DimplyT)
    case "\\forall->\\exists" => Some(forallThenExistsF, forallThenExistsT)
    case "->true" => Some(impliesTrueF, impliesTrueT)
    case "true->" => Some(trueImpliesF, trueImpliesT)
    case "&true" => Some(andTrueF, andTrueT)
    case "true&" => Some(trueAndF, trueAndT)
    case "0*" => Some(zeroTimesF, zeroTimesT)
    case "0+" => Some(zeroPlusF, zeroPlusT)
    //    case "x' derive var" => Some(DvarF, DvarT)
    case "x' derive variable" => Some(DvariableF, DvariableT)
    case "' linear" => Some(DlinearF, DlinearT)
    case "' linear right" => Some(DlinearRightF, DlinearRightT)
    case "DG differential pre-ghost" => Some(DGpreghostF, DGpreghostT)
    case "= reflexive" => Some(equalReflexiveF, equalReflexiveT)
    case "* commute" => Some(timesCommuteF, timesCommuteT)
    case "= commute" => Some(equalCommuteF, equalCommuteT)
    case "<=" => Some(lessEqualF, lessEqualT)
    case "= negate" => Some(notNotEqualF, notNotEqualT)
    case "!= negate" => Some(notEqualF, notEqualT)
    case "! !=" => derivedAxiomInfo("= negate")
    case "! =" => Some(notEqualF, notEqualT)
    case "! <" => Some(notLessF, notLessT)
    case "! <=" => Some(notLessEqualF, notLessEqualT)
    case "! >" => Some(notGreaterF, notGreaterT)
    case "! >=" => derivedAxiomInfo("< negate")
    case "< negate" => Some(notGreaterEqualF, notGreaterEqualT)
    case ">= flip" => Some(flipGreaterEqualF, flipGreaterEqualT)
    case "> flip" => Some(flipGreaterF, flipGreaterT)
    case "<" => Some(lessF, lessT)
    case ">" => Some(greaterF, greaterT)
    case "abs" => Some(absF, absT)
    case "min" => Some(minF, minT)
    case "max" => Some(maxF, maxT)
    case "<*> stuck" => Some(loopStuckF, loopStuckT)
    case "<'> stuck" => Some(odeStuckF, odeStuckT)
    case "+<= up" => Some(intervalUpPlusF, intervalUpPlusT)
    case "-<= up" => Some(intervalUpMinusF, intervalUpMinusT)
    case "*<= up" => Some(intervalUpTimesF, intervalUpTimesT)
    case "1Div<= up" => Some(intervalUp1DivideF, intervalUp1DivideT)
    case "Div<= up" => Some(intervalUpDivideF, intervalUpDivideT)
    case "<=+ down" => Some(intervalDownPlusF, intervalDownPlusT)
    case "<=- down" => Some(intervalDownMinusF, intervalDownMinusT)
    case "<=* down" => Some(intervalDownTimesF, intervalDownTimesT)
    case "<=1Div down" => Some(intervalDown1DivideF, intervalDown1DivideT)
    case "<=Div down" => Some(intervalDownDivideF, intervalDownDivideT)
    case _ => None
  }

  /** populates the derived lemma database with all of the lemmas in the case statement above.*/
  def prepopulateDerivedLemmaDatabase() = {
    require(AUTO_INSERT, "AUTO_INSERT should be on if lemma database is being pre-populated.")
    //@note copied from derivedAxiomInfo.
    val derivedAxiomMap = HashMap(
      "<-> reflexive" -> Some(equivReflexiveF, equivReflexiveT)
      , "-> distributes over &" -> Some(implyDistAndF, implyDistAndT)
      , "-> distributes over <->" -> Some(implyDistEquivF, implyDistEquivT)
      , "-> weaken" -> Some(implWeakenF, implWeakenT)
      , "!! double negation" -> Some(doubleNegationF, doubleNegationT)
      , "exists dual" -> Some(existsDualF, existsDualT)
      , "!exists" -> Some(notExistsF, notExistsT)
      , "!all" -> Some(notAllF, notAllT)
      , "![]" -> Some(notBoxF, notBoxT)
      , "!<>" -> Some(notDiamondF, notDiamondT)
      , "[] dual" -> Some(boxDualF, boxDualT)
      , "[] split" -> Some(boxSplitF, boxSplitT)
      , "[] split left" -> Some(boxSplitLeftF, boxSplitLeftT)
      , "[] split right" -> Some(boxSplitRightF, boxSplitRightT)
      , "<> split" -> Some(diamondSplitF, diamondSplitT)
      , "<> split left" -> Some(diamondSplitLeftF, diamondSplitLeftT)
      , "<:=> assign" -> Some(assigndF, assigndT)
      , ":= assign dual" -> Some(assignDualF, assignDualT)
      , "[:=] assign equational" -> Some(assignbEquationalF, assignbEquationalT)
      , "[:=] assign update" -> Some(assignbUpdateF, assignbUpdateT)
      , "[:=] vacuous assign" -> Some(vacuousAssignbF, vacuousAssignbT)
      //      , "<:=> assign equational" -> ??? //Some(assigndEquationalF, assigndEquationalT)
      , "<:=> assign update" -> Some(assigndUpdateF, assigndUpdateT)
      , "<:=> vacuous assign" -> Some(vacuousAssigndF, vacuousAssigndT)
      , "<':=> differential assign" -> Some(assignDF, assignDT)
      , "<:*> assign nondet" -> Some(nondetassigndF, nondetassigndT)
      , "<?> test" -> Some(testdF, testdT)
      , "<++> choice" -> Some(choicedF, choicedT)
      , "<;> compose" -> Some(composedF, composedT)
      , "<*> iterate" -> Some(iteratedF, iteratedT)
      , "<*> approx" -> Some(loopApproxdF, loopApproxdT)
      , "[*] approx" -> Some(loopApproxbF, loopApproxbT)
      , "exists generalize" -> Some(existsGeneralizeF, existsGeneralizeT)
      //@todo , "exists eliminate" -> Some(existsEliminateF, existsEliminateT)
      , "all substitute" -> Some(allSubstituteF, allSubstituteT)
      , "vacuous exists quantifier" -> Some(vacuousExistsF, vacuousExistsT)
      , "V[:*] vacuous assign nondet" -> Some(vacuousBoxAssignNondetF, vacuousBoxAssignNondetT)
      , "V<:*> vacuous assign nondet" -> Some(vacuousDiamondAssignNondetF, vacuousDiamondAssignNondetT)
      , "DX diamond differential skip" -> Some(DskipdF, DskipdT)
      , "DW differential weakening" -> Some(DWeakeningF, DWeakeningT)
      , "DS differential equation solution" -> Some(DSnodomainF, DSnodomainT)
      , "Dsol& differential equation solution" -> Some(DSddomainF, DSddomainT)
      , "Dsol differential equation solution" -> Some(DSdnodomainF, DSdnodomainT)
      , "Domain Constraint Conjunction Reordering" -> Some(domainCommuteF, domainCommuteT)
      , "[] post weaken" -> Some(postconditionWeakenF, postconditionWeakenT)
      , "& commute" -> Some(andCommuteF, andCommuteT)
      , "& associative" -> Some(andAssocF, andAssocT)
      , "!& deMorgan" -> Some(notAndF, notAndT)
      , "!| deMorgan" -> Some(notOrF, notOrT)
      , "!-> deMorgan" -> Some(notImplyF, notImplyT)
      , "!<-> deMorgan" -> Some(notEquivF, notEquivT)
      , "-> expand" -> Some(implyExpandF, implyExpandT)
      , "-> tautology" -> Some(implyTautologyF, implyTautologyT)
      , "->' derive imply" -> Some(DimplyF, DimplyT)
      , "\\forall->\\exists" -> Some(forallThenExistsF, forallThenExistsT)
      , "->true" -> Some(impliesTrueF, impliesTrueT)
      , "true->" -> Some(trueImpliesF, trueImpliesT)
      , "&true" -> Some(andTrueF, andTrueT)
      , "true&" -> Some(trueAndF, trueAndT)
      , "0*" -> Some(zeroTimesF, zeroTimesT)
      , "0+" -> Some(zeroPlusF, zeroPlusT)
      //    , "x' derive var" -> Some(DvarF, DvarT)
      , "x' derive variable" -> Some(DvariableF, DvariableT)
      , "' linear" -> Some(DlinearF, DlinearT)
      //      , "' linear right" -> Some(DlinearRightF, DlinearRightT)
      , "DG differential pre-ghost" -> Some(DGpreghostF, DGpreghostT)
      , "= reflexive" -> Some(equalReflexiveF, equalReflexiveT)
      , "* commute" -> Some(timesCommuteF, timesCommuteT)
      , "= commute" -> Some(equalCommuteF, equalCommuteT)
      , "<=" -> Some(lessEqualF, lessEqualT)
      , "= negate" -> Some(notNotEqualF, notNotEqualT)
      , "!= negate" -> Some(notEqualF, notEqualT)
      //      , "! !=" -> derivedAxiomInfo("= negate")
      //      , "! =" -> Some(notEqualF, notEqualT)
      , "! <" -> Some(notLessF, notLessT)
      //      , "! <=" -> Some(notLessEqualF, notLessEqualT)
      , "! >" -> Some(notGreaterF, notGreaterT)
      //      , "! >=" -> derivedAxiomInfo("< negate")
      , "< negate" -> Some(notGreaterEqualF, notGreaterEqualT)
      , ">= flip" -> Some(flipGreaterEqualF, flipGreaterEqualT)
      , "> flip" -> Some(flipGreaterF, flipGreaterT)
      , "<" -> Some(lessF, lessT)
      , ">" -> Some(greaterF, greaterT)
      , "abs" -> Some(absF, absT)
      , "min" -> Some(minF, minT)
      , "max" -> Some(maxF, maxT)
      , "<*> stuck" -> Some(loopStuckF, loopStuckT)
      , "<'> stuck" -> Some(odeStuckF, odeStuckT)
      , "+<= up" -> Some(intervalUpPlusF, intervalUpPlusT)
      , "-<= up" -> Some(intervalUpMinusF, intervalUpMinusT)
      , "*<= up" -> Some(intervalUpTimesF, intervalUpTimesT)
      , "1Div<= up" -> Some(intervalUp1DivideF, intervalUp1DivideT)
      , "Div<= up" -> Some(intervalUpDivideF, intervalUpDivideT)
      , "<=+ down" -> Some(intervalDownPlusF, intervalDownPlusT)
      , "<=- down" -> Some(intervalDownMinusF, intervalDownMinusT)
      , "<=* down" -> Some(intervalDownTimesF, intervalDownTimesT)
      , "<=1Div down" -> Some(intervalDown1DivideF, intervalDown1DivideT)
      , "<=Div down" -> Some(intervalDownDivideF, intervalDownDivideT)
    ) ensuring(r => r.forall(kv => derivedAxiomInfo(kv._1)==kv._2), "same contents as derivedAxiomInfo()")

    derivedAxiomMap.keys.map(key => {
      val proof: Provable = derivedAxiom(key)
      derivedAxiom(key, proof)
    })
  }

  /**
   * {{{Axiom "<-> reflexive".
   *  p() <-> p()
   * End.
   * }}}
   * @Derived
   */
  lazy val equivReflexiveF = "p() <-> p()".asFormula
  lazy val equivReflexiveAxiom = derivedAxiom("<-> reflexive",
    Provable.startProof(Sequent(Nil, IndexedSeq(), IndexedSeq(equivReflexiveF)))
      (EquivRight(SuccPos(0)), 0)
      // right branch
      (Close(AntePos(0),SuccPos(0)), 1)
      // left branch
      (Close(AntePos(0),SuccPos(0)), 0)
  )

  lazy val equivReflexiveT = derivedAxiomT(equivReflexiveAxiom)

  /**
   * {{{Axiom "-> distributes over &".
   *  (p() -> (q()&r())) <-> ((p()->q()) & (p()->r()))
   * End.
   * }}}
   * @Derived
   */
  lazy val implyDistAndF = "(p() -> (q()&r())) <-> ((p()->q()) & (p()->r()))".asFormula
  lazy val implyDistAndAxiom = derivedAxiom("-> distributes over &",
    Sequent(Nil, IndexedSeq(), IndexedSeq(implyDistAndF)),
    prop
  )

  lazy val implyDistAndT = derivedAxiomT(implyDistAndAxiom)

  /**
   * {{{Axiom "-> weaken".
   *  (p() -> q()) -> (p()&c() -> q())
   * End.
   * }}}
   * @Derived
   */
  lazy val implWeakenF = "(p() -> q()) -> ((p()&c()) -> q())".asFormula
  lazy val implWeaken = derivedAxiom("-> weaken",
    Sequent(Nil, IndexedSeq(), IndexedSeq(implWeakenF)),
    prop
  )

  lazy val implWeakenT = derivedAxiomT(implWeaken)

  /**
   * {{{Axiom "-> distributes over <->".
   *  (p() -> (q()<->r())) <-> ((p()->q()) <-> (p()->r()))
   * End.
   * }}}
   * @Derived
   */
  lazy val implyDistEquivF = "(p() -> (q()<->r())) <-> ((p()->q()) <-> (p()->r()))".asFormula
  lazy val implyDistEquivAxiom = derivedAxiom("-> distributes over <->",
    Sequent(Nil, IndexedSeq(), IndexedSeq(implyDistEquivF)),
    prop
  )

  lazy val implyDistEquivT = derivedAxiomT(implyDistEquivAxiom)

  /**
   * {{{Axiom "!! double negation".
   *  (!(!p())) <-> p()
   * End.
   * }}}
   * @Derived
   */
  lazy val doubleNegationF = "(!(!p())) <-> p()".asFormula
  lazy val doubleNegationAxiom = derivedAxiom("!! double negation",
    Provable.startProof(Sequent(Nil, IndexedSeq(), IndexedSeq(doubleNegationF)))
      (EquivRight(SuccPos(0)), 0)
      // right branch
      (NotRight(SuccPos(0)), 1)
      (NotLeft(AntePos(1)), 1)
      (Close(AntePos(0),SuccPos(0)), 1)
      // left branch
      (NotLeft(AntePos(0)), 0)
      (NotRight(SuccPos(1)), 0)
      (Close(AntePos(0),SuccPos(0)), 0)
  )

  lazy val doubleNegationT = derivedAxiomT(doubleNegationAxiom)

  /**
   * {{{Axiom "exists dual".
   *   (!\forall x (!p(x))) <-> (\exists x p(x))
   * End.
   * }}}
   * @Derived
   */
  lazy val existsDualF = "(!\\forall x (!p(x))) <-> \\exists x p(x)".asFormula
  lazy val existsDualAxiom = derivedAxiom("exists dual",
    Sequent(Nil, IndexedSeq(), IndexedSeq(existsDualF)),
    useAt("all dual", PosInExpr(1::Nil))(SuccPosition(0, 0::0::Nil)) &
      useAt(doubleNegationAxiom)(SuccPosition(0, 0::Nil)) &
      useAt(doubleNegationAxiom)(SuccPosition(0, 0::0::Nil)) &
      byUS(equivReflexiveAxiom)
  )

  lazy val existsDualT = derivedAxiomT(existsDualAxiom)

  /**
   * {{{Axiom "!exists".
   *   (!\exists x (p(x))) <-> \forall x (!p(x))
   * End.
   * }}}
   * @Derived
   */
  lazy val notExistsF = "(!\\exists x (p(x))) <-> \\forall x (!p(x))".asFormula
  lazy val notExists = derivedAxiom("!exists",
    Sequent(Nil, IndexedSeq(), IndexedSeq(notExistsF)),
    useAt("!! double negation", PosInExpr(1::Nil))(1, 0::0::0::Nil) &
      useAt("all dual")(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val notExistsT = derivedAxiomT(notExists)

  /**
   * {{{Axiom "!all".
   *   (!\forall x (p(x))) <-> \exists x (!p(x))
   * End.
   * }}}
   * @Derived
   */
  lazy val notAllF = "(!\\forall x (p(x))) <-> \\exists x (!p(x))".asFormula
  lazy val notAll = derivedAxiom("!all",
    Sequent(Nil, IndexedSeq(), IndexedSeq(notAllF)),
    useAt("!! double negation", PosInExpr(1::Nil))(1, 0::0::0::Nil) &
      useAt("exists dual")(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val notAllT = derivedAxiomT(notAll)

  /**
   * {{{Axiom "![]".
   *   ![a;]p(x) <-> <a;>!p(x)
   * End.
   * }}}
   * @Derived
   */
  lazy val notBoxF = "(![a;]p(x)) <-> (<a;>!p(x))".asFormula
  lazy val notBox = derivedAxiom("![]",
    Sequent(Nil, IndexedSeq(), IndexedSeq(notBoxF)),
    useAt("!! double negation", PosInExpr(1::Nil))(1, 0::0::1::Nil) &
      useAt("<> dual")(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val notBoxT = derivedAxiomT(notBox)

  /**
   * {{{Axiom "!<>".
   *   !<a;>p(x) <-> [a;]!p(x)
   * End.
   * }}}
   * @Derived
   */
  lazy val notDiamondF = "(!<a;>p(x)) <-> ([a;]!p(x))".asFormula
  lazy val notDiamond = derivedAxiom("!<>",
    Sequent(Nil, IndexedSeq(), IndexedSeq(notDiamondF)),
    useAt("!! double negation", PosInExpr(1::Nil))(1, 0::0::1::Nil) &
      useAt("[] dual")(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val notDiamondT = derivedAxiomT(notDiamond)

  /**
   * {{{Axiom "all eliminate".
   *    (\forall x p(??)) -> p(??)
   * End.
   * }}}
   * @todo will clash unlike the converse proof.
   */
  lazy val allEliminateF = "(\\forall x p(??)) -> p(??)".asFormula
  lazy val allEliminateAxiom = derivedAxiom("all eliminate",
    Sequent(Nil, IndexedSeq(), IndexedSeq(allEliminateF)),
    US(SubstitutionPair(PredOf(Function("p",None,Real,Bool),DotTerm), PredOf(Function("p",None,Real,Bool),Anything))::Nil)
  )
  lazy val allEliminateT = derivedAxiomT(allEliminateAxiom)

  /**
   * {{{Axiom "all distribute".
   *   (\forall x (p(x)->q(x))) -> ((\forall x p(x))->(\forall x q(x)))
   * }}}
   */
  // Imply(Forall(Seq(x), Imply(PredOf(p,x),PredOf(q,x))), Imply(Forall(Seq(x),PredOf(p,x)), Forall(Seq(x),PredOf(q,x))))
  lazy val allDistributeF = "(\\forall x (p(x)->q(x))) -> ((\\forall x p(x))->(\\forall x q(x)))".asFormula
  lazy val allDistributeAxiom = derivedAxiom("all distribute",
    Sequent(Nil, IndexedSeq(), IndexedSeq(allDistributeF)),
    implyR(1) & implyR(1) &
      allR(1) &
      allL(Variable("x",None,Real), Variable("x",Some(0),Real))(-2) &
      allL(Variable("x",None,Real), Variable("x",Some(0),Real))(-1) &
      prop
  )
  lazy val allDistributeT = derivedAxiomT(allDistributeAxiom)

  /**
   * {{{Axiom "all quantifier scope".
   *    (\forall x (p(x) & q())) <-> ((\forall x p(x)) & q())
   * End.
   * }}}
   * @todo follows from "all distribute" and "all vacuous"
   */


  /**
   * {{{Axiom "[] dual".
   *    (!<a;>(!p(??))) <-> [a;]p(??)
   * End.
   * }}}
   * @note almost same proof as "exists dual"
   * @Derived
   */
  lazy val boxDualF = "(!<a;>(!p(??))) <-> [a;]p(??)".asFormula
  lazy val boxDualAxiom = derivedAxiom("[] dual",
    Sequent(Nil, IndexedSeq(), IndexedSeq(boxDualF)),
    useAt("<> dual", PosInExpr(1::Nil))(SuccPosition(0, 0::0::Nil)) &
      useAt(doubleNegationAxiom)(SuccPosition(0, 0::Nil)) &
      useAt(doubleNegationAxiom)(SuccPosition(0, 0::1::Nil)) &
      byUS(equivReflexiveAxiom)
  )

  lazy val boxDualT = derivedAxiomT(boxDualAxiom)

  /**
   * {{{Axiom "".
   *    [a;]p(??) & <a;>q(??) -> <a;>(p(??) & q(??))
   * End.
   * }}}
   * @Derived
   */
  lazy val boxDiamondPropagationF = "([a;]p(??) & <a;>q(??)) -> <a;>(p(??) & q(??))".asFormula
  lazy val boxDiamondPropagation = derivedAxiom("[]~><> propagation",
    Sequent(Nil, IndexedSeq(), IndexedSeq(boxDiamondPropagationF)),
    useAt("<> dual", PosInExpr(1::Nil))(1, 0::1::Nil) &
      useAt("<> dual", PosInExpr(1::Nil))(1, 1::Nil) &
      cut("[a;]p(??) & [a;]!(p(??)&q(??)) -> [a;]!q(??)".asFormula) <(
      /* show */ hideR(1) &
        cut("[a;](p(??) & !(p(??)&q(??)))".asFormula) <(
          /* show */ implyR(1) & splitb(1) & prop,
          /* use */ implyR(1) & hideL(-1) & monb & prop
      ),
      /* use */ prop
    )
  )

  lazy val boxDiamondPropagationT = derivedAxiomT(boxDiamondPropagation)

  /**
   * {{{Axiom "K1".
   *   [a;](p(??)&q(??)) -> [a;]p(??) & [a;]q(??)
   * End.
   * }}}
   * @Derived
   * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, K1 p. 26
   */
  lazy val K1F = "[a;](p(??)&q(??)) -> [a;]p(??) & [a;]q(??)".asFormula
  lazy val K1 = derivedAxiom("K1",
    Sequent(Nil, IndexedSeq(), IndexedSeq(K1F)),
    implyR(1) & andR(1) <(
      useAt("[] split left")(-1) & close,
      useAt("[] split right")(-1) & close
      )
  )
  lazy val K1T = derivedAxiomT(K1)

  /**
   * {{{Axiom "K2".
   *   [a;]p(??) & [a;]q(??) -> [a;](p(??)&q(??))
   * End.
   * }}}
   * @Derived
   * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, K2 p. 27
   */
  lazy val K2F = "[a;]p(??) & [a;]q(??) -> [a;](p(??)&q(??))".asFormula
  lazy val K2 = derivedAxiom("K2",
    Sequent(Nil, IndexedSeq(), IndexedSeq(K2F)),
    cut(/*(9)*/"([a;](q(??)->p(??)&q(??)) -> ([a;]q(??) -> [a;](p(??)&q(??))))  ->  (([a;]p(??) & [a;]q(??)) -> [a;](p(??)&q(??)))".asFormula) <(
      /* show */ cut(/*(8)*/"([a;]p(??) -> [a;](q(??) -> p(??)&q(??)))  ->  (([a;](q(??)->p(??)&q(??)) -> ([a;]q(??) -> [a;](p(??)&q(??))))  ->  (([a;]p(??) & [a;]q(??)) -> [a;](p(??)&q(??))))".asFormula) <(
        /* show */ cohide(3) & prop,
        /* use */ cut(/*(5)*/"[a;]p(??) -> [a;](q(??) -> p(??)&q(??))".asFormula) <(
          /* show */ cohide(3) & useAt("K modal modus ponens", PosInExpr(1::Nil))(1) & useAt("-> tautology")(1, 1::Nil) & V(1) & close,
          /* use */ modusPonens(AntePos(1), AntePos(0)) & close
        )
      ),
      /* use */ cut(/*(6)*/"[a;](q(??) -> (p(??)&q(??)))  ->  ([a;]q(??) -> [a;](p(??)&q(??)))".asFormula) <(
        /* show */ cohide(2) & USubstPatternTactic(
        (SequentType(Sequent(Nil, IndexedSeq(), IndexedSeq("[a;](p(??)->q(??)) -> (([a;]p(??)) -> ([a;]q(??)))".asFormula))),
          (ru: RenUSubst) => ru.getRenamingTactic & ProofRuleTactics.axiomatic("K modal modus ponens", ru.substitution.usubst))::Nil),
//          uniformSubstT(
//            SubstitutionPair(PredOf(Function("p", None, Real, Bool), Anything), "q(??)".asFormula) ::
//              SubstitutionPair(PredOf(Function("q", None, Real, Bool), Anything), "p(??)&q(??)".asFormula) :: Nil,
//            Map(/*(6)*/"[a;](q(??) -> (p(??)&q(??)))  ->  ([a;]q(??) -> [a;](p(??)&q(??)))".asFormula -> /*K*/"[a;](p(??)->q(??)) -> (([a;]p(??)) -> ([a;]q(??)))".asFormula)) &
//          AxiomTactic.axiomT("K modal modus ponens"),
        /* use */ modusPonens(AntePos(1), AntePos(0)) & close
      )
    )
  )
  lazy val K2T = derivedAxiomT(K2)

  /**
   * {{{Axiom "[] split".
   *    [a;](p(??)&q(??)) <-> [a;]p(??)&[a;]q(??)
   * End.
   * }}}
   * @Derived
   * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, K3 p. 28
   */
  lazy val boxSplitF = "[a;](p(??)&q(??)) <-> [a;]p(??)&[a;]q(??)".asFormula
  lazy val boxSplit = derivedAxiom("[] split",
    Sequent(Nil, IndexedSeq(), IndexedSeq(boxSplitF)),
    equivR(1) <(
      useAt("K1", PosInExpr(1::Nil))(1) & close,
      useAt("K2", PosInExpr(1::Nil))(1) & close
    )
  )
  lazy val boxSplitT = derivedAxiomT(boxSplit)

  /**
   * {{{Axiom "boxSplitLeft".
   *    [a;](p(??)&q(??)) -> [a;]p(??)
   * End.
   * }}}
   * @Derived
   * @Note implements (1)-(5) of Cresswell, Hughes. A New Introduction to Modal Logic, K1
   */
  lazy val boxSplitLeftF = "[a;](p(??)&q(??)) -> [a;]p(??)".asFormula
  lazy val boxSplitLeft = derivedAxiom("[] split left",
    Sequent(Nil, IndexedSeq(), IndexedSeq(boxSplitLeftF)),
    cut(/*(2)*/"[a;](p(??)&q(??) -> p(??))".asFormula) <(
      /* show */ cohide(2) & useAt("PC1")(1, 1::0::Nil) & useAt("-> self")(1, 1::Nil) & V(1) & close,
      /* use */ cut(/*(4)*/"[a;](p(??)&q(??)->p(??)) -> ([a;](p(??)&q(??)) -> [a;]p(??))".asFormula) <(
        /* show */ cohide(2) & USubstPatternTactic(
          (SequentType(Sequent(Nil, IndexedSeq(), IndexedSeq("[a;](p(??)->q(??)) -> (([a;]p(??)) -> ([a;]q(??)))".asFormula))),
            (ru: RenUSubst) => ru.getRenamingTactic & ProofRuleTactics.axiomatic("K modal modus ponens", ru.substitution.usubst))::Nil),
//          uniformSubstT(
//            SubstitutionPair(PredOf(Function("p", None, Real, Bool), Anything), "p(??)&q(??)".asFormula) ::
//              SubstitutionPair(PredOf(Function("q", None, Real, Bool), Anything), "p(??)".asFormula) :: Nil,
//            Map(/*(4)*/"[a;](p(??)&q(??)->p(??)) -> ([a;](p(??)&q(??)) -> [a;]p(??))".asFormula -> /*K*/"[a;](p(??)->q(??)) -> (([a;]p(??)) -> ([a;]q(??)))".asFormula)
//          ) & AxiomTactic.axiomT("K modal modus ponens"),
        /* use */ modusPonens(AntePosition(0), AntePosition(1)) & close
      )
    )
  )

  lazy val boxSplitLeftT = derivedAxiomT(boxSplitLeft)

  /**
   * {{{Axiom "<> split".
   *    <a;>(p(??)|q(??)) <-> <a;>p(??)|<a;>q(??)
   * End.
   * }}}
   * @Derived
   */
  lazy val diamondSplitF = "<a;>(p(??)|q(??)) <-> <a;>p(??)|<a;>q(??)".asFormula
  lazy val diamondSplit = derivedAxiom("<> split",
    Sequent(Nil, IndexedSeq(), IndexedSeq(diamondSplitF)),
    useAt("<> dual", PosInExpr(1::Nil))(SuccPosition(0, PosInExpr(0::Nil))) &
      useAt("<> dual", PosInExpr(1::Nil))(SuccPosition(0, PosInExpr(1::0::Nil))) &
      useAt("<> dual", PosInExpr(1::Nil))(SuccPosition(0, PosInExpr(1::1::Nil))) &
      useAt("!| deMorgan")(SuccPosition(0, PosInExpr(0::0::1::Nil))) &
      useAt("[] split")(SuccPosition(0, PosInExpr(0::0::Nil))) &
      useAt("!& deMorgan")(SuccPosition(0, PosInExpr(0::Nil))) &
      byUS("<-> reflexive")
  )
  lazy val diamondSplitT = derivedAxiomT(diamondSplit)

  /**
   * {{{Axiom "<> split left".
   *    <a;>(p(??)&q(??)) -> <a;>p(??)
   * End.
   * }}}
   * @Derived
   */
  lazy val diamondSplitLeftF = "<a;>(p(??)&q(??)) -> <a;>p(??)".asFormula
  lazy val diamondSplitLeft = derivedAxiom("<> split left",
    Sequent(Nil, IndexedSeq(), IndexedSeq(diamondSplitLeftF)),
    useAt("PC1")(1, 0::1::Nil) & useAt("-> self")(1) & close
  )
  lazy val diamondSplitLeftT = derivedAxiomT(diamondSplitLeft)

  /**
   * {{{Axiom "boxSplitRight".
   *    [a;](p(??)&q(??)) -> q(??)
   * End.
   * }}}
   * @Derived
   * @Note implements (6)-(9) of Cresswell, Hughes. A New Introduction to Modal Logic, K1
   */
  lazy val boxSplitRightF = "[a;](p(??)&q(??)) -> [a;]q(??)".asFormula
  lazy val boxSplitRight = derivedAxiom("[] split right",
    Sequent(Nil, IndexedSeq(), IndexedSeq(boxSplitRightF)),
    cut(/*7*/"[a;](p(??)&q(??) -> q(??))".asFormula) <(
      /* show */ cohide(2) & useAt("PC2")(1, 1::0::Nil) & useAt("-> self")(1, 1::Nil) & V(1) & close,
      /* use */ cut(/*(8)*/"[a;](p(??)&q(??)->q(??)) -> ([a;](p(??)&q(??)) -> [a;]q(??))".asFormula) <(
        /* show */ cohide(2) & USubstPatternTactic(
          (SequentType(Sequent(Nil, IndexedSeq(), IndexedSeq("[a;](p(??)->q(??)) -> (([a;]p(??)) -> ([a;]q(??)))".asFormula))),
            (ru: RenUSubst) => ru.getRenamingTactic & ProofRuleTactics.axiomatic("K modal modus ponens", ru.substitution.usubst))::Nil),
//          uniformSubstT(
//            SubstitutionPair(PredOf(Function("p", None, Real, Bool), Anything), "p(??)&q(??)".asFormula) :: Nil,
//            Map(/*(8)*/"[a;](p(??)&q(??)->q(??)) -> ([a;](p(??)&q(??)) -> [a;]q(??))".asFormula -> /*K*/"[a;](p(??)->q(??)) -> (([a;]p(??)) -> ([a;]q(??)))".asFormula)
//          ) & AxiomTactic.axiomT("K modal modus ponens")),
        /* use */ modusPonens(AntePos(0), AntePos(1)) & close
      )
    )
  )
  lazy val boxSplitRightT = derivedAxiomT(boxSplitRight)

  /**
   * {{{Axiom "<:=> assign".
   *    <v:=t();>p(v) <-> p(t())
   * End.
   * }}}
   * @Derived
   */
  lazy val assigndF = "<v:=t();>p(v) <-> p(t())".asFormula
  lazy val assigndAxiom = derivedAxiom("<:=> assign",
    Sequent(Nil, IndexedSeq(), IndexedSeq(assigndF)),
    useAt("<> dual", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[:=] assign")(1, 0::0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val assigndT = derivedAxiomT(assigndAxiom)

  /**
   * {{{Axiom ":= assign dual".
   *    <v:=t();>p(v) <-> [v:=t();]p(v)
   * End.
   * }}}
   * @Derived
   */
  lazy val assignDualF = "<v:=t();>p(v) <-> [v:=t();]p(v)".asFormula
  lazy val assignDualAxiom = derivedAxiom(":= assign dual",
    Sequent(Nil, IndexedSeq(), IndexedSeq(assignDualF)),
    useAt("<:=> assign")(1, 0::Nil) &
      useAt("[:=] assign")(1, 1::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val assignDualT = derivedAxiomT(assignDualAxiom)

  /**
   * {{{Axiom "[:=] assign equational".
   *    [v:=t();]p(v) <-> \forall v (v=t() -> p(v))
   * End.
   * }}}
   * @Derived
   */
  lazy val assignbEquationalF = "[v:=t();]p(v) <-> \\forall v (v=t() -> p(v))".asFormula
  lazy val assignbEquationalAxiom = derivedAxiom("[:=] assign equational",
    Sequent(Nil, IndexedSeq(), IndexedSeq(assignbEquationalF)),
    useAt("[:=] assign")(1, 0::Nil) &
      commuteEquivR(1) &
      byUS(allSubstitute)
  )

  lazy val assignbEquationalT = derivedAxiomT(assignbEquationalAxiom)

  /**
   * {{{Axiom "[:=] assign update".
   *    [x:=t();]p(x) <-> [x:=t();]p(x)
   * End.
   * }}}
   * @Derived
   * @note Trivial reflexive stutter axiom, only used with a different recursor pattern in AxiomIndex.
   */
  lazy val assignbUpdateF = "[x:=t();]p(x) <-> [x:=t();]p(x)".asFormula
  lazy val assignbUpdate = derivedAxiom("[:=] assign update",
    Sequent(Nil, IndexedSeq(), IndexedSeq(assignbUpdateF)),
    byUS("<-> reflexive")
  )

  lazy val assignbUpdateT = derivedAxiomT(assignbUpdate)

  /**
   * {{{Axiom "<:=> assign update".
   *    <x:=t();>p(x) <-> <x:=t();>p(x)
   * End.
   * }}}
   * @Derived
   * @note Trivial reflexive stutter axiom, only used with a different recursor pattern in AxiomIndex.
   */
  lazy val assigndUpdateF = "<x:=t();>p(x) <-> <x:=t();>p(x)".asFormula
  lazy val assigndUpdate = derivedAxiom("<:=> assign update",
    Sequent(Nil, IndexedSeq(), IndexedSeq(assigndUpdateF)),
    byUS("<-> reflexive")
  )

  lazy val assigndUpdateT = derivedAxiomT(assigndUpdate)

  /**
   * {{{Axiom "[:=] vacuous assign".
   *    [v:=t();]p() <-> p()
   * End.
   * }}}
   * @Derived
   */
  lazy val vacuousAssignbF = "[v:=t();]p() <-> p()".asFormula
  lazy val vacuousAssignbAxiom = derivedAxiom("[:=] vacuous assign",
    Sequent(Nil, IndexedSeq(), IndexedSeq(vacuousAssignbF)),
    useAt("[:=] assign")(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val vacuousAssignbT = derivedAxiomT(vacuousAssignbAxiom)

  /**
   * {{{Axiom "<:=> vacuous assign".
   *    <v:=t();>p() <-> p()
   * End.
   * }}}
   * @Derived
   */
  lazy val vacuousAssigndF = "<v:=t();>p() <-> p()".asFormula
  lazy val vacuousAssigndAxiom = derivedAxiom("<:=> vacuous assign",
    Sequent(Nil, IndexedSeq(), IndexedSeq(vacuousAssigndF)),
    useAt("<> dual", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[:=] vacuous assign")(1, 0::0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val vacuousAssigndT = derivedAxiomT(vacuousAssigndAxiom)

  /**
   * {{{Axiom "<':=> differential assign".
   *    <v':=t();>p(v') <-> p(t())
   * End.
   * }}}
   * @Derived
   */
  lazy val assignDF = "<v':=t();>p(v') <-> p(t())".asFormula
  lazy val assignDAxiom = derivedAxiom("<':=> differential assign",
    Sequent(Nil, IndexedSeq(), IndexedSeq(assignDF)),
    useAt("<> dual", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[':=] differential assign")(1, 0::0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val assignDT = derivedAxiomT(assignDAxiom)

  /**
   * {{{Axiom "<:*> assign nondet".
   *    <x:=*;>p(x) <-> (\exists x p(x))
   * End.
   * }}}
   * @Derived
   */
  lazy val nondetassigndF = "<x:=*;>p(x) <-> (\\exists x p(x))".asFormula
  lazy val nondetassigndAxiom = derivedAxiom("<:*> assign nondet",
    Sequent(Nil, IndexedSeq(), IndexedSeq(nondetassigndF)),
    useAt("<> dual", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[:*] assign nondet")(1, 0::0::Nil) &
      useAt("all dual", PosInExpr(1::Nil))(1, 0::0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val nondetassigndT = derivedAxiomT(nondetassigndAxiom)

  /**
   * {{{Axiom "<?> test".
   *    <?H();>p() <-> (H() & p())
   * End.
   * }}}
   * @Derived
   */
  lazy val testdF = "<?H();>p() <-> (H() & p())".asFormula
  lazy val testdAxiom = derivedAxiom("<?> test",
    Sequent(Nil, IndexedSeq(), IndexedSeq(testdF)),
    useAt("<> dual", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[?] test")(1, 0::0::Nil) &
      prop
  )

  lazy val testdT = derivedAxiomT(testdAxiom)

  /**
   * {{{Axiom "<++> choice".
   *    <a;++b;>p(??) <-> (<a;>p(??) | <b;>p(??))
   * End.
   * }}}
   * @todo first show de Morgan
   */
  lazy val choicedF = "<a;++b;>p(??) <-> (<a;>p(??) | <b;>p(??))".asFormula
  lazy val choicedAxiom = derivedAxiom("<++> choice",
    Sequent(Nil, IndexedSeq(), IndexedSeq(choicedF)),
    useAt("<> dual", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[++] choice")(1, 0::0::Nil) &
      useAt("<> dual", PosInExpr(1::Nil))(1, 1::0::Nil) &
      useAt("<> dual", PosInExpr(1::Nil))(1, 1::1::Nil) &
      prop
  )

  lazy val choicedT = derivedAxiomT(choicedAxiom)

  /**
   * {{{Axiom "<;> compose".
   *    <a;b;>p(??) <-> <a;><b;>p(??)
   * End.
   * }}}
   * @Derived
   */
  lazy val composedF = "<a;b;>p(??) <-> <a;><b;>p(??)".asFormula
  lazy val composedAxiom = derivedAxiom("<;> compose",
    Sequent(Nil, IndexedSeq(), IndexedSeq(composedF)),
    useAt("<> dual", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[;] compose")(1, 0::0::Nil) &
      useAt("<> dual", PosInExpr(1::Nil))(1, 1::1::Nil) &
      useAt("<> dual", PosInExpr(1::Nil))(1, 1::Nil) &
      useAt(doubleNegationAxiom)(1, 1::0::1::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val composedT = derivedAxiomT(composedAxiom)

  /**
   * {{{Axiom "<*> iterate".
   *    <{a;}*>p(??) <-> (p(??) | <a;><{a;}*> p(??))
   * End.
   * }}}
   * @Derived
   */
  lazy val iteratedF = "<{a;}*>p(??) <-> (p(??) | <a;><{a;}*> p(??))".asFormula
  lazy val iteratedAxiom = derivedAxiom("<*> iterate",
    Sequent(Nil, IndexedSeq(), IndexedSeq(iteratedF)),
    useAt("<> dual", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("[*] iterate")(1, 0::0::Nil) &
      useAt("<> dual", PosInExpr(1::Nil))(1, 1::1::1::Nil) &
      useAt("<> dual", PosInExpr(1::Nil))(1, 1::1::Nil) &
      HilbertCalculus.stepAt(1, 0::Nil) &
      useAt(doubleNegationAxiom)(1, 1::1::0::1::Nil) &
      prop
  )

  lazy val iteratedT = derivedAxiomT(iteratedAxiom)

  /**
   * {{{Axiom "<*> approx".
   *    <a;>p(??) -> <{a;}*>p(??)
   * End.
   * }}}
   * @Derived
   */
  lazy val loopApproxdF = "<a;>p(??) -> <{a;}*>p(??)".asFormula
  lazy val loopApproxd = derivedAxiom("<*> approx",
    Sequent(Nil, IndexedSeq(), IndexedSeq(loopApproxdF)),
    useAt("<*> iterate")(1, 1::Nil) &
      useAt("<*> iterate")(1, 1::1::1::Nil) &
      cut("<a;>p(??) -> <a;>(p(??) | <a;><{a;}*>p(??))".asFormula) <(
      /* show */ hideR(1) & ls(implyR) & mond & prop,
      /* use */ prop
    )
  )

  lazy val loopApproxdT = derivedAxiomT(loopApproxd)


  /**
   * {{{Axiom "[*] approx".
   *    [{a;}*]p(??) -> [a;]p(??)
   * End.
   * }}}
   * @Derived
   */
  lazy val loopApproxbF = "[{a;}*]p(??) -> [a;]p(??)".asFormula
  lazy val loopApproxb = derivedAxiom("[*] approx",
    Sequent(Nil, IndexedSeq(), IndexedSeq(loopApproxbF)),
    useAt("[*] iterate")(1, 0::Nil) &
      useAt("[*] iterate")(1, 0::1::1::Nil) &
      cut("[a;](p(??) & [a;][{a;}*]p(??)) -> [a;]p(??)".asFormula) <(
      /* show */ hideR(1) & ls(implyR) & monb & prop,
      /* use */ prop
    )
  )
  lazy val loopApproxbT = derivedAxiomT(loopApproxb)

  //@todo this is somewhat indirect. Maybe it'd be better to represent derived axioms merely as Lemma and auto-wrap them within their ApplyRule[LookupLemma] tactics on demand.
  //private def useAt(lem: ApplyRule[LookupLemma]): PositionTactic = TactixLibrary.useAt(lem.rule.lemma.fact)

  /**
   * {{{Axiom "exists generalize".
   *    p(t()) -> (\exists x p(x))
   * End.
   * }}}
   * @Derived
   */
  lazy val existsGeneralizeF = "p(t()) -> (\\exists x p(x))".asFormula
  lazy val existsGeneralize = derivedAxiom("exists generalize",
    Sequent(Nil, IndexedSeq(), IndexedSeq(existsGeneralizeF)),
    useAt("exists dual", PosInExpr(1::Nil))(1, 1::Nil) &
      implyR(SuccPos(0)) &
      notR(SuccPos(0)) &
      useAt("all instantiate", PosInExpr(0::Nil))(-2) &
      prop
  )
  lazy val existsGeneralizeT = derivedAxiomT(existsGeneralize)

  /**
   * {{{Axiom "exists eliminate".
   *    p(??) -> (\exists x p(??))
   * End.
   * }}}
   * @Derived
   * @todo prove
   */
  lazy val existsEliminateF = "p(??) -> (\\exists x p(??))".asFormula
  lazy val existsEliminate = derivedAxiom("exists eliminate",
    Sequent(Nil, IndexedSeq(), IndexedSeq(existsEliminateF)),
    useAt("exists dual", PosInExpr(1::Nil))(1, 1::Nil) &
      implyR(1) &
      notR(1) &
      useAt("all eliminate", PosInExpr(0::Nil))(-2) &
      prop
  )

  lazy val existsEliminateT = derivedAxiomT(existsEliminate)

  /**
   * {{{Axiom "all substitute".
   *    (\forall x (x=t() -> p(x))) <-> p(t())
   * End.
   * }}}
   * @Derived
   */
  lazy val allSubstituteF = "(\\forall x (x=t() -> p(x))) <-> p(t())".asFormula
  lazy val allSubstitute = derivedAxiom("all substitute",
    Sequent(Nil, IndexedSeq(), IndexedSeq(allSubstituteF)),
    equivR(SuccPos(0)) <(
      //@todo check if unifications still fail -> may need to use original implementation
      //@note unifications fail here -> proved from sequent calculus
      /* equiv left */ allL(Variable("x"), "t()".asTerm)(-1) & implyL(-1) <(cohide(2) & byUS("= reflexive"), close),
      /* equiv right */ allR(1) & byUS("const formula congruence")
    )
  )
  lazy val allSubstituteT = derivedAxiomT(allSubstitute)


  /**
   * {{{Axiom "vacuous exists quantifier".
   *    (\exists x p()) <-> p()
   * End.
   * }}}
   * @Derived
   */
  lazy val vacuousExistsF = "(\\exists x p()) <-> p()".asFormula
  lazy val vacuousExistsAxiom = derivedAxiom("vacuous exists quantifier",
    Sequent(Nil, IndexedSeq(), IndexedSeq(vacuousExistsF)),
    useAt(existsDualAxiom, PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("vacuous all quantifier")(1, 0::0::Nil) &
      useAt(doubleNegationAxiom)(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val vacuousExistsT = derivedAxiomT(vacuousExistsAxiom)

  /**
   * {{{Axiom "V[:*] vacuous assign nondet".
   *    [x:=*;]p() <-> p()
   * End.
   * @todo reorient
   * @Derived
   */
  lazy val vacuousBoxAssignNondetF = "([x:=*;]p()) <-> p()".asFormula
  lazy val vacuousBoxAssignNondetAxiom = derivedAxiom("V[:*] vacuous assign nondet",
    Sequent(Nil, IndexedSeq(), IndexedSeq(vacuousBoxAssignNondetF)),
    useAt("[:*] assign nondet")(1, 0::Nil) &
      useAt("vacuous all quantifier")(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val vacuousBoxAssignNondetT = derivedAxiomT(vacuousBoxAssignNondetAxiom)

  /**
   * {{{Axiom "V<:*> vacuous assign nondet".
   *    <x:=*;>p() <-> p()
   * End.
   * }}}
   * @todo reorient
   * @Derived
   */
  lazy val vacuousDiamondAssignNondetF = "(<x:=*;>p()) <-> p()".asFormula
  lazy val vacuousDiamondAssignNondetAxiom = derivedAxiom("V<:*> vacuous assign nondet",
    Sequent(Nil, IndexedSeq(), IndexedSeq(vacuousDiamondAssignNondetF)),
    useAt("<:*> assign nondet")(1, 0::Nil) &
      useAt(vacuousExistsAxiom)(1, 0::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val vacuousDiamondAssignNondetT = derivedAxiomT(vacuousDiamondAssignNondetAxiom)


  /**
   * {{{Axiom "Domain Constraint Conjunction Reordering".
   *    [{c & (H(??) & q(??))}]p(??) <-> [{c & (q(??) & H(??))}]p(??)
   * End.
   * }}}
   * @Derived
   */
  lazy val domainCommuteF = "[{c & (H(??) & q(??))}]p(??) <-> [{c & (q(??) & H(??))}]p(??)".asFormula
  lazy val domainCommute = derivedAxiom("Domain Constraint Conjunction Reordering",
    Sequent(Nil, IndexedSeq(), IndexedSeq(domainCommuteF)),
    useAt(andCommute)(1, 0::0::1::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val domainCommuteT = derivedAxiomT(domainCommute)

  /**
   * {{{Axiom "[] post weaken".
   *   [a;]p(??)  ->  [a;](q(??)->p(??))
   * End.
   * }}}
   * @Derived from M (or also from K)
   */
  lazy val postconditionWeakenF = "([a;]p(??))  ->  [a;](q(??)->p(??))".asFormula
  lazy val postconditionWeaken = derivedAxiom("[] post weaken",
    Sequent(Nil, IndexedSeq(), IndexedSeq(postconditionWeakenF)),
    implyR(1) & monb & prop
  )
  lazy val postconditionWeakenT = derivedAxiomT(postconditionWeaken)


  /**
   * {{{Axiom "& commute".
   *    (p() & q()) <-> (q() & p())
   * End.
   * }}}
   * @Derived
   */
  lazy val andCommuteF = "(p() & q()) <-> (q() & p())".asFormula
  lazy val andCommute = derivedAxiom("& commute", Sequent(Nil, IndexedSeq(), IndexedSeq(andCommuteF)), prop)
  lazy val andCommuteT = derivedAxiomT(andCommute)

  /**
   * {{{Axiom "& associative".
   *    ((p() & q()) & r()) <-> (p() & (q() & r()))
   * End.
   * }}}
   * @Derived
   */
  lazy val andAssocF = "((p() & q()) & r()) <-> (p() & (q() & r()))".asFormula
  lazy val andAssoc = derivedAxiom("& associative", Sequent(Nil, IndexedSeq(), IndexedSeq(andAssocF)), prop)
  lazy val andAssocT = derivedAxiomT(andAssoc)

  /**
   * {{{Axiom "& reflexive".
   *    p() & p() <-> p()
   * End.
   * }}}
   */
  lazy val andReflexiveF = "p() & p() <-> p()".asFormula
  lazy val andReflexive = derivedAxiom("& reflexive", Sequent(Nil, IndexedSeq(), IndexedSeq(andReflexiveF)), prop)
  lazy val andReflexiveT = derivedAxiomT(andReflexive)

  /**
   * {{{Axiom "-> self".
   *    (p() -> p()) <-> true
   * End.
   * }}}
   */
  lazy val implySelfF = "(p() -> p()) <-> true".asFormula
  lazy val implySelf = derivedAxiom("-> self", Sequent(Nil, IndexedSeq(), IndexedSeq(implySelfF)), prop)
  lazy val implySelfT = derivedAxiomT(implySelf)

  /**
   * {{{Axiom "!& deMorgan".
   *    (!(p() & q())) <-> ((!p()) | (!q()))
   * End.
   * }}}
   * @Derived
   */
  lazy val notAndF = "(!(p() & q())) <-> ((!p()) | (!q()))".asFormula
  lazy val notAnd = derivedAxiom("!& deMorgan", Sequent(Nil, IndexedSeq(), IndexedSeq(notAndF)), prop)
  lazy val notAndT = derivedAxiomT(notAnd)

  /**
   * {{{Axiom "!| deMorgan".
   *    (!(p() | q())) <-> ((!p()) & (!q()))
   * End.
   * }}}
   * @Derived
   */
  lazy val notOrF = "(!(p() | q())) <-> ((!p()) & (!q()))".asFormula
  lazy val notOr = derivedAxiom("!| deMorgan", Sequent(Nil, IndexedSeq(), IndexedSeq(notOrF)), prop)
  lazy val notOrT = derivedAxiomT(notOr)

  /**
   * {{{Axiom "!-> deMorgan".
   *    (!(p() -> q())) <-> ((p()) & (!q()))
   * End.
   * }}}
   * @Derived
   */
  lazy val notImplyF = "(!(p() -> q())) <-> ((p()) & (!q()))".asFormula
  lazy val notImply = derivedAxiom("!-> deMorgan", Sequent(Nil, IndexedSeq(), IndexedSeq(notImplyF)), prop)
  lazy val notImplyT = derivedAxiomT(notImply)

  /**
   * {{{Axiom "!<-> deMorgan".
   *    (!(p() <-> q())) <-> (((p()) & (!q())) | ((!p()) & (q())))
   * End.
   * }}}
   * @Derived
   */
  lazy val notEquivF = "(!(p() <-> q())) <-> (((p()) & (!q())) | ((!p()) & (q())))".asFormula
  lazy val notEquiv = derivedAxiom("!<-> deMorgan", Sequent(Nil, IndexedSeq(), IndexedSeq(notEquivF)), prop)
  lazy val notEquivT = derivedAxiomT(notEquiv)

  /**
   * {{{Axiom "-> expand".
   *    (p() -> q()) <-> ((!p()) | q())
   * End.
   * }}}
   * @Derived
   */
  lazy val implyExpandF = "(p() -> q()) <-> ((!p()) | q())".asFormula
  lazy val implyExpand = derivedAxiom("-> expand", Sequent(Nil, IndexedSeq(), IndexedSeq(implyExpandF)), prop)
  lazy val implyExpandT = derivedAxiomT(implyExpand)


  /**
   * {{{Axiom "PC1".
   *    p()&q() -> p()
   * End.
   * }}}
   * @Derived
   * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, PC1
   */
  lazy val PC1F = "p()&q() -> p()".asFormula
  lazy val PC1 = derivedAxiom("PC1", Sequent(Nil, IndexedSeq(), IndexedSeq(PC1F)), prop)
  lazy val PC1T = derivedAxiomT(PC1)

  /**
   * {{{Axiom "PC2".
   *    p()&q() -> q()
   * End.
   * }}}
   * @Derived
   * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, PC2
   */
  lazy val PC2F = "p()&q() -> q()".asFormula
  lazy val PC2 = derivedAxiom("PC2", Sequent(Nil, IndexedSeq(), IndexedSeq(PC2F)), prop)
  lazy val PC2T = derivedAxiomT(PC2)

  /**
   * {{{Axiom "PC3".
   *    p()&q() -> ((p()->r())->(p()->q()&r())) <-> true
   * End.
   * }}}
   * @Derived
   * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, PC3
   */
  lazy val PC3F = "p()&q() -> ((p()->r())->(p()->q()&r())) <-> true".asFormula
  lazy val PC3 = derivedAxiom("PC3", Sequent(Nil, IndexedSeq(), IndexedSeq(PC3F)), prop)
  lazy val PC3T = derivedAxiomT(PC3)

  /**
   * {{{Axiom "PC9".
   *    p() -> p() | q()
   * End.
   * }}}
   * @Derived
   * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, PC9
   */
  lazy val PC9F = "p() -> p() | q()".asFormula
  lazy val PC9 = derivedAxiom("PC9", Sequent(Nil, IndexedSeq(), IndexedSeq(PC9F)), prop)
  lazy val PC9T = derivedAxiomT(PC9)

  /**
   * {{{Axiom "PC10".
   *    q() -> p() | q()
   * End.
   * }}}
   * @Derived
   * @Note implements Cresswell, Hughes. A New Introduction to Modal Logic, PC10
   */
  lazy val PC10F = "q() -> p() | q()".asFormula
  lazy val PC10 = derivedAxiom("PC10", Sequent(Nil, IndexedSeq(), IndexedSeq(PC10F)), prop)
  lazy val PC10T = derivedAxiomT(PC10)

  /**
   * {{{Axiom "-> tautology".
   *    (p() -> (q() -> p()&q())) <-> true
   * End.
   * }}}
   * @Derived
   */
  lazy val implyTautologyF = "(p() -> (q() -> p()&q())) <-> true".asFormula
  lazy val implyTautology = derivedAxiom("-> tautology", Sequent(Nil, IndexedSeq(), IndexedSeq(implyTautologyF)), prop)
  lazy val implyTautologyT = derivedAxiomT(implyTautology)

  /**
   * {{{Axiom "->' derive imply".
   *    (p(??) -> q(??))' <-> (!p(??) | q(??))'
   * End.
   * }}}
   * @Derived by CE
   */
  lazy val DimplyF = "(p(??) -> q(??))' <-> (!p(??) | q(??))'".asFormula
  lazy val Dimply = derivedAxiom("->' derive imply",
    Sequent(Nil, IndexedSeq(), IndexedSeq(DimplyF)),
    useAt(implyExpand)(1, 0::0::Nil) &
      byUS(equivReflexiveAxiom)
  )
  lazy val DimplyT = derivedAxiomT(Dimply)

  /**
   * {{{Axiom "\forall->\exists".
   *    (\forall x p(x)) -> (\exists x p(x))
   * End.
   * }}}
   * @Derived
   */
  lazy val forallThenExistsF = "(\\forall x p(x)) -> (\\exists x p(x))".asFormula
  lazy val forallThenExistsAxiom = derivedAxiom("\\forall->\\exists",
    Sequent(Nil, IndexedSeq(), IndexedSeq(forallThenExistsF)),
    implyR(SuccPosition(0)) &
      useAt("exists generalize", PosInExpr(1::Nil))(1) &
      useAt("all instantiate")(-1) &
      closeId
  )
  lazy val forallThenExistsT = derivedAxiomT(forallThenExistsAxiom)

  /**
   * {{{Axiom "->true".
   *    (p()->true) <-> true
   * End.
   * }}}
   * @Derived
   */
  lazy val impliesTrueF = "(p()->true) <-> true".asFormula
  lazy val impliesTrue = derivedAxiom("->true", Sequent(Nil, IndexedSeq(), IndexedSeq(impliesTrueF)), prop)
  lazy val impliesTrueT = derivedAxiomT(impliesTrue)

  /**
   * {{{Axiom "true->".
   *    (true->p()) <-> p()
   * End.
   * }}}
   * @Derived
   */
  lazy val trueImpliesF = "(true->p()) <-> p()".asFormula
  lazy val trueImplies = derivedAxiom("true->", Sequent(Nil, IndexedSeq(), IndexedSeq(trueImpliesF)), prop)
  lazy val trueImpliesT = derivedAxiomT(trueImplies)

  /**
   * {{{Axiom "&true".
   *    (p()&true) <-> p()
   * End.
   * }}}
   * @Derived
   */
  lazy val andTrueF = "(p()&true) <-> p()".asFormula
  lazy val andTrue = derivedAxiom("&true", Sequent(Nil, IndexedSeq(), IndexedSeq(andTrueF)), prop)
  lazy val andTrueT = derivedAxiomT(andTrue)

  /**
   * {{{Axiom "true&".
   *    (true&p()) <-> p()
   * End.
   * }}}
   * @Derived
   */
  lazy val trueAndF = "(true&p()) <-> p()".asFormula
  lazy val trueAnd = derivedAxiom("true&", Sequent(Nil, IndexedSeq(), IndexedSeq(trueAndF)), prop)
  lazy val trueAndT = derivedAxiomT(trueAnd)

  /**
   * {{{Axiom "0*".
   *    (0*f()) = 0
   * End.
   * }}}
   * @Derived
   */
  lazy val zeroTimesF = "(0*f()) = 0".asFormula
  lazy val zeroTimes = derivedAxiom("0*", Sequent(Nil, IndexedSeq(), IndexedSeq(zeroTimesF)), QE)
  lazy val zeroTimesT = derivedAxiomT(zeroTimes)

  /**
   * {{{Axiom "0+".
   *    (0+f()) = f()
   * End.
   * }}}
   * @Derived
   */
  lazy val zeroPlusF = "(0+f()) = f()".asFormula
  lazy val zeroPlus = derivedAxiom("0+", Sequent(Nil, IndexedSeq(), IndexedSeq(zeroPlusF)), QE)
  lazy val zeroPlusT = derivedAxiomT(zeroPlus)


  // differential equations

  /**
   * {{{Axiom "DW differential weakening".
   *    [{c&H(??)}]p(??) <-> ([{c&H(??)}](H(??)->p(??)))
   *    /* [x'=f(x)&q(x);]p(x) <-> ([x'=f(x)&q(x);](q(x)->p(x))) THEORY */
   * End.
   * }}}
   * @see footnote 3 in "Andre Platzer. A uniform substitution calculus for differential dynamic logic. In Amy P. Felty and Aart Middeldorp, editors, International Conference on Automated Deduction, CADE'15, Berlin, Germany, Proceedings, volume 9195 of LNCS, pages 467-481. Springer, 2015. arXiv 1503.01981, 2015."
   */
  lazy val DWeakeningF = "[{c&H(??)}]p(??) <-> ([{c&H(??)}](H(??)->p(??)))".asFormula
  lazy val DWeakening = derivedAxiom("DW differential weakening",
    Sequent(Nil, IndexedSeq(), IndexedSeq(DWeakeningF)),
    equivR(1) <(
      /* equiv left */
      cut("[{c&H(??)}](p(??)->(H(??)->p(??)))".asFormula) <(
        /* show */ cohide(2) & G & prop,
        /* use */ useAt("K modal modus ponens", PosInExpr(0::Nil))(-2) & implyL(-2) <(close, close)
      ),
      /* equiv right */
      useAt("K modal modus ponens", PosInExpr(0::Nil))(-1) & implyL(-1) <(cohide(2) & byUS("DW"), close)
    )
  )
  lazy val DWeakeningT = derivedAxiomT(DWeakening)

  /**
   * {{{Axiom "DX diamond differential skip".
   *    <{c&H(??)}>p(??) <- H(??)&p(??)
   * End.
   * }}}
   * @Derived
   */
  lazy val DskipdF = "<{c&H(??)}>p(??) <- H(??)&p(??)".asFormula
  lazy val Dskipd = derivedAxiom("DX diamond differential skip",
    Sequent(Nil, IndexedSeq(), IndexedSeq(DskipdF)),
    useAt("!! double negation", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("!& deMorgan")(1, 0::0::Nil) &
      useAt("-> expand", PosInExpr(1::Nil))(1, 0::0::Nil) &
      useAt("DX differential skip")(1, 0::0::Nil) &
//      ODETactics.diffSkipT(DifferentialProgramConst("c"))(1, 0::0::Nil) &
      useAt("<> dual")(1, 0::Nil) & implyR(1) & close
  )
  lazy val DskipdT = derivedAxiomT(Dskipd)

  /**
   * {{{Axiom "DS differential equation solution".
   *    [{x'=c()}]p(x) <-> \forall t (t>=0 -> [x:=x+(c()*t);]p(x))
   * End.
   * }}}
   * @Derived
   */
  lazy val DSnodomainF = "[{x'=c()}]p(x) <-> \\forall t (t>=0 -> [x:=x+(c()*t);]p(x))".asFormula
  lazy val DSnodomain = derivedAxiom("DS differential equation solution",
    Sequent(Nil, IndexedSeq(), IndexedSeq(DSnodomainF)),
    useAt("DS& differential equation solution")(1, 0::Nil) &
      useAt(impliesTrue)(1, 0::0::1::0::0::Nil) &
      useAt("vacuous all quantifier")(1, 0::0::1::0::Nil) &
      useAt(trueImplies)(1, 0::0::1::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val DSnodomainT = derivedAxiomT(DSnodomain)

  /**
   * {{{Axiom "Dsol differential equation solution".
   *    <{x'=c()}>p(x) <-> \exists t (t>=0 & <x:=x+(c()*t);>p(x))
   * End.
   * }}}
   * @Derived
   */
  lazy val DSdnodomainF = "<{x'=c()}>p(x) <-> \\exists t (t>=0 & <x:=x+(c()*t);>p(x))".asFormula
  lazy val DSdnodomain = derivedAxiom("Dsol differential equation solution",
    Sequent(Nil, IndexedSeq(), IndexedSeq(DSdnodomainF)),
    useAt("Dsol& differential equation solution")(1, 0::Nil) &
      useAt(impliesTrue)(1, 0::0::1::0::0::Nil) &
      useAt("vacuous all quantifier")(1, 0::0::1::0::Nil) &
      useAt(trueAnd)(1, 0::0::1::Nil) &
      byUS(equivReflexiveAxiom)
  )

  lazy val DSdnodomainT = derivedAxiomT(DSdnodomain)

  /**
   * {{{Axiom "Dsol& differential equation solution".
   *    <{x'=c()&q(x)}>p(x) <-> \exists t (t>=0 & ((\forall s ((0<=s&s<=t) -> q(x+(c()*s)))) & <x:=x+(c()*t);>p(x)))
   * End.
   * }}}
   */
  lazy val DSddomainF = "<{x'=c()&q(x)}>p(x) <-> \\exists t (t>=0 & ((\\forall s ((0<=s&s<=t) -> q(x+(c()*s)))) & <x:=x+(c()*t);>p(x)))".asFormula
  lazy val DSddomain = derivedAxiom("Dsol& differential equation solution",
    Sequent(Nil, IndexedSeq(), IndexedSeq(DSddomainF)),
    useAt("<> dual", PosInExpr(1::Nil))(1, 0::Nil) &
      useAt("DS& differential equation solution")(1, 0::0::Nil) &
      useAt("!all")(1, 0::Nil) & //step(1, 0::Nil) &
      useAt("!-> deMorgan")(1, 0::0::Nil) &
      useAt("!-> deMorgan")(1, 0::0::1::Nil) &
      useAt("<> dual")(1, 0::0::1::1::Nil) &
      //useAt("& associative", PosInExpr(1::Nil))(1, 0::0::Nil) &
      byUS("<-> reflexive")
  )

  lazy val DSddomainT = derivedAxiomT(DSddomain)

  //  lazy val existsDualAxiom: LookupLemma = derivedAxiom("exists dual",
  //    Provable.startProof(Sequent(Nil, IndexedSeq(), IndexedSeq("\\exists x q(x) <-> !(\\forall x (!q(x)))".asFormula)))
  //      (CutRight("\\exists x q(x) <-> !!(\\exists x (!!q(x)))".asFormula, SuccPos(0)), 0)
  //      // right branch
  //      (EquivifyRight(SuccPos(0)), 1)
  //      (AxiomaticRule("CE congruence", USubst(
  //        SubstitutionPair(PredicationalOf(context, DotFormula), "\\exists x q(x) <-> !⎵".asFormula) ::
  //          SubstitutionPair(pany, "!\\exists x !!q(x)".asFormula) ::
  //          SubstitutionPair(qany, "\\forall x !q(x)".asFormula) :: Nil
  //      )), 1)
  //      (CommuteEquivRight(SuccPos(0)), 1)
  //      (Axiom("all dual"), 1)
  //      (Close(AntePos(0),SuccPos(0)), 1)
  //  )


  /**
   * {{{Axiom "DG differential pre-ghost".
   *    [{c&H(??)}]p(??) <-> \exists y [{y'=(t()*y)+s(),c&H(??)}]p(??)
   *    // [x'=f(x)&q(x);]p(x) <-> \exists y [{y'=(a(x)*y)+b(x), x'=f(x))&q(x)}]p(x) THEORY
   * End.
   * }}}
   * Pre Differential Auxiliary / Differential Ghost -- not strictly necessary but saves a lot of reordering work.
   */
  lazy val DGpreghostF = "[{c&H(??)}]p(??) <-> \\exists y [{y'=(t()*y)+s(),c&H(??)}]p(??)".asFormula
  lazy val DGpreghost = derivedAxiom("DG differential pre-ghost",
    Sequent(Nil, IndexedSeq(), IndexedSeq(DGpreghostF)),
    useAt("DG differential ghost")(1, 0::Nil) &
      useAt(", commute")(1, 0::0::Nil) &
      byUS(equivReflexiveAxiom)
  )
  lazy val DGpreghostT = derivedAxiomT(DGpreghost)

  /**
   * {{{Axiom "x' derive variable".
   *    \forall x_ ((x_)' = x_')
   * End.
   * }}}
   */
  lazy val DvariableF = "\\forall x_ ((x_)' = x_')".asFormula
  lazy val Dvariable = derivedAxiom("x' derive variable",
    Provable.startProof(Sequent(Nil, IndexedSeq(), IndexedSeq(DvariableF)))
    (Skolemize(SuccPos(0)), 0)
    (Axiom.axiom("x' derive var"), 0)
  )
  lazy val DvariableT = derivedAxiomT(Dvariable)
  //  /**
  //   * {{{Axiom "x' derive var".
  //   *    (x_)' = x_'
  //   * End.
  //   * }}}
  //   * @todo derive
  //   */
  //  lazy val DvarF = "((x_)' = x_')".asFormula
  //  lazy val Dvar = derivedAxiom("'x derive var",
  //    Provable.startProof(Sequent(Nil, IndexedSeq(), IndexedSeq(DvarF)))
  //      (CutRight("\\forall x_ ((x_)' = x_')".asFormula, SuccPos(0)), 0)
  //      // right branch
  //      (UniformSubstitutionRule.UniformSubstitutionRuleForward(Axiom.axiom("all eliminate"),
  //        USubst(SubstitutionPair(PredOf(Function("p_",None,Real,Bool),Anything), DvarF)::Nil)), 0)
  //      // left branch
  //      (Axiom.axiom("x' derive variable"), 0)
  //    /*TacticLibrary.instantiateQuanT(Variable("x_",None,Real), Variable("x",None,Real))(1) &
  //      byUS("= reflexive")*/
  //  )
  //  lazy val DvarT = derivedAxiomT(Dvar)
  /**
   * {{{Axiom "' linear".
   *    (c()*f(??))' = c()*(f(??))'
   * End.
   * }}}
   */
  lazy val DlinearF = "(c()*f(??))' = c()*(f(??))'".asFormula
  lazy val Dlinear = derivedAxiom("' linear",
    Sequent(Nil, IndexedSeq(), IndexedSeq(DlinearF)),
    useAt("*' derive product")(1, 0::Nil) &
      useAt("c()' derive constant fn")(1, 0::0::0::Nil) &
      useAt(zeroTimes)(1, 0::0::Nil) &
      useAt(zeroPlus)(1, 0::Nil) &
      byUS("= reflexive")
  )
  lazy val DlinearT = derivedAxiomT(Dlinear)
  /**
   * {{{Axiom "' linear right".
   *    (f(??)*c())' = f(??)'*c()
   * End.
   * }}}
   */
  lazy val DlinearRightF = "(f(??)*c())' = (f(??))'*c()".asFormula
  lazy val DlinearRight = derivedAxiom("' linear right",
    Sequent(Nil, IndexedSeq(), IndexedSeq(DlinearRightF)),
    useAt("* commute")(1, 0::0::Nil) &
      useAt("* commute")(1, 1::Nil) &
      by(Dlinear)
  )
  lazy val DlinearRightT = derivedAxiomT(DlinearRight)

  // real arithmetic

  /**
   * {{{Axiom "= reflexive".
   *    s() = s()
   * End.
   * }}}
   */
  lazy val equalReflexiveF = "s() = s()".asFormula
  lazy val equalReflex = derivedAxiom("= reflexive", Sequent(Nil, IndexedSeq(), IndexedSeq(equalReflexiveF)), QE)
  lazy val equalReflexiveT = derivedAxiomT(equalReflex)

  /**
   * {{{Axiom "= commute".
   *   (f()=g()) <-> (g()=f())
   * End.
   * }}}
   */
  lazy val equalCommuteF = "(f()=g()) <-> (g()=f())".asFormula
  lazy val equalCommute = derivedAxiom("= commute", Sequent(Nil, IndexedSeq(), IndexedSeq(equalCommuteF)), QE)
  lazy val equalCommuteT = derivedAxiomT(equalCommute)

  /**
   * {{{Axiom "* commute".
   *   (f()*g()) = (g()*f())
   * End.
   * }}}
   */
  lazy val timesCommuteF = "(f()*g()) = (g()*f())".asFormula
  lazy val timesCommute = derivedAxiom("* commute", Sequent(Nil, IndexedSeq(), IndexedSeq(timesCommuteF)), QE)
  lazy val timesCommuteT = derivedAxiomT(timesCommute)

  /**
   * {{{Axiom "<=".
   *   (f()<=g()) <-> ((f()<g()) | (f()=g()))
   * End.
   * }}}
   */
  lazy val lessEqualF = "(f()<=g()) <-> ((f()<g()) | (f()=g()))".asFormula
  lazy val lessEqual = derivedAxiom("<=", Sequent(Nil, IndexedSeq(), IndexedSeq(lessEqualF)), QE)
  lazy val lessEqualT = derivedAxiomT(lessEqual)

  /**
   * {{{Axiom "= negate".
   *   (!(f() != g())) <-> (f() = g())
   * End.
   * }}}
   */
  lazy val notNotEqualF = "(!(f() != g())) <-> (f() = g())".asFormula
  lazy val notNotEqual = derivedAxiom("= negate", Sequent(Nil, IndexedSeq(), IndexedSeq(notNotEqualF)), QE)
  lazy val notNotEqualT = derivedAxiomT(notNotEqual)

  /**
   * {{{Axiom "! =".
   *   !(f() = g()) <-> f() != g()
   * End.
   * }}}
   */
  lazy val notEqualF = "(!(f() = g())) <-> (f() != g())".asFormula
  lazy val notEqual = derivedAxiom("!= negate", Sequent(Nil, IndexedSeq(), IndexedSeq(notEqualF)), QE)
  lazy val notEqualT = derivedAxiomT(notEqual)

  /**
   * {{{Axiom "! >".
   *   (!(f() > g())) <-> (f() <= g())
   * End.
   * }}}
   */
  lazy val notGreaterF = "(!(f() > g())) <-> (f() <= g())".asFormula
  lazy val notGreater = derivedAxiom("! >", Sequent(Nil, IndexedSeq(), IndexedSeq(notGreaterF)), QE)
  lazy val notGreaterT = derivedAxiomT(notGreater)

  /**
   * {{{Axiom "! <".
   *   (!(f() < g())) <-> (f() >= g())
   * End.
   * }}}
   * @todo derive more efficiently via flip
   */
  lazy val notLessF = "(!(f() < g())) <-> (f() >= g())".asFormula
  lazy val notLess = derivedAxiom("! <", Sequent(Nil, IndexedSeq(), IndexedSeq(notLessF)), QE)
  lazy val notLessT = derivedAxiomT(notLess)

  /**
   * {{{Axiom "! <=".
   *   (!(f() <= g())) <-> (f() > g())
   * End.
   * }}}
   * @todo derive more efficiently via flip
   */
  lazy val notLessEqualF = "(!(f() <= g())) <-> (f() > g())".asFormula
  lazy val notLessEqual = derivedAxiom("! <=", Sequent(Nil, IndexedSeq(), IndexedSeq(notLessEqualF)), QE)
  lazy val notLessEqualT = derivedAxiomT(notLessEqual)

  /**
   * {{{Axiom "< negate".
   *   (!(f() >= g())) <-> (f() < g())
   * End.
   * }}}
   */
  lazy val notGreaterEqualF = "(!(f() >= g())) <-> (f() < g())".asFormula
  lazy val notGreaterEqual = derivedAxiom("< negate", Sequent(Nil, IndexedSeq(), IndexedSeq(notGreaterEqualF)), QE)
  lazy val notGreaterEqualT = derivedAxiomT(notGreaterEqual)

  /**
   * {{{Axiom ">= flip".
   *   (f() >= g()) <-> (g() <= f())
   * End.
   * }}}
   */
  lazy val flipGreaterEqualF = "(f() >= g()) <-> (g() <= f())".asFormula
  lazy val flipGreaterEqual = derivedAxiom(">= flip", Sequent(Nil, IndexedSeq(), IndexedSeq(flipGreaterEqualF)), QE)
  lazy val flipGreaterEqualT = derivedAxiomT(flipGreaterEqual)

  /**
   * {{{Axiom "> flip".
   *   (f() > g()) <-> (g() < f())
   * End.
  */
  lazy val flipGreaterF = "(f() > g()) <-> (g() < f())".asFormula
  lazy val flipGreater = derivedAxiom("> flip", Sequent(Nil, IndexedSeq(), IndexedSeq(flipGreaterF)), QE)
  lazy val flipGreaterT = derivedAxiomT(flipGreater)

  /**
   * {{{Axiom "+ associative".
   *    (f()+g()) + h() = f() + (g()+h())
   * End.
   * }}}
   */
  lazy val plusAssociativeF = "(f() + g()) + h() = f() + (g() + h())".asFormula
  lazy val plusAssociative = derivedAxiom("+ associative", Sequent(Nil, IndexedSeq(), IndexedSeq(plusAssociativeF)), QE)
  lazy val plusAssociativeT = derivedAxiomT(plusAssociative)

  /**
   * {{{Axiom "* associative".
   *    (f()*g()) * h() = f() * (g()*h())
   * End.
   * }}}
   */
  lazy val timesAssociativeF = "(f() * g()) * h() = f() * (g() * h())".asFormula
  lazy val timesAssociative = derivedAxiom("* associative", Sequent(Nil, IndexedSeq(), IndexedSeq(timesAssociativeF)), QE)
  lazy val timesAssociativeT = derivedAxiomT(timesAssociative)

  /**
   * {{{Axiom "+ commutative".
   *    f()+g() = g()+f()
   * End.
   * }}}
   */
  lazy val plusCommutativeF = "f()+g() = g()+f()".asFormula
  lazy val plusCommutative = derivedAxiom("+ commutative", Sequent(Nil, IndexedSeq(), IndexedSeq(plusCommutativeF)), QE)
  lazy val plusCommutativeT = derivedAxiomT(plusCommutative)

  /**
   * {{{Axiom "* commutative".
   *    f()*g() = g()*f()
   * End.
   * }}}
   */
  lazy val timesCommutativeF = "f()*g() = g()*f()".asFormula
  lazy val timesCommutative = derivedAxiom("* commutative", Sequent(Nil, IndexedSeq(), IndexedSeq(timesCommutativeF)), QE)
  lazy val timesCommutativeT = derivedAxiomT(timesCommutative)

  /**
   * {{{Axiom "distributive".
   *    f()*(g()+h()) = f()*g() + f()*h()
   * End.
   * }}}
   */
  lazy val distributiveF = "f()*(g()+h()) = f()*g() + f()*h()".asFormula
  lazy val distributive = derivedAxiom("distributive", Sequent(Nil, IndexedSeq(), IndexedSeq(distributiveF)), QE)
  lazy val distributiveT = derivedAxiomT(distributive)

  /**
   * {{{Axiom "+ identity".
   *    f()+0 = f()
   * End.
   * }}}
   */
  lazy val plusIdentityF = zeroPlusF
  lazy val plusIdentity = zeroPlus
  lazy val plusIdentityT = zeroPlusT

  /**
   * {{{Axiom "* identity".
   *    f()*1 = f()
   * End.
   * }}}
   */
  lazy val timesIdentityF = "f()*1 = f()".asFormula
  lazy val timesIdentity = derivedAxiom("* identity", Sequent(Nil, IndexedSeq(), IndexedSeq(timesIdentityF)), QE)
  lazy val timesIdentityT = derivedAxiomT(timesIdentity)

  /**
   * {{{Axiom "+ inverse".
   *    f() + (-f()) = 0
   * End.
   * }}}
   */
  lazy val plusInverseF = "f() + (-f()) = 0".asFormula
  lazy val plusInverse = derivedAxiom("+ inverse", Sequent(Nil, IndexedSeq(), IndexedSeq(plusInverseF)), QE)
  lazy val plusInverseT = derivedAxiomT(plusInverse)

  /**
   * {{{Axiom "* inverse".
   *    f() != 0 -> f()*(f()^-1) = 1
   * End.
   * }}}
   */
  lazy val timesInverseF = "f() != 0 -> f()*(f()^-1) = 1".asFormula
  lazy val timesInverse = derivedAxiom("* inverse", Sequent(Nil, IndexedSeq(), IndexedSeq(timesInverseF)), QE)
  lazy val timesInverseT = derivedAxiomT(timesInverse)

  /**
   * {{{Axiom "positivity".
   *    f() < 0 | f() = 0 | 0 < f()
   * End.
   * }}}
   */
  lazy val positivityF = "f() < 0 | f() = 0 | 0 < f()".asFormula
  lazy val positivity = derivedAxiom("positivity", Sequent(Nil, IndexedSeq(), IndexedSeq(positivityF)), QE)
  lazy val positivityT = derivedAxiomT(positivity)

  /**
   * {{{Axiom "+ closed".
   *    0 < f() & 0 < g() -> 0 < f()+g()
   * End.
   * }}}
   */
  lazy val plusClosedF = "0 < f() & 0 < g() -> 0 < f()+g()".asFormula
  lazy val plusClosed = derivedAxiom("+ closed", Sequent(Nil, IndexedSeq(), IndexedSeq(plusClosedF)), QE)
  lazy val plusClosedT = derivedAxiomT(plusClosed)

  /**
   * {{{Axiom "* closed".
   *    0 < f() & 0 < g() -> 0 < f()*g()
   * End.
   * }}}
   */
  lazy val timesClosedF = "0 < f() & 0 < g() -> 0 < f()*g()".asFormula
  lazy val timesClosed = derivedAxiom("* closed", Sequent(Nil, IndexedSeq(), IndexedSeq(timesClosedF)), QE)
  lazy val timesClosedT = derivedAxiomT(timesClosed)

  /**
   * {{{Axiom "<".
   *    f() < g() <-> 0 < g()-f()
   * End.
   * }}}
   */
  lazy val lessF = "f() < g() <-> 0 < g()-f()".asFormula
  lazy val less = derivedAxiom("<", Sequent(Nil, IndexedSeq(), IndexedSeq(lessF)), QE)
  lazy val lessT = derivedAxiomT(less)

  /**
   * {{{Axiom ">".
   *    f() > g() <-> g() < f()
   * End.
   * }}}
   */
  lazy val greaterF = "f() > g() <-> g() < f()".asFormula
  lazy val greater = derivedAxiom(">", Sequent(Nil, IndexedSeq(), IndexedSeq(greaterF)), QE)
  lazy val greaterT = derivedAxiomT(greater)

  /**
   * {{{Axiom "abs".
   *   (abs(s()) = t()) <->  ((s()>=0 & t()=s()) | (s()<0 & t()=-s()))
   * End.
   * }}}
   * @Derived from built-in arithmetic abs in [[edu.cmu.cs.ls.keymaerax.tools.Mathematica]]
   */
  lazy val absF = "(abs(s()) = t()) <->  ((s()>=0 & t()=s()) | (s()<0 & t()=-s()))".asFormula
  lazy val absDef = derivedAxiom("abs", Sequent(Nil, IndexedSeq(), IndexedSeq(absF)), QE)
  lazy val absT = derivedAxiomT(absDef)

  /**
   * {{{Axiom "min".
   *    (min(f(), g()) = h()) <-> ((f()<=g() & h()=f()) | (f()>g() & h()=g()))
   * End.
   * }}}
   * @Derived from built-in arithmetic abs in [[edu.cmu.cs.ls.keymaerax.tools.Mathematica]]
   */
  lazy val minF = "(min(f(), g()) = h()) <-> ((f()<=g() & h()=f()) | (f()>g() & h()=g()))".asFormula
  lazy val minDef = derivedAxiom("min", Sequent(Nil, IndexedSeq(), IndexedSeq(minF)), QE)
  lazy val minT = derivedAxiomT(minDef)

  /**
   * {{{Axiom "max".
   *    (max(f(), g()) = h()) <-> ((f()>=g() & h()=f()) | (f()<g() & h()=g()))
   * End.
   * }}}
   * @Derived from built-in arithmetic abs in [[edu.cmu.cs.ls.keymaerax.tools.Mathematica]]
   */
  lazy val maxF = "(max(f(), g()) = h()) <-> ((f()>=g() & h()=f()) | (f()<g() & h()=g()))".asFormula
  lazy val maxDef = derivedAxiom("max", Sequent(Nil, IndexedSeq(), IndexedSeq(maxF)), QE)
  lazy val maxT = derivedAxiomT(maxDef)

  /**
   * {{{Axiom "<*> stuck".
   *    <{a;}*>p(??) <-> <{a;}*>p(??)
   * End.
   * }}}
   * @Derived
   * @note Trivial reflexive stutter axiom, only used with a different recursor pattern in AxiomIndex.
   */
  lazy val loopStuckF = "<{a;}*>p(??) <-> <{a;}*>p(??)".asFormula
  lazy val loopStuck = derivedAxiom("<*> stuck",
    Sequent(Nil, IndexedSeq(), IndexedSeq(loopStuckF)),
    byUS("<-> reflexive")
  )
  lazy val loopStuckT = derivedAxiomT(loopStuck)

  /**
   * {{{Axiom "<'> stuck".
   *    <{c&H(??)}>p(??) <-> <{c&H(??)}>p(??)
   * End.
   * }}}
   * @Derived
   * @note Trivial reflexive stutter axiom, only used with a different recursor pattern in AxiomIndex.
   */
  lazy val odeStuckF = "<{c&H(??)}>p(??) <-> <{c&H(??)}>p(??)".asFormula
  lazy val odeStuck = derivedAxiom("<'> stuck",
    Sequent(Nil, IndexedSeq(), IndexedSeq(odeStuckF)),
    byUS("<-> reflexive")
  )
  lazy val odeStuckT = derivedAxiomT(odeStuck)


  /**
   * {{{Axiom "+<= up".
   *    f()+g()<=h() <- ((f()<=F() & g()<=G()) & F()+G()<=h())
   * End.
   * }}}
   */
  lazy val intervalUpPlusF = "f()+g()<=h() <- ((f()<=F() & g()<=G()) & F()+G()<=h())".asFormula
  lazy val intervalUpPlus = derivedAxiom("+<= up", Sequent(Nil, IndexedSeq(), IndexedSeq(intervalUpPlusF)), QE)
  lazy val intervalUpPlusT = derivedAxiomT(intervalUpPlus)

  /**
   * {{{Axiom "-<= up".
   *    f()-g()<=h() <- ((f()<=F() & G()<=g()) & F()-G()<=h())
   * End.
   * }}}
   */
  lazy val intervalUpMinusF = "f()-g()<=h() <- ((f()<=F() & G()<=g()) & F()-G()<=h())".asFormula
  lazy val intervalUpMinus = derivedAxiom("-<= up", Sequent(Nil, IndexedSeq(), IndexedSeq(intervalUpMinusF)), QE)
  lazy val intervalUpMinusT = derivedAxiomT(intervalUpMinus)


  /**
   * {{{Axiom "*<= up".
   *    f()*g()<=h() <- ((ff()<=f() & f()<=F() & gg()<=g() & g()<=G()) & (ff()*gg()<=h() & ff()*G()<=h() & F()*gg()<=h() & F()*G()<=h()))
   * End.
   * }}}
   */
  lazy val intervalUpTimesF = "f()*g()<=h() <- ((ff()<=f() & f()<=F() & gg()<=g() & g()<=G()) & (ff()*gg()<=h() & ff()*G()<=h() & F()*gg()<=h() & F()*G()<=h()))".asFormula
  lazy val intervalUpTimes = derivedAxiom("*<= up", Sequent(Nil, IndexedSeq(), IndexedSeq(intervalUpTimesF)), QE)
  lazy val intervalUpTimesT = derivedAxiomT(intervalUpTimes)


  /**
   * {{{Axiom "1Div<= up".
   *    1/f()<=h() <- ((F()<=f() & F()*f()>0) & (1/F()<=h()))
   * End.
   * }}}
   */
  lazy val intervalUp1DivideF = "1/f()<=h() <- ((F()<=f() & F()*f()>0) & (1/F()<=h()))".asFormula
  lazy val intervalUp1Divide = derivedAxiom("1Div<= up", Sequent(Nil, IndexedSeq(), IndexedSeq(intervalUp1DivideF)), QE)
  lazy val intervalUp1DivideT = derivedAxiomT(intervalUp1Divide)


  /**
   * {{{Axiom "Div<= up".
   *    f()/g()<=h() <- f()*(1/g())<=h() & g()!=0
   * End.
   * }}}
   */
  lazy val intervalUpDivideF = "f()/g()<=h() <- (f()*(1/g())<=h()) & g()!=0".asFormula
  lazy val intervalUpDivide = derivedAxiom("Div<= up", Sequent(Nil, IndexedSeq(), IndexedSeq(intervalUpDivideF)), QE)
  lazy val intervalUpDivideT = derivedAxiomT(intervalUpDivide)


  /**
   * {{{Axiom "<=+ down".
   *    h()<=f()+g() <- ((F()<=f() & G()<=g()) & h()<=F()+G())
   * End.
   * }}}
   */
  lazy val intervalDownPlusF = "h()<=f()+g() <- ((F()<=f() & G()<=g()) & h()<=F()+G())".asFormula
  lazy val intervalDownPlus = derivedAxiom("<=+ down", Sequent(Nil, IndexedSeq(), IndexedSeq(intervalDownPlusF)), QE)
  lazy val intervalDownPlusT = derivedAxiomT(intervalDownPlus)

  /**
   * {{{Axiom "<=- down".
   *    h()<=f()-g() <- ((F()<=f() & g()<=G()) & h()<=F()-G())
   * End.
   * }}}
   */
  lazy val intervalDownMinusF = "h()<=f()-g() <- ((F()<=f() & g()<=G()) & h()<=F()-G())".asFormula
  lazy val intervalDownMinus = derivedAxiom("<=- down", Sequent(Nil, IndexedSeq(), IndexedSeq(intervalDownMinusF)), QE)
  lazy val intervalDownMinusT = derivedAxiomT(intervalDownMinus)


  /**
   * {{{Axiom "<=* down".
   *    h()<=f()*g()<- ((ff()<=f() & f()<=F() & gg()<=g() & g()<=G()) & (h()<=ff()*gg() & h()<=ff()*G() & h()<=F()*gg() & h()<=F()*G()))
   * End.
   * }}}
   */
  lazy val intervalDownTimesF = "h()<=f()*g()<- ((ff()<=f() & f()<=F() & gg()<=g() & g()<=G()) & (h()<=ff()*gg() & h()<=ff()*G() & h()<=F()*gg() & h()<=F()*G()))".asFormula
  lazy val intervalDownTimes = derivedAxiom("<=* down", Sequent(Nil, IndexedSeq(), IndexedSeq(intervalDownTimesF)), QE)
  lazy val intervalDownTimesT = derivedAxiomT(intervalDownTimes)


  /**
   * {{{Axiom "<=1Div down".
   *    h()<=1/f() <- ((f()<=F() & F()*f()>0) & (h()<=1/F()))
   * End.
   * }}}
   */
  lazy val intervalDown1DivideF = "h()<=1/f() <- ((f()<=F() & F()*f()>0) & (h()<=1/F()))".asFormula
  lazy val intervalDown1Divide = derivedAxiom("<=1Div down", Sequent(Nil, IndexedSeq(), IndexedSeq(intervalDown1DivideF)), QE)
  lazy val intervalDown1DivideT = derivedAxiomT(intervalDown1Divide)


  /**
   * {{{Axiom "<=Div down".
   *    h()<=f()/g() <- h()<=f()*(1/g()) & g()!=0
   * End.
   * }}}
   */
  lazy val intervalDownDivideF = "h()<=f()/g() <- h()<=f()*(1/g()) & g()!=0".asFormula
  lazy val intervalDownDivide = derivedAxiom("<=Div down", Sequent(Nil, IndexedSeq(), IndexedSeq(intervalDownDivideF)), QE)
  lazy val intervalDownDivideT = derivedAxiomT(intervalDownDivide)

}