/**
* Copyright (c) Carnegie Mellon University. CONFIDENTIAL
* See LICENSE.txt for the conditions of this license.
*/
package edu.cmu.cs.ls.keymaerax.tactics

import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.tactics.AxiomTactic._
import edu.cmu.cs.ls.keymaerax.tactics.BranchLabels._
import edu.cmu.cs.ls.keymaerax.tactics.FOQuantifierTacticsImpl._
import edu.cmu.cs.ls.keymaerax.tactics.SearchTacticsImpl._
import edu.cmu.cs.ls.keymaerax.tactics.TacticLibrary.AxiomCloseT
import edu.cmu.cs.ls.keymaerax.tactics.TacticLibrary.TacticHelper._
import edu.cmu.cs.ls.keymaerax.tactics.TacticLibrary.skolemizeT
import edu.cmu.cs.ls.keymaerax.tactics.Tactics._
import edu.cmu.cs.ls.keymaerax.tools.Tool
import TacticLibrary._

import scala.collection.immutable.List

/**
 * Solves simple ODEs.
 * @author Nathan Fulton
 */
object LogicalODESolver {


  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // tactics for the advanced solver
  // The advanced solver is the same as the simple solver, but instead of diffWeaken it does successive inverse ghosts
  // and inverse cuts until finally only time remains, and then solves just for t' = 0*t + 1. This allows the selection
  // of only specific points in time.
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Given a system of form:
   *    [x1' = theta1, ..., t' = 0*t+1 & x1=s1, ...]p
   * produces a system of form:
   *    [x2' = theta2, ..., t' = 0*t+1 & x1=s1, ...]p
   * that is, it removes the first ODE from the system that is not required.
   *
   * @return The tactic.
   */
  /*
  private def stepRemoveT : PositionTactic = new PositionTactic("Remove solved ODE from system") {
    override def applies(s: Sequent, p: Position): Boolean = s(p) match {
      case program : DifferentialProduct => {
        val solvedEquations = conditionsToValues(odeConstraints(program))
        val variables = atomicODEs(program).map(_.xp.x)

        val nextVariable : Option[Variable] = ???

        (variables.toSet - timeVar(program) -- solvedEquations.keys.toSet).isEmpty && nextVariable.isDefined
      }
    }

    override def apply(p: Position): Tactic = new ConstructionTactic() {
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
        case program : DifferentialProduct => {
          val solvedEquations = conditionsToValues(odeConstraints(program))
          val variables = atomicODEs(program).map(_.xp.x)
          require((variables.toSet - timeVar(program) -- solvedEquations.keys.toSet).isEmpty,
            "All primed variables should have solution")

          ???
        }
      }

      override def applicable(node: ProofNode): Boolean = ???
    }
  }
  */

  private def removeTimeVar : PositionTactic = ???


  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tactics of the simple solver
  //////////////////////////////////////////////////////////////////////////////////////////////////

  def solveT : PositionTactic = new PositionTactic("Solve ODE") {
    override def applies(s: Sequent, p: Position): Boolean = true //@todo

    override def apply(p: Position): Tactic =
      LogicalODESolver.setupTimeVarT(p) ~
      (stepTacticT(p) *) &
      cutTimeLB(p) &
      ODETactics.diffWeakenAxiomT(p) & //the axiom, not the proof rule.
      renameAndDropImpl(p) & onBranch(
        ("renameAndDropImpl-output", (successiveInverseCut(p) ))
      )
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Successive diff cuts
  //////////////////////////////////////////////////////////////////////////////////////////////////
  private def successiveInverseCut : PositionTactic = new PositionTactic("successiveInverseCut") {
    override def applies(s: Sequent, p: Position): Boolean = s(p) match {
      case Box(pi:DifferentialProgram, f : Formula) => true //not even close.
      case _ => false
    }

    // ([c&H(?);]p(?) <-> [c&(H(?)&r(?));]p(?)) <- [c&H(?);]r(?)
    override def apply(p: Position): Tactic = new ConstructionTactic("construct next " + name) {
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
        case Box(program : DifferentialProgram, formula) => {
          val lastPartial = getLastPartialSoln(program)

            Some(
            debugT("before axiom") &
            mvPartialSolnToEnd(lastPartial)(p) &
            debugT(s"Successfully moved partial soln $lastPartial to end") &
            ODETactics.diffCutInvAxiomT(lastPartial)(p) & onBranch(
              (BranchLabels.axiomUseLbl, debugT("Axiom use")),
              (BranchLabels.axiomShowLbl, debugT("axiom show"))
            )
          )

        }
      }

      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
    }
  }

  private def getLastPartialSoln(program : DifferentialProgram) : Formula = program match {
    case ODESystem(odes, constraint) => extractInitialConditions(constraint).last
    case _ => throw new Exception("Need to implement all cases. Not sure." + program)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // G,K, diff cuts, diff ghost, diff solve for time, assignment, arith.
  //////////////////////////////////////////////////////////////////////////////////////////////////
  /**
   * This is the G,K step of the proof on page 25 of the uniform substitution calculus paper.
   * However, I couldn't see how that could possibly follow from just G and K...
   *
   * Input: [pi, x=\theta&...;](x=\theta&... --> f(x...))
   * Output: [pi, x=\theta&...;](\thetas replace xs in f)
   */
  private def renameAndDropImpl : PositionTactic = new PositionTactic("renameAndDropImpl") {
    override def applies(s: Sequent, p: Position): Boolean = s(p) match {
      case Box(pi : DifferentialProgram, Imply(evolutionDomain, originalConclusion)) => true
      case _ => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic("Construct " + name) {
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
        case Box(pi: DifferentialProgram, Imply(evolutionDomain, originalConclusion)) => {
          val newConclusion = constructNewConclusion(evolutionDomain, originalConclusion)

          val rewritingFormula = Equiv(Box(pi, Imply(evolutionDomain, originalConclusion)), Box(pi, newConclusion))

          Some(
            cutT(Some(rewritingFormula)) & onBranch(
            //Um yeah not sure what was meant here but it's definitely not G,K...
//              (BranchLabels.cutShowLbl, dischargeEquivalence(pi, Imply(evolutionDomain, originalConclusion), newConclusion)(SuccPos(node.sequent.succ.length))),
              (BranchLabels.cutShowLbl, LabelBranch("renameAndDropImpl-todo")),
              (BranchLabels.cutUseLbl, {
                val equivPos = AntePos(node.sequent.ante.length)
                assertPT(rewritingFormula, "Precond check failed: Expected equivalence")(equivPos) &
                EqualityRewritingImpl.equivRewriting(equivPos, p) ~
                assertPT(Box(pi, newConclusion), "Postcond check failed: Expected new conclusion")(p) &
                LabelBranch("renameAndDropImpl-output")
              })
            )
          )
        }
      }

      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
    }

    /**
     * Input: [pi](x = T -> p(x)) <-> [pi](q) where q = T for x in p
     * Output: Close
     */
//    private def dischargeEquivalence(pi : DifferentialProgram, originalConclusion : Formula, newConclusion : Formula) : PositionTactic = new PositionTactic(name + " discharge equivalence") {
//      override def applies(s: Sequent, p: Position): Boolean = s(p) match {
//        case Equiv(Box(leftProgram, leftFormula), Box(rightProgram, rightFormula)) =>
//          leftProgram.equals(pi) && rightProgram.equals(pi) &&
//            leftFormula.equals(originalConclusion) && rightFormula.equals(newConclusion)
//        case _ => false
//      }

//      override def apply(p: Position): Tactic =
//        PropositionalTacticsImpl.cohideT(p) &
//        debugT("what's up?") &
//        EquivRightT(SuccPos(0)) &
//        debugT("sup?")
//    }

//    private def dischargeEquivalence(pi : DifferentialProgram, originalConclusion : Formula, newConclusion : Formula) : PositionTactic = new PositionTactic(name + " discharge equivalence") {
//      override def applies(s: Sequent, p: Position): Boolean = s(p) match {
//        case Equiv(Box(leftProgram, leftFormula), Box(rightProgram, rightFormula)) =>
//          leftProgram.equals(pi) && rightProgram.equals(pi) &&
//          leftFormula.equals(originalConclusion) && rightFormula.equals(newConclusion)
//        case _ => false
//      }
//
//      override def apply(p: Position): Tactic =
//        PropositionalTacticsImpl.cohideT(p) &
//        AxiomaticRuleTactics.equivalenceCongruenceT(PosInExpr(1 :: Nil)) ~
//        assertPT(Equiv(originalConclusion, newConclusion),
//          "Expected to find appropriate equiv " + originalConclusion.prettyString + " " + newConclusion.prettyString)(SuccPos(0)) &
//        PropositionalTacticsImpl.EquivRightT(SuccPos(0)) & onBranch(
//          (BranchLabels.equivLeftLbl, TacticLibrary.arithmeticT  ~ errorT("left label")),
//          (BranchLabels.equivRightLbl, TacticLibrary.arithmeticT ~ errorT("right label"))
//        )
//    }

//    private def dischargeEquivalence(program : DifferentialProgram,
//                                     originalConclusion : Formula,
//                                     newConclusion : Formula) : PositionTactic =
//      new PositionTactic(name + "Discharge Equivalence") {
//        override def applies(s: Sequent, p: Position): Boolean = s(p) match {
//          case Equiv(Box(leftProgram, leftFormula), Box(rightProgram, rightFormula)) =>
//            leftProgram.equals(program) && rightProgram.equals(program) &&
//            leftFormula.equals(originalConclusion) && rightFormula.equals(newConclusion)
//          case _ => false
//        }
//
//        override def apply(p: Position): Tactic = debugT("Before equiv right") &
//          assertPT(Equiv(Box(program, originalConclusion), Box(program, newConclusion)))(p) &
//          PropositionalTacticsImpl.cohideT(p) &
//          EquivRightT(SuccPos(0)) & debugT("broken...")
//      }

    private def constructNewConclusion(evolutionDomain : Formula, originalConclusion : Formula) = {
      //Compute the new conclusion.
      val fvsConclusion = StaticSemantics.freeVars(originalConclusion) //Free vars of conc'l
      val variablesToReplace : List[Equal] = extractInitialConditions(evolutionDomain).filter(_ match {
          case Equal(x: Variable, _) => fvsConclusion.contains(x)
          case _ => false
        }).asInstanceOf[List[Equal]]

      variablesToReplace.foldLeft(originalConclusion)(
        (currentFormula, nextEquality) =>
          SubstitutionHelper.replaceFree(currentFormula)(nextEquality.left, nextEquality.right))
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Setting up explicit time and add solutions to the evoluation domain constraint
  //////////////////////////////////////////////////////////////////////////////////////////////////
  private def cutTimeLB : PositionTactic = new PositionTactic("DiffCut and prove a lower-bound on time.") {
    override def applies(s: Sequent, p: Position): Boolean = s(p) match {
      case Box(odes:ODESystem, _) => true //@todo
      case _ => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic("Construct " + name) {
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
        case Box(program : DifferentialProgram, f) => {
          val t = timeVar(program).getOrElse(throw new Exception("Need time var"))

          //Should always be 0, but let's be safe.
          val timeInitialCondition : Term = node.sequent.ante.flatMap(extractInitialConditions).find(f => f match {
            case Equal(x, _) if x.equals(t) => true
            case _ => false
          }).getOrElse(throw new Exception("Need initial condition on time variable " + t)) match {
            case Equal(x, term) => term
            case _ => throw new Exception("find failed.")
          }

          val theCut = diffCutT(GreaterEqual(t, timeInitialCondition))(p) & onBranch(
            (BranchLabels.cutShowLbl, diffInvariant(p)),
            (BranchLabels.cutUseLbl, /*yield*/NilT)
          ) & debugT("yield from cutTimeLB")

          Some(theCut)
        }
      }


      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
    }
  }

  private def setupTimeVarT : PositionTactic = new PositionTactic("Introduce time into ODE") {

    override def applies(s: Sequent, p: Position): Boolean = s(p) match {
      case Box(dp : DifferentialProgram, f) => timeVar(dp).isEmpty
      case _ => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic("Construct " + name) {

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
        case Box(program : DifferentialProgram, f) => {
          //Copied from DiffSolutionT
          // HACK need some convention for internal names
          val initialTime: Variable = freshNamedSymbol(Variable("kxtime", None, Real), node.sequent)
          // universal quantifier and skolemization in ghost tactic (t:=0) will increment index twice
          val time = Variable(initialTime.name,
            initialTime.index match { case None => Some(1) case Some(a) => Some(a+2) }, initialTime.sort)

          val lastAntePos = AntePos(node.sequent.ante.length + 1)

          val setTimer = HybridProgramTacticsImpl.nonAbbrvDiscreteGhostT(Some(initialTime), Number(0))(p) & boxAssignT(p)

          val tempTime = Variable(time.name, time.index match {
            case None => Some(1)
            case Some(a) => Some(a + 1)
          })
          val introTime =
            setTimer &
            ODETactics.diffAuxiliaryT(time, Number(0), Number(1))(p) &
            errorT("Need exists monotone") &
            hasTimeAssertionT(p) //Check post-cond holds.

          Some(introTime)
        }
      }

      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      /**
       * Post-condition of setupTimeVarT
       * A test that passes if and only if there is a time variable in the ODE.
       */
      private val hasTimeAssertionT = assertPT( (s,p) => {
        val fAtPos : Formula = s(p)
        fAtPos match {
          case Box(differentialProgram : DifferentialProgram, cond) => {
            val tv = timeVar(differentialProgram)
            if(tv.isDefined) {
              println("Found a time variable: " + tv.get.prettyString)
              true
            }
            else {
              println("Did not find time variable.")
              false
            }
          }
          case _ => {
            println("Variable did not have correct form: " + fAtPos.prettyString);
            false
          }
        }
      }, "Expected to find [differnetialProgram]phi, where differentialProgram has a time variable.")
    }
  }

  /**
   * @return A tactic that cuts in a solution to an ODE in a system. This should be saturated.
   */
  private def stepTacticT : PositionTactic = new PositionTactic("Logical ODE Solver") {
    override def applies(s: Sequent, p: Position): Boolean = s(p) match {
      case Box(program : DifferentialProgram, _) => {
        val hasNextStep = atomicODEs(program).filter(ode => !timeVar(program).getOrElse( () ).equals(ode.xp.x)).find(ode => isUnsolved(ode.xp.x, program)) match {
          case Some(_) => true
          case None => false
        }
        timeVar(program).isDefined && hasNextStep
      }
      case _ => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic("Construct " + name) {
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        val initialConditions : List[Formula] = node.sequent.ante.flatMap(extractInitialConditions).toList

        node.sequent(p) match {
          case Box(program : DifferentialProgram, f) => {
            val sortedOdes = sortAtomicOdes(atomicODEs(program))
            val nextOde = sortedOdes
              .filter(ode => !timeVar(program).getOrElse( () ).equals(ode.xp.x)) //Skip time var, which we deal with using diff solve instead of diff inv.
              .find(ode => isUnsolved(ode.xp.x, program)).getOrElse(throw new Exception("applies method failed."))
            val toCut = Equal(nextOde.xp.x, integralOf(nextOde.xp.x, program, initialConditions))



            Some(ODETactics.diffCutT(toCut)(p) & onBranch(
              (BranchLabels.cutUseLbl, /*yield*/NilT),
              (BranchLabels.cutShowLbl, ODETactics.diffInvariantT(p))
            ))
          }
          case _ => throw new Exception
        }
      }

      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
    }
  }


  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Helper methods for step tactic.
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   *
   * @param f A formula.
   * @return A list of formulas with no top-level andss.
   */
  private def decomposeAnds(f : Formula) : List[Formula] = f match {
    case And(l,r) => decomposeAnds(l) ++ decomposeAnds(r)
    case _ => f :: Nil
  }

  /**
   * Converts list of formulas possibly containing Ands into list of formulas that does not contain any ANDs.
   * @param fs A list of formulas, possibly containing Ands.
   */
  private def flattenAnds(fs : List[Formula]) = fs.flatMap(decomposeAnds)

  /**
   *
   * @param f A formula containing conjunctions.
   * @return A list of *equality* formulas after deconstructing Ands. E.g., A&B&C -> A::B::C::Nil
   */
  private def extractInitialConditions(f : Formula) : List[Formula] = flattenAnds(f match {
    case And(l, r) => extractInitialConditions(l) ++ extractInitialConditions(r)
    case Equal(v: Variable, _) => f :: Nil
    case Equal(_, v: Variable) => f :: Nil
    case _ => Nil //ignore?
  })

  /**
   *
   * @param v A variable occuring in the odes program.
   * @param program An ode system.
   * @return true if the program does not already contain an = constraint (a.k.a. sol'n) for v in the evolution domain.
   */
  def isUnsolved(v : Variable, program : DifferentialProgram) = {
    val odes = atomicODEs(program)
    if(odes.find(_.xp.x.equals(v)).isEmpty) false //Variables that don't occur in the ODE are trivially already solved.
    else if(timeVar(program).equals(v)) false //Don't need to solve for the time var.
    //In non-special cases, check for a = evolution domain constraint in the ode.
    else {
      val vConstraints = odeConstraints(program).flatMap(decomposeAnds).find(_ match {
        case Equal(l, r) => l.equals(v)
        case _ => false
      })
      vConstraints.isEmpty
    }
  }

  /**
   * @param odes
   * @return
   */
  private def sortAtomicOdes(odes : List[AtomicODE]) : List[AtomicODE] = {
    sortAtomicOdesHelper(odes).map(v => odes.find(_.xp.x.equals(v)).get)
  }

  //@todo check this implementation.
  private def sortAtomicOdesHelper(odes : List[AtomicODE], prevOdes : List[AtomicODE] = Nil) : List[Variable] = {
    var primedVars = odes.map(_.xp.x)

    def dependencies(v : Variable) : List[Variable] = {
      val vTerm = odes.find(_.xp.x.equals(v)).get.e
      //remove self-references to cope with the fact that t' = 0*t + 1, which is necessary due to DG.
      primedVars.filter(StaticSemantics.freeVars(vTerm).contains(_)).filter(!_.equals(v))
    }

    var nonDependentSet : List[Variable] = primedVars.filter(dependencies(_).isEmpty)
    val possiblyDependentOdes = odes.filter(ode => !nonDependentSet.contains(ode.xp.x))

    if(possiblyDependentOdes.isEmpty) nonDependentSet
    else {
      if(prevOdes.equals(possiblyDependentOdes)) throw new Exception("Cycle detected!")
      nonDependentSet ++ sortAtomicOdesHelper(possiblyDependentOdes, odes)
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Integrals of a single ODE.
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * If v' = term occurs in the system of ODEs, then this function computes the integral of term.
   * Assumes that the ODEs have a time variable, that a formula of the form v=f occurs in the initialConditions formulas,
   * and that the system of odes is blah blah.
   * @param v
   * @param program
   * @param initialConditions
   * @return Integral of f assuming v' = f occurs in ODEs.
   */
  def integralOf(v : Variable, program : DifferentialProgram, initialConditions : List[Formula]) : Term = {
    val termToIntegrate = resolveRecurrences(v, program, initialConditions)
    println("Integrating term " + termToIntegrate)

    val t = timeVar(program) match {
      case Some(t) => t
      case None    => throw new Exception("Could not find time variable in ODEs")
    }

    val v_0 : Term = conditionsToValues(initialConditions).get(v) match {
      case Some(x) => x
      case None => throw new Exception("Could not find initial condition for " + v.name)
    }

    Plus(integrator(termToIntegrate, t), v_0)
  }

  /**
   * A syntactic integrator for @todo something like sums of terms over polynomials univariable in t.
   * @param term The term
   * @param t Time variable
   * @return Integral term dt
   */
  private def integrator(term : Term, t : Variable) : Term = term match {
    case Plus(l, r) => Plus(integrator(l, t), integrator(r, t))
    case Minus(l, r) => Minus(integrator(l, t), integrator(r, t))
    case Times(c, x) if x.equals(t) && !StaticSemantics.freeVars(c).contains(t) => Times(Divide(c, Number(2)), Power(x, Number(2)))
    case Times(c, Power(x, exp)) if x.equals(t) && !StaticSemantics.freeVars(exp).contains(t) && !StaticSemantics.freeVars(c).contains(t) => {
      val newExp = exp match {
        case Number(n) => Number(n+1)
        case _ => Plus(exp, Number(1))
      }
      Times(Divide(c, newExp), Power(t, newExp))
    }
    case Neg(c) => Neg(integrator(c, t))
    case Power(base, exp) => exp match {
      case Number(n) =>
        if(n == 1) integrator(base, t)
        else       Times(Divide(Number(1), Number(n+1)), integrator(Power(base, Number(n-1)), t))
      case _ => throw new Exception("Cannot integrate terms with non-number exponents!")
    }
    case x : Term if !StaticSemantics.freeVars(x).contains(t) => Times(x, t)
  }

  /**
   * Given x' = f, replaces all variables in f with their recurrences or initial conditions.
   * @param v A variable s.t. v' = f occurs in the ODEs.
   * @param program ODE(s) with a time variable (some x s.t. x' = 1).
   * @param initialConditions Any initial conditions for the ODE.
   * @return f with all variables replaced by their recurrences or initial conditions.
   */
  def resolveRecurrences(v : Variable, program : DifferentialProgram, initialConditions : List[Formula]) : Term = {
    val odes         = atomicODEs(program)

    val time : Variable = timeVar(program) match {
      case Some(theTimeVar) => theTimeVar
      case None             => throw new Exception("A time variable should exist prior to calling solutionForVariable.")
    }

    //The assertion message is not technically true becuase the solution would just be zero.
    //But if the variable requested is not in the ODE, it's most likely this indicates a programming error rather than
    //an honest inquiry.
    assert(odes.find(ode => ode.xp.x.equals(v)).isDefined, "Cannot solve for a variable that does not occur in the ODE")

    val primedVariables : Set[Variable] = odes.map(_.xp.x).toSet

    //Compute the free variables in the ode corresponding to v'.
    val ode = odes.find(_.xp.x.equals(v)).getOrElse(throw new Exception("Could not find ODE associated with " + v))
    val varsInOde = StaticSemantics.freeVars(ode.e).toSet.map((x : NamedSymbol) => {
        assert(x.isInstanceOf[Variable], "Only variables should occur as the child of the LHS of an ODE")
        x.asInstanceOf[Variable]
      })

    //Variables that occur in the term associated with v' and also occur primed in the ODE.
    val recurrenceVars : Set[Variable] = (varsInOde intersect primedVariables) //for lack of a better name.

    //Variables that occur in the term associated with v' but do not occur primed in the ODE.
    val nonRecurringVars : Set[Variable] = varsInOde -- recurrenceVars

    if(recurrenceVars.isEmpty) {
      // If x' = a where a is not a variable occurring in the system of odes, then the solution is
      // x = at + x_0 where t is the time variable and x_0 is the value in initialValues associated with
      val f_initValuesResolved = nonRecurringVars.foldLeft[Term](ode.e)((currTerm, x) => {
        val x_0 = initValue(initialConditions, x)
        assert(x_0.isDefined, "Need an initial condition for non-recurring variable " + x + " while solve for " + v)
        SubstitutionHelper.replaceFree(currTerm)(x, x_0.get)
      })

      f_initValuesResolved
    }
    else {
      //Replace all instance of primed variables in the term assocaited with v'
      val f_substRecurrences = recurrenceVars.foldLeft[Term](ode.e)((currTerm, x) => {
        val xSoln = recurrence(program, initialConditions, x)
        assert(xSoln.isDefined, "Need a solution for recurring variable " + x + " while solving for " + v)
        SubstitutionHelper.replaceFree(currTerm)(x, xSoln.get)
      })
      val f_substInitValues = nonRecurringVars.foldLeft[Term](f_substRecurrences)((currTerm, x) => {
        val x_0 = initValue(initialConditions, x)
        assert(x_0.isDefined, "Need an initial condition for non-recurring variable " + x + " while solve for " + v)
        SubstitutionHelper.replaceFree(currTerm)(x, x_0.get)
      })
      f_substInitValues
    }
  }

  /**
   * Converts formulas of the form x = term into a map x -> term, and ignores all formulas of other forms.
   * @param fs A list of formulas.
   * @return A map (f -> term) which maps each f in fs of the foram f=term to term.
   */
  private def conditionsToValues(fs : List[Formula]) : Map[Variable, Term] = {
    val flattened = flattenAnds(fs)
    val vOnLhs = flattened.map({
      case Equal(left, right) => left match {
        case v : Variable => Some(v, right)
        case _ => None
      }
      case _ => None
    })

    val vOnRhs = flattened.map({
      case Equal(left, right) => right match {
        case v : Variable => Some(v, left)
        case _ => None
      }
      case _ => None
    })

    (vOnLhs ++ vOnRhs)
      .filter(_.isDefined)
      .map(e => e.get._1 -> e.get._2)
      .toMap
  }

  /**
   * @param program (An system of) odes.
   * @param initialConstraints Formulas describing initial values.
   * @param x A variable that occurs on the left hand side of some ode.
   * @return Some(term) if x = term occurs in either the ev.dom. constraint or the initial constraints. Otherwise, None.
   */
  private def recurrence(program : DifferentialProgram, initialConstraints : List[Formula], x : Variable) : Option[Term] = {
    val odeConditions = conditionsToValues(flattenAnds(odeConstraints(program)))
    val initialConditions = conditionsToValues(flattenAnds(initialConstraints))
    if(odeConditions.contains(x)) odeConditions.get(x)
    else if(initialConditions.contains(x)) initialConditions.get(x)
    else None
  }

  /**
   *
   * @param iniitalConstraints
   * @param x The variable whose initial value is requested.
   * @return The initial value of x.
   */
  private def initValue(iniitalConstraints : List[Formula], x : Variable) : Option[Term] = {
    val initialConditions = conditionsToValues(iniitalConstraints)
    initialConditions.get(x)
  }

  /**
   * @param ode
   * @return The list of atomic differential equations occurring in the differential program.
   * @author Nathan Fulton
   */
  private def odeConstraints(ode : DifferentialProgram) : List[Formula] = ode match {
    case AtomicODE(x,e)                   => Nil
    case ODESystem(ode, constraint)       => constraint :: Nil
    case DifferentialProduct(left, right) => odeConstraints(left) ++ odeConstraints(right)
  }

  /**
   * @param ode
   * @return The list of atomic differential equations occurring in the differential program.
   * @author Nathan Fulton
   */
  private def atomicODEs(ode : DifferentialProgram) : List[AtomicODE] = ode match {
    case AtomicODE(x, e)                  => AtomicODE(x,e) :: Nil
    case ODESystem(ode, constraint)       => atomicODEs(ode)
    case DifferentialProduct(left, right) => atomicODEs(left) ++ atomicODEs(right)
  }

  /**
   * @param ode Any differential program.
   * @return A variable x s.t. x'=1 occurs in ode.
   * @author Nathan Fulton
   */
  def timeVar(ode : DifferentialProgram) : Option[Variable] = {
    //The second value is the one that we cut in. @todo maybe actually we really need time to be 0*t + 1?
    def isTimeVar(atomic : AtomicODE) = atomic.e.equals(Number(1)) || atomic.e.equals(Plus(Times(Number(0), atomic.xp.x), Number(1)))

    ode match {
      case atomic:AtomicODE => if(isTimeVar(atomic)) Some(atomic.xp.x) else None
      case ODESystem(ode, constraint)       => timeVar(ode)
      case DifferentialProduct(left, right) => (timeVar(left), timeVar(right)) match {
        case (Some(t), Some(t2)) => if(t.equals(t2)) Some(t) else ???
        case (Some(t), None)     => Some(t)
        case (None, Some(t))     => Some(t)
        case (None, None)        => None
      }
    }
  }

  private def freshTimeVar(s : Sequent) : Variable =
    Variable("t",TacticHelper.freshIndexInSequent("t", s), Real)

  private def freshTimeVar(f : Formula) : Variable =
    Variable("t", TacticHelper.freshIndexInFormula("t", f), Real)



  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Reordering the conjunctions of evolution domain constraints
  // This tactic is used in the LogicalODESolver to move the next relevant partial solution
  // constraint to the end of a conjunction so that the inverse Diff Cut axiom has the appropriate
  // form.
  // [c & (H() & q());]p() <-> [c & (q() & H());]p()
  //////////////////////////////////////////////////////////////////////////////////////////////////
  /**
   * @todo Might need to re-order the conjunction at the end so that And's always right-associate.
   */
  def AndReoderingT : PositionTactic = {
    def axiomInstance(fml: Formula): Formula = fml match {
      case Box(ODESystem(c, And(h, q)), p) =>
        Equiv(fml, Box(ODESystem(c, And(q, h)), p))
      case _ => False
    }

    def condT: PositionTactic = new PositionTactic("Label") {
      override def applies(s: Sequent, p: Position): Boolean = true
      override def apply(p: Position): Tactic = LabelBranch(cutShowLbl)
    }

    uncoverAxiomT("Domain Constraint Conjunction Reordering", axiomInstance, _ => andReorderingAxiomBaseT)
  }

  def andReorderingAxiomBaseT: PositionTactic = { // diffcut = thing to remove
  def subst(fml: Formula) : List[SubstitutionPair] = fml match {
      case Equiv(Box(ODESystem(c, And(h, q)), p), _) =>
        val aC = DifferentialProgramConst("c")
        val aH = PredOf(Function("H", None, Real, Bool), Anything)
        val aP = PredOf(Function("p", None, Real, Bool), Anything)
        val aQ = PredOf(Function("q", None, Real, Bool), Anything)
        SubstitutionPair(aC, c) :: SubstitutionPair(aH, h) :: SubstitutionPair(aP, p):: SubstitutionPair(aQ, q) :: Nil
    }
    axiomLookupBaseT("Domain Constraint Conjunction Reordering", subst, _ => NilPT, (f, ax) => ax) //@todo not sure the ax is necessary here.
  }

  //@todo might be a duplicate implementation.
  def conjunctionToList(f : Formula) : List[Formula] = f match {
    case And(l, r) => conjunctionToList(l) ++ conjunctionToList(r)
    case _ => f :: Nil
  }

  /**
   * Moves soln to the last position in a conjunctive evolution domain constraint.
   * @todo enforce assumption that constraint is conjunctive.
   */
  def mvPartialSolnToEnd(soln: Formula): PositionTactic = new PositionTactic("mvPartialSoln") {
    override def applies(s: Sequent, p: Position): Boolean = p.isTopLevel && (s(p) match {
      case Box(ODESystem(_, constraint), _) => conjunctionToList(constraint).contains(soln)
      case _ => false
    })

    override def apply(p: Position): Tactic = debugT("About to try mv") & (mvPartialSolnStep(soln)(p) *) & assertT(s => s(p) match {
      case Box(ODESystem(_, constraint), _) => conjunctionToList(constraint).last.equals(soln)
    }, s"Post-Cond: last element of ev dom constraint should be $soln.")
  }
  /**
   *
   * @param soln A portion of a (conjunctive) evolution domain constraint
   * @return A tactic that moves soln to the end of the ev dom constraint.
   */
  def mvPartialSolnStep(soln: Formula) : PositionTactic = new PositionTactic("mvPartialSolnStep") {
    assert(soln match {
      case Equal(x:Variable, _) => true
      case _ => false
    }, "Expected soln to be an Equal with a variable on the LHS.");

    override def applies(s: Sequent, p: Position): Boolean = p.isTopLevel && (s(p) match {
      case Box(ODESystem(odes, constraint), _) => {
        val list = conjunctionToList(constraint)
        !list.last.equals(soln) && list.contains(soln)
      }
    })

    override def apply(p: Position): Tactic = new ConstructionTactic("Construct " + name) {
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
        case Box(ODESystem(_, constraint), _) => Some(debugT("About to step.") & AndReoderingT(p))
        case _ => None
      }

      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
    }
  }


}
