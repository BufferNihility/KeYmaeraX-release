package edu.cmu.cs.ls.keymaera.tactics

import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.tactics.BranchLabels._
import edu.cmu.cs.ls.keymaera.tactics.EqualityRewritingImpl.equalityRewriting
import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl.AndRightT
import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl.AxiomCloseT
import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl.ImplyLeftT
import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl.ImplyRightT
import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl.cutT
import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl.hideT
import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl.kModalModusPonensT
import edu.cmu.cs.ls.keymaera.tactics.TacticLibrary.TacticHelper._
import edu.cmu.cs.ls.keymaera.tactics.Tactics._

import edu.cmu.cs.ls.keymaera.tactics.TacticLibrary._
import SearchTacticsImpl.{locateSucc,locateAnte,onBranch}

import scala.collection.immutable.{List, Seq}

import edu.cmu.cs.ls.keymaera.tactics.AlphaConversionHelper._

/**
 * Implementation of tactics for handling hybrid programs.
 */
object HybridProgramTacticsImpl {

  /*********************************************
   * Axiom Tactics
   *********************************************/

  /**
   * Creates a new axiom tactic for differential box assignment [x := t;]
   *  [v':=t;]p(v') <-> p(t)
   * @return The axiom tactic.
   * @author Stefan Mitsch
   */
  def boxDerivativeAssignT = new PositionTactic("[':=] differential assign") {
    def applies(s: Sequent, p: Position): Boolean = s(p) match {
      case BoxModality(Assign(Derivative(_, _: Variable), _), _) => true
      case _ => false
    }

    def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        val innerMost = innerMostPos(node.sequent(p))
        val pos = if (p.isAnte) AntePosition(p.index, innerMost) else SuccPosition(p.index, innerMost)
        if (pos.isTopLevel) Some(boxDerivativeAssignTopLevelT(pos))
        else Some(boxDerivativeAssignInContextT(pos))
      }
    }

    private def innerMostPos(f: Formula): PosInExpr = f match {
      case BoxModality(Assign(Derivative(_, _: Variable), _),
        b@BoxModality(Assign(Derivative(_, _: Variable), _), _)) => innerMostPos(b).second
      case _ => PosInExpr()
    }
  }

  /**
   * Creates a new axiom tactic for differential box assignment [x := t;], applicable only on top level positions and
   * without nested derivative assignments
   *  [v':=t;]p(v') <-> p(t)
   * @author Nathan Fulton
   * @author Stefan Mitsch
   * @return The axiom tactic.
   *
   */
  def boxDerivativeAssignTopLevelT = new AxiomTactic("[':=] differential assign", "[':=] differential assign") {
    override def applies(s: Sequent, p: Position): Boolean = p.isTopLevel && super.applies(s, p)
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(Assign(Derivative(vSort,v:Variable), _), phi) => phi match {
        case BoxModality(Assign(_: Derivative, _), _) => false
        case _ => true
      }
      case _ => false
    }

    override def constructInstanceAndSubst(f: Formula, axiom: Formula): Option[(Formula, Formula, Substitution, Option[PositionTactic], Option[PositionTactic])] = f match {
      case BoxModality(Assign(d@Derivative(vSort, v:Variable), t), p) =>
        val g  = SubstitutionHelper.replaceFree(p)(d, t)
        val axiomInstance = Equiv(f, g)

        // substitution
        val aT = Apply(Function("t", None, Unit, vSort), Nothing)
        val aP = ApplyPredicate(Function("p", None, vSort, Bool), CDot) //(p(t)

        val subst = Substitution(List(
          SubstitutionPair(aT, t),
          SubstitutionPair(aP, SubstitutionHelper.replaceFree(p)(d, CDot))
        ))

        // alpha renaming
        val aV = Variable("v", None, vSort)
        val alpha = new PositionTactic("Alpha") {
          override def applies(s: Sequent, p: Position): Boolean = s(p) match {
            case Equiv(BoxModality(Assign(Derivative(_), _), _), _) => true
            case _ => false
          }

          override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
                Some(globalAlphaRenamingT(v.name, v.index, aV.name, aV.index))

            override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
          }
        }

        val (alphaAxiom, cont) = {
          if (v.name == aV.name && v.index == aV.index) (axiom, None)
          else (replace(axiom)(aV, v), Some(alpha))
        }

        Some(alphaAxiom, axiomInstance, subst, None, cont)
      case _ => throw new Exception("Tactic was not applicable")
    }
  }

  /**
   * Creates a new axiom tactic for differential box assignment [v':=t;]p(v') <-> p(t), in some context
   * @return The new tactic
   * @author Stefan Mitsch
   */
  def boxDerivativeAssignInContextT = new DerivativeAxiomInContextTactic("[':=] differential assign", "[':=] differential assign") {
    override def applies(s: Sequent, p: Position): Boolean = !p.isTopLevel && super.applies(s, p)
    override def applies(f: Formula) = f match {
      case BoxModality(Assign(Derivative(_, _), _), _) => true
      case _ => false
    }

    import PropositionalTacticsImpl.uniformSubstT
    override def constructInstanceAndSubst(f: Formula, axiom: Formula) = f match {
      case b@BoxModality(Assign(dv@Derivative(sort, v: Variable), t), phi) =>
        val aV = Variable("v", None, Real)
        val aT = Apply(Function("t", None, Unit, Real), Nothing)
        val aP = ApplyPredicate(Function("p", None, Real, Bool), CDot)

        val desiredResult = replaceFree(phi)(dv, t)

        val usubst = uniformSubstT(new Substitution(List(SubstitutionPair(aT, t),
          SubstitutionPair(aP, replaceFree(phi)(dv, CDot)))),
          Map(Equiv(b, desiredResult) -> replaceFree(axiom)(aV, v, None)))

        val alpha = assertT(0, 1) & globalAlphaRenamingT(v.name, v.index, aV.name, aV.index)

        Some(desiredResult, Some(usubst & alpha))
      case _ => None
    }
  }


  def boxDerivativeAssignTold: PositionTactic = new AxiomTactic("[':=] differential assign equational", "[':=] differential assign equational") {
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(Assign(Derivative(_,Variable(_)),_),_) => true
    }

    /**
     * [v' := t;]p(v') <-> \forall v_newIndex . (v'=t -> p(v'))
     *
     * This methods constructs the axiom before the renaming, axiom instance, substitution to be performed and a tactic
     * that performs the renaming.
     *
     * An axiom tactic performs the following steps (Hilbert style):
     * 1. Guess axiom
     * 2. Rename bound variables to match the instance we want
     * 3. Perform Uniform substitution to instantiate the axiom
     *
     * Axioms usually have the form ax = OP(a, b). The constructed instance either has the form OP(f, g) or OP(g, f).
     * Here, f is an input to this function and g is derived from the axiom to be used. The output of this function
     * should be 4 things:
     * 1. The form of the axiom before apply the tactic provided in 4
     * 2. The instance of the axiom eventually to be used in the proof
     * 3. The substitution to turn 2 into 1
     * 4. A tactic to turn the result of 3 into the actual axiom
     *
     * In the long run all this should be computed by unification.
     *
     * @param f the formula that should be rewritten using the axiom
     * @param axiom the axiom to be used
     * @return (Axiom before executing the given position tactic;
     *         the instance of the axiom,
     *         the uniform substitution that transforms the first into the second axiom (Hilbert style);
     *         an optional position tactic that transforms the first
     *         argument into the actual axiom (usually alpha renaming)).
     * @see #constructInstanceAndSubst(Formula)
     */
    override def constructInstanceAndSubst(f: Formula, axiom: Formula): Option[(Formula, Formula, Substitution,
        Option[PositionTactic], Option[PositionTactic])] = f match {
      case BoxModality(Assign(dv@Derivative(sort, v: Variable), t), p) => {
        // Construct the axiom substitution.
        val axiomV = Variable("v", None, sort)
        val axiomDV = Derivative(sort, axiomV)
        val axiomT = Apply(Function("t", None, Unit, sort), Nothing)
        val axiomP = Function("p", None, sort, Bool)
        // substitution in axiom = [v' := t;]p(v') <-> \forall v . (v'=t -> p(v'))
        val substitution = Substitution(
          List(
            new SubstitutionPair(axiomDV, dv),
            new SubstitutionPair(axiomT, t),
            new SubstitutionPair(ApplyPredicate(axiomP, CDot), SubstitutionHelper.replaceFree(p)(dv, CDot))
          )
        )

        //construct the RHS of the axiom instance: \forall v . (v'=t -> p(v'))
        val fv = TacticHelper.freshNamedSymbol(v, f)
        val dfv = Derivative(Real, fv)
        val g = Forall(Seq(fv), Imply(Equals(Real, dfv, t), replaceFree(p)(dv, dfv)))

        val axiomInstance = Equiv(f, g)

        // Construct the axiom, but ensure the naming is correct.
        def alpha(left: Boolean) = new PositionTactic("Alpha") {
          override def applies(s: Sequent, p: Position): Boolean = s(p) match {
            case Equiv(BoxModality(Assign(Derivative(_, _), _), _), Forall(_, _)) => true
            case _ => false
          }

          override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
              if(left)
                Some(alphaRenamingT(v.name, v.index, axiomV.name, axiomV.index)(p.first)
                  & alphaRenamingT(fv.name, fv.index, axiomV.name, axiomV.index)(p.second))
              else
                Some(alphaRenamingT(fv.name, fv.index, axiomV.name, axiomV.index)(p.second))

            override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
          }
        }

        val Equiv(left, right) = axiom
        val (alphaAxiom, cont) = {
          if (v.name == axiomV.name && v.index == axiomV.index)
            (Equiv(left, replaceFree(right)(axiomV, fv)), Some(alpha(left = false)))
          else (Equiv(replaceFree(left)(axiomV, v, None), replaceFree(right)(axiomV, fv)), Some(alpha(left = true)))
        }

        Some(alphaAxiom, axiomInstance, substitution, None, cont)
      }
    }
  }

  def boxAssignT: PositionTactic = boxAssignT(FOQuantifierTacticsImpl.skolemizeT)
  def boxAssignT(skolemizeHow: Boolean => PositionTactic): PositionTactic =
      new PositionTactic("[:=] assign equational") {
    override def applies(s: Sequent, p: Position): Boolean = p.inExpr == HereP && (s(p) match {
      case BoxModality(Assign(Variable(_, _, _), _), _) => true
      case _ => false
    })

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      def assignEqualMandatory(v: Variable, t: Term, rest: Formula) = Helper.names(t).contains(v) || (rest match {
        case BoxModality(_: ContEvolveProgram, _) => true
        case BoxModality(_: Loop, _) => true
        case _ => /* false requires substitution of variables */ true
      })

      import FOQuantifierTacticsImpl.skolemizeT
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
        case BoxModality(Assign(v: Variable, t: Term), phi) if assignEqualMandatory(v, t, phi) =>
          if (p.isAnte) Some(boxAssignEqualT(p))
          else Some(boxAssignEqualT(p) & skolemizeHow(true)(p) & ImplyRightT(p) & (v2vBoxAssignT(p) | NilT))
        case BoxModality(Assign(v: Variable, t: Term), phi) if !assignEqualMandatory(v, t, phi) => Some(v2tBoxAssignT(p))
        }
      }
  }

  def boxAssignEqualT: PositionTactic = new PositionTactic("[:=] assign equational") {
    override def applies(s: Sequent, p: Position): Boolean = p.inExpr == HereP && (s(p) match {
      case BoxModality(Assign(Variable(_, _,_), _), _) => true
      case _ => false
    })

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        import scala.language.postfixOps

        val f = node.sequent(p)
        // construct a new name for the quantified variable
        val (newV1, newV2) = f match {
          case BoxModality(Assign(v: Variable, _), _) =>
            val tIdx = TacticHelper.freshIndexInFormula(v.name, f)
            (Variable(v.name, tIdx, v.sort), Variable(v.name, Some(tIdx.get + 1), v.sort))
          case _ => throw new IllegalStateException("Checked by applies to never happen")
        }

        node.sequent(p) match {
          case BoxModality(Assign(v: Variable, _), BoxModality(prg: Loop, _))
            if Helper.names(prg).contains(v) && !Substitution.freeVariables(prg).contains(v) => Some(
            alphaRenamingT(v.name, v.index, newV1.name, newV1.index)(p.second) &
              boxAssignWithoutAlphaT(newV2, checkNewV = false)(p)
          )
          case BoxModality(Assign(v: Variable, _), BoxModality(prg: ContEvolveProgram, _))
            if Helper.names(prg).contains(v) && !Substitution.freeVariables(prg).contains(v) => Some(
            alphaRenamingT(v.name, v.index, newV1.name, newV1.index)(p.second) &
              boxAssignWithoutAlphaT(newV2, checkNewV = false)(p)
          )
          case _ => Some(boxAssignWithoutAlphaT(newV1)(p))
        }
      }
    }
  }

  /**
   * Creates a new axiom tactic for box assignment [x := t;]
   * @return The axiom tactic.
   */
  private def boxAssignWithoutAlphaT(newV: Variable, checkNewV: Boolean = true): PositionTactic =
      new AxiomTactic("[:=] assign equational", "[:=] assign equational") {
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(Assign(Variable(_, _,_), _), _) => !checkNewV || !Helper.names(f).contains(newV)
      case _ => false
    }

    override def constructInstanceAndSubst(f: Formula, axiom: Formula):
        Option[(Formula, Formula, Substitution, Option[PositionTactic], Option[PositionTactic])] = f match {
      case BoxModality(Assign(v: Variable, t), p) =>
        // TODO check that axiom is of the expected form [v:=t]p(v) <-> \forall v_tIdx . (v_tIdx=t -> p(v_tIdx))
        // construct substitution
        val aT = Apply(Function("t", None, Unit, Real), Nothing)
        val aP = Function("p", None, Real, Bool)
        val l = List(SubstitutionPair(aT, t),
          SubstitutionPair(ApplyPredicate(aP, CDot), SubstitutionHelper.replaceFree(p)(v, CDot)))

        // construct axiom instance: [v:=t]p(v) <-> \forall v_tIdx . (v_tIdx=t -> p(v_tIdx))
        val g = Forall(Seq(newV), Imply(Equals(Real, newV,t), SubstitutionHelper.replaceFree(p)(v, newV)))
        val axiomInstance = Equiv(f, g)

        // rename to match axiom if necessary
        val aV = Variable("v", None, Real)
        def alpha(left: Boolean) = new PositionTactic("Alpha") {
          override def applies(s: Sequent, p: Position): Boolean = s(p) match {
            case Equiv(BoxModality(Assign(_, _), _), Forall(_, _)) => true
            case _ => false
          }

          override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
              if(left)
                Some(globalAlphaRenamingT(v.name, v.index, aV.name, aV.index)
                  & alphaRenamingT(newV.name, newV.index, aV.name, aV.index)(p.second))
              else
                Some(alphaRenamingT(newV.name, newV.index, aV.name, aV.index)(p.second))

            override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
          }
        }
        val Equiv(left, right) = axiom
        val (ax, cont) =
          if (v.name == aV.name && v.index == aV.index)
            (Equiv(left, replaceFree(right)(aV, newV)), Some(alpha(left = false)))
          else (Equiv(replaceFree(left)(aV, v, None), replaceFree(right)(aV, newV)), Some(alpha(left = true)))

        // return tactic
        Some(ax, axiomInstance, Substitution(l), None, cont)
      case _ => None
    }
  }

  /**
   * Creates a new axiom tactic for reversing box assignment [v := t;], i.e., introduces a ghost v for term t
   * @return The axiom tactic.
   */
  def discreteGhostT(ghost: Option[Variable], t: Term): PositionTactic = new AxiomTactic("[:=] assign", "[:=] assign") {
    override def applies(f: Formula): Boolean = true

    override def constructInstanceAndSubst(f: Formula, axiom: Formula):
        Option[(Formula, Formula, Substitution, Option[PositionTactic], Option[PositionTactic])] = {
      // TODO check that axiom is of the expected form [v:=t]p(v) <-> p(t)
      // construct substitution
      val aT = Apply(Function("t", None, Unit, Real), Nothing)
      val aP = Function("p", None, Real, Bool)
      val l = List(new SubstitutionPair(aT, t),
        new SubstitutionPair(ApplyPredicate(aP, CDot), SubstitutionHelper.replaceFree(f)(t, CDot)))

      // check specified name, or construct a new name for the ghost variable if None
      val v = ghost match {
        case Some(gv) => require(gv == t || (!Helper.names(f).contains(gv))); gv
        case None => t match {
          case v: Variable => TacticHelper.freshNamedSymbol(v, f)
          case _ => throw new IllegalArgumentException("Only variables allowed when ghost name should be auto-provided")
        }
      }

      // construct axiom instance: [v:=t]p(v) <-> p(t)
      val aV = Variable("v", None, Real)
      val g = BoxModality(Assign(v, t), SubstitutionHelper.replaceFree(f)(t, v))
      val axiomInstance = Equiv(g, f)

      // rename to match axiom if necessary
      val alpha = new PositionTactic("Alpha") {
        override def applies(s: Sequent, p: Position): Boolean = s(p) match {
          case Equiv(BoxModality(Assign(_, _), _), _) => true
          case _ => false
        }

        override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
          override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
              Some(globalAlphaRenamingT(v.name, v.index, aV.name, aV.index))

          override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
        }
      }
      val Equiv(left, right) = axiom
      val (ax, cont) = (Equiv(replace(left)(aV, v), right), Some(alpha))

      // return tactic
      Some(ax, axiomInstance, Substitution(l), None, cont)
    }
  }

  /**
   * Creates a new axiom tactic for reversing box assignment [v := t;], i.e., introduces a ghost v for term t
   * @return The axiom tactic.
   */
  def nonAbbrvDiscreteGhostT(ghost: Option[Variable], t: Term): PositionTactic = new AxiomTactic("[:=] vacuous assign", "[:=] vacuous assign") {
    override def applies(f: Formula): Boolean = true

    override def constructInstanceAndSubst(f: Formula, axiom: Formula):
    Option[(Formula, Formula, Substitution, Option[PositionTactic], Option[PositionTactic])] = {
      // TODO check that axiom is of the expected form [v:=t]p <-> p
      // construct substitution
      val aT = Apply(Function("t", None, Unit, Real), Nothing)
      val aP = ApplyPredicate(Function("p", None, Unit, Bool), Nothing)
      val l = List(new SubstitutionPair(aT, t), new SubstitutionPair(aP, f))

      // check specified name, or construct a new name for the ghost variable if None
      val v = ghost match {
        case Some(gv) => require(gv == t || (!Helper.names(f).contains(gv))); gv
        case None => t match {
          case v: Variable => TacticHelper.freshNamedSymbol(v, f)
          case _ => throw new IllegalArgumentException("Only variables allowed when ghost name should be auto-provided")
        }
      }

      // construct axiom instance: [v:=t]p(v) <-> p(t)
      val g = BoxModality(Assign(v, t), f)
      val axiomInstance = Equiv(g, f)

      // rename to match axiom if necessary
      val aV = Variable("v", None, Real)
      val alpha = new PositionTactic("Alpha") {
        override def applies(s: Sequent, p: Position): Boolean = s(p) match {
          case Equiv(BoxModality(Assign(_, _), _), _) => true
          case _ => false
        }

        override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
          override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
            Some(globalAlphaRenamingT(v.name, v.index, aV.name, aV.index))

          override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
        }
      }
      val Equiv(left, right) = axiom
      val (ax, cont) = (Equiv(replace(left)(aV, v), right), Some(alpha))

      // return tactic
      Some(ax, axiomInstance, Substitution(l), None, cont)
    }
  }

  /**
   * Creates a new position tactic for box assignment [x := t;], for the case when followed by ODE or loop.
   * Alpha renaming in ODEs and loops introduces initial value assignments. This tactic is designed to handle those.
   * @return The tactic.
   */
  def v2vBoxAssignT: PositionTactic = new PositionTactic("[:=] assign") {
    override def applies(s: Sequent, p: Position): Boolean = s(p) match {
      case BoxModality(Assign(_: Variable, v: Variable), pred) => pred match {
        case BoxModality(_: ContEvolveProgram, _) => !Helper.certainlyFreeNames(pred).contains(v)
        case BoxModality(_: Loop, _) => !Helper.certainlyFreeNames(pred).contains(v)
        case DiamondModality(_: ContEvolveProgram, _) => !Helper.certainlyFreeNames(pred).contains(v)
        case DiamondModality(_: Loop, _) => !Helper.certainlyFreeNames(pred).contains(v)
        // prevent application on anything else. otherwise, boxAssignT has the surprising effect of handling multiple
        // assignments at once
        case _ => false
      }
      case _ => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        import scala.language.postfixOps
        import SearchTacticsImpl.onBranch
        import BranchLabels.cutShowLbl
        import PropositionalTacticsImpl.EquivRightT

        val succLength = node.sequent.succ.length
        val anteLength = node.sequent.ante.length

        node.sequent(p) match {
          case b@BoxModality(Assign(v: Variable, t: Variable), pred) => Some(
            cutT(Some(Equiv(b, replace(pred)(v, t)))) &
              onBranch(
                (cutShowLbl,
                  alphaRenamingT(t.name, t.index, v.name, v.index)(SuccPosition(succLength, PosInExpr(1 :: Nil))) &
                    EquivRightT(SuccPosition(succLength)) &
                    AxiomCloseT(AntePosition(anteLength), SuccPosition(succLength))),
                (cutUseLbl, equalityRewriting(AntePosition(anteLength), p) &
                  hideT(AntePosition(anteLength)) & hideT(p))
              )
          )
        }
      }
    }
  }

  /**
   * Creates a new tactic for box assignment [x := t;] when x == t.
   * @return The tactic.
   */
  def selfAssignmentT: PositionTactic = new PositionTactic("[:=] self-assign") {
    override def applies(s: Sequent, p: Position): Boolean = s(p) match {
      case BoxModality(Assign(v: Variable, t: Variable), _) => v == t
      case _ => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
          case b@BoxModality(Assign(v: Variable, t: Variable), _) if v == t => Some(
            abstractionT(p) & hideT(p) & skolemizeT(p))
          case _ => throw new IllegalArgumentException("Checked by applicable to not happen")
      }
    }
  }

  /**
   * Creates a new axiom tactic for box assignment [x := t;]
   * @return The axiom tactic.
   */
  protected[tactics] def v2tBoxAssignT: PositionTactic = new AxiomTactic("[:=] assign", "[:=] assign") {
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(Assign(v: Variable, t: Term), pred) => pred match {
        // loop and ODE are probably a little too strict, but we have v2vBoxAssignT to handle those
        case BoxModality(_: ContEvolveProgram, _) => t match { case tv: Variable => v == tv case _ => false }
        case BoxModality(_: Loop, _) => t match { case tv: Variable => v == tv case _ => false }
        case DiamondModality(_: ContEvolveProgram, _) => t match { case tv: Variable => v == tv case _ => false }
        case DiamondModality(_: Loop, _) => t match { case tv: Variable => v == tv case _ => false }
        case _ => t match {
          case tv: Variable => true
          case _ => !Helper.names(t).contains(v)
        }
      }
      case _ => false
    }

    override def constructInstanceAndSubst(f: Formula, axiom: Formula):
    Option[(Formula, Formula, Substitution, Option[PositionTactic], Option[PositionTactic])] = f match {
      case BoxModality(Assign(v: Variable, t: Term), p) =>
        // TODO check that axiom is of the expected form [v:=t]p(v) <-> p(t))
        // construct substitution
        val aT = Apply(Function("t", None, Unit, Real), Nothing)
        val aP = Function("p", None, Real, Bool)
        val l = List(SubstitutionPair(aT, t), SubstitutionPair(ApplyPredicate(aP, CDot),
          SubstitutionHelper.replaceFree(p)(v, CDot)))

        // construct axiom instance: [v:=t]p(v) <-> p(t)
        val g = SubstitutionHelper.replaceFree(p)(v, t)
        val axiomInstance = Equiv(f, g)

        // rename to match axiom if necessary
        val aV = Variable("v", None, Real)
        def alpha = new PositionTactic("Alpha") {
          override def applies(s: Sequent, p: Position): Boolean = s(p) match {
            case Equiv(BoxModality(Assign(_, _), _), _) => true
            case _ => false
          }

          override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
              Some(alphaRenamingT(v.name, v.index, aV.name, aV.index)(p) ~ globalAlphaRenamingT(v.name, v.index, aV.name, aV.index))

            override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
          }
        }

        val Equiv(lhs, rhs) = axiom
        val (ax, cont) = {
          val lhsrepl = if (v.name != aV.name || v.index != None) replace(lhs)(aV, v) else lhs
          val thealpha = if (v.name != aV.name || v.index != None) Some(alpha) else None

          (Equiv(lhsrepl, rhs), thealpha)
        }

        // return tactic
        Some(ax, axiomInstance, Substitution(l), None, cont)
      case _ => None
    }
  }

  /**
   * Creates a new axiom tactic for test [?H].
   * @return The new tactic.
   */
  protected[tactics] def boxTestT: PositionTactic = new AxiomTactic("[?] test", "[?] test") {
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(Test(_), _) => true
      case _ => false
    }

    override def constructInstanceAndSubst(f: Formula): Option[(Formula, Substitution)] = f match {
      case BoxModality(Test(h), p) =>
        // construct substitution
        val aH = ApplyPredicate(Function("H", None, Unit, Bool), Nothing)
        val aP = ApplyPredicate(Function("p", None, Unit, Bool), Nothing)
        val l = List(new SubstitutionPair(aH, h), new SubstitutionPair(aP, p))
        // construct axiom instance: [?H]p <-> (H -> p).
        val g = Imply(h, p)
        val axiomInstance = Equiv(f, g)
        Some(axiomInstance, Substitution(l))
      case _ => None
    }
  }

  def boxNDetAssign: PositionTactic = new PositionTactic("[:=] assign equational") {
    override def applies(s: Sequent, p: Position): Boolean = !p.isAnte && p.inExpr == HereP && (s(p) match {
      case BoxModality(NDetAssign(v: Variable), _) => true
      case _ => false
    })

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = {
        import scala.language.postfixOps
        import FOQuantifierTacticsImpl.skolemizeT

        val f = node.sequent(p)
        // construct a new name for renaming in ODE
        val newV = f match {
          case BoxModality(NDetAssign(v: Variable), _) => TacticHelper.freshNamedSymbol(v, f)
          case _ => throw new IllegalStateException("Checked by applies to never happen")
        }

        node.sequent(p) match {
          case BoxModality(NDetAssign(v: Variable), BoxModality(prg: Loop, _))
            if BindingAssessment.catVars(prg).bv.contains(v) => Some(
            alphaRenamingT(v.name, v.index, newV.name, newV.index)(p.second) &
              boxNDetAssignWithoutAlpha(p) & skolemizeT(p) & v2vBoxAssignT(p)
          )
          case BoxModality(NDetAssign(v: Variable), BoxModality(prg: ContEvolveProgram, _))
            if BindingAssessment.catVars(prg).bv.contains(v) => Some(
            alphaRenamingT(v.name, v.index, newV.name, newV.index)(p.second) &
              boxNDetAssignWithoutAlpha(p) & skolemizeT(p) & v2vBoxAssignT(p)
          )
          case _ => Some(boxNDetAssignWithoutAlpha(p) & skolemizeT(p))
        }
      }
    }
  }

  /**
   * Creates a new axiom tactic for non-deterministic assignment [x := *].
   * @return The new tactic.
   */
  private def boxNDetAssignWithoutAlpha: PositionTactic = new AxiomTactic("[:*] assign nondet", "[:*] assign nondet") {
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(NDetAssign(_), _) => true
      case _ => false
    }
    
    override def constructInstanceAndSubst(f: Formula, axiom: Formula): Option[(Formula, Formula, Substitution,
        Option[PositionTactic], Option[PositionTactic])] = f match {
      case BoxModality(NDetAssign(x), p) if Variable.unapply(x).isDefined =>
        val v = x.asInstanceOf[Variable]
        // construct substitution
        val aP = Function("p", None, Real, Bool)
        val l = List(new SubstitutionPair(ApplyPredicate(aP, CDot), SubstitutionHelper.replaceFree(p)(x, CDot)))
        // construct axiom instance: [v:=*]p(v) <-> \forall v. p(v).
        val g = Forall(Seq(v), p)
        val axiomInstance = Equiv(f, g)

        // alpha renaming
        val aV = Variable("v", None, Real)
        val alpha = new PositionTactic("Alpha") {
          override def applies(s: Sequent, p: Position): Boolean = s(p) match {
            case Equiv(BoxModality(NDetAssign(_), _), Forall(_, _)) => true
            case _ => false
          }

          override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
              Some(globalAlphaRenamingT(v.name, v.index, aV.name, aV.index))

            override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
          }
        }
        // rename to match axiom if necessary
        val (ax, cont) =
          if (v.name != aV.name || v.index != None) (replaceFree(axiom)(aV, v, None), Some(alpha))
          else (axiom, None)
        Some(ax, axiomInstance, Substitution(l), None, cont)
      case _ => None
    }
  }

  /**
   * Creates a new axiom tactic for sequential composition [;]
   * @return The new tactic.
   */
  protected[tactics] def boxSeqT: PositionTactic = new AxiomTactic("[;] compose", "[;] compose") {
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(Sequence(_, _), _) => true
      case _ => false
    }

    override def constructInstanceAndSubst(f: Formula): Option[(Formula, Substitution)] = f match {
      case BoxModality(Sequence(a, b), p) =>
        // construct substitution
        val aA = ProgramConstant("a")
        val aB = ProgramConstant("b")
        val aP = ApplyPredicate(Function("p", None, Real, Bool), Anything)
        val l = List(SubstitutionPair(aA, a), SubstitutionPair(aB, b), SubstitutionPair(aP, p))
        // construct axiom instance: [ a; b ]p <-> [a][b]p.
        val g = BoxModality(a, BoxModality(b, p))
        val axiomInstance = Equiv(f, g)
        Some(axiomInstance, Substitution(l))
      case _ => None
    }

  }

  /**
   * Creates a new axiom tactic for box induction [*] I induction
   * @return The new tactic.
   */
  protected[tactics] def boxInductionT: PositionTactic = new AxiomTactic("I induction", "I induction") {
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(Loop(_), _) => true
      case _ => false
    }

    override def constructInstanceAndSubst(f: Formula): Option[(Formula, Substitution)] = f match {
      case BoxModality(Loop(a), p) =>
        // construct substitution
        val aA = ProgramConstant("a")
        val aP = ApplyPredicate(Function("p", None, Real, Bool), Anything)
        val l = List(SubstitutionPair(aA, a), SubstitutionPair(aP, p))
        // construct axiom instance: (p & [a*](p -> [a] p)) -> [a*]p
        val g = And(p, BoxModality(Loop(a), Imply(p, BoxModality(a, p))))
        val axiomInstance = Imply(g, f)
        Some(axiomInstance, Substitution(l))
      case _ => None
    }

  }

  /**
   * Creates a new axiom tactic for box choice [++].
   * @return The new tactic.
   */
  def boxChoiceT: PositionTactic = new AxiomTactic("[++] choice", "[++] choice") {
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(Choice(_, _), _) => true
      case _ => false
    }

    override def constructInstanceAndSubst(f: Formula): Option[(Formula, Substitution)] = f match {
      case BoxModality(Choice(a, b), p) =>
        // construct substitution
        val aA = ProgramConstant("a")
        val aB = ProgramConstant("b")
        val aP = ApplyPredicate(Function("p", None, Real, Bool), Anything)
        val l = List(new SubstitutionPair(aA, a), new SubstitutionPair(aB, b), new SubstitutionPair(aP, p))
        // construct axiom instance: [ a ++ b ]p <-> [a]p & [b]p.
        val g = And(BoxModality(a, p), BoxModality(b, p))
        val axiomInstance = Equiv(f, g)
        Some(axiomInstance, Substitution(l))
      case _ => None
    }
  }

  /**
   * Creates a new position tactic to apply the induction rule.
   * @param inv The invariant.
   * @return The position tactic.
   */
  protected[tactics] def inductionT(inv: Option[Formula]): PositionTactic = new PositionTactic("induction") {
    def getBody(g: Formula): Option[Program] = g match {
      case BoxModality(Loop(a), _) => Some(a)
      case _ => None
    }
    override def applies(s: Sequent, p: Position): Boolean = !p.isAnte && p.inExpr == HereP && getBody(s(p)).isDefined

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      def ind(cutSPos: Position, cont: Tactic) = boxInductionT(cutSPos) & AndRightT(cutSPos) &
        (LabelBranch("Close Next"), abstractionT(cutSPos) & hideT(cutSPos) & cont)

      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = inv match {
        case Some(f) =>
          val cutAPos = AntePosition(node.sequent.ante.length, HereP)
          val prepareKMP = new ConstructionTactic("Prepare K modus ponens") {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
              case x@BoxModality(a, _) =>
                val cPos = AntePosition(node.sequent.ante.length)
                val b1 = ImplyLeftT(cPos) & AxiomCloseT
                val b2 = hideT(p)
                Some(cutT(Some(Imply(BoxModality(a, f), x))) & onBranch((cutUseLbl, b1), (cutShowLbl, b2)))
              case _ => None
            }
            override def applicable(node: ProofNode): Boolean = true
          }
          val cutSPos = SuccPosition(node.sequent.succ.length - 1, HereP)
          val useCase = prepareKMP & hideT(cutAPos) & kModalModusPonensT(cutSPos) & abstractionT(cutSPos) &
            hideT(cutSPos) & LabelBranch(indUseCaseLbl)
          val branch1Tactic = ImplyLeftT(cutAPos) & (hideT(p) & LabelBranch(indInitLbl), useCase)
          val branch2Tactic = hideT(p) &
            ImplyRightT(cutSPos) &
            ind(cutSPos, hideT(cutAPos) & LabelBranch(indStepLbl)) &
            onBranch(("Close Next", AxiomCloseT))
          getBody(node.sequent(p)) match {
            case Some(a) =>
              Some(cutT(Some(Imply(f, BoxModality(Loop(a), f)))) &
                onBranch((cutUseLbl, branch1Tactic), (cutShowLbl, branch2Tactic)))
            case None => None
          }
        case None => Some(ind(p, NilT) & LabelBranch(indStepLbl))
      }
    }
  }

  /**
   * Creates a new position tactic to apply the induction rule. Wipes the context instead of abstraction.
   * @param inv The invariant.
   * @return The new position tactic.
   */
  def wipeContextInductionT(inv: Option[Formula]): PositionTactic = new PositionTactic("induction") {
    def getBody(g: Formula): Option[Program] = g match {
      case BoxModality(Loop(a), _) => Some(a)
      case _ => None
    }
    override def applies(s: Sequent, p: Position): Boolean = !p.isAnte && p.inExpr == HereP && getBody(s(p)).isDefined

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)

      def wipeContext(bvFromPos: Position, except: Position*) = new ConstructionTactic("Wipe Context") {
        require(!bvFromPos.isAnte)
        override def applicable(node: ProofNode) = true
        override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(bvFromPos) match {
          case Forall(vars, _) =>
            val anteExcepts = except.filter(_.isInstanceOf[AntePosition]).map(_.index).toSet
            val anteHidePos = node.sequent.ante.zipWithIndex.collect {
              case (f,i) if Helper.names(f).intersect(vars.toSet).nonEmpty => i }.toSet -- anteExcepts
            val anteHides = anteHidePos.toList.sorted.reverse.map(i => hideT(AntePosition(i)))
            val succExcepts = except.filter(_.isInstanceOf[SuccPosition]).map(_.index).toSet
            val succHidePos = node.sequent.succ.zipWithIndex.collect {
              case (f,i) if Helper.names(f).intersect(vars.toSet).nonEmpty => i }.toSet -- succExcepts
            val succHides = succHidePos.toList.sorted.reverse.map(i => hideT(SuccPosition(i)))
            val bvFromPosCorr = succHidePos.count(_ < bvFromPos.index)
            Some((anteHides ++ succHides).foldLeft(NilT)((t, i) => t & i) &
              skolemizeT(SuccPosition(bvFromPos.index - bvFromPosCorr)))
        }
      }

      def ind(cutSPos: Position, cont: Tactic) = boxInductionT(cutSPos) & AndRightT(cutSPos) &
        (LabelBranch("Close Next"), abstractionT(cutSPos) & hideT(cutSPos) & wipeContext(cutSPos, cutSPos) & cont)
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = inv match {
        case Some(f) =>
          val cutAPos = AntePosition(node.sequent.ante.length)
          val cutSPos = SuccPosition(node.sequent.succ.length - 1)

          val prepareKMP = new ConstructionTactic("Prepare K modus ponens") {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = node.sequent(p) match {
              case x@BoxModality(a, _) =>
                val cPos = AntePosition(node.sequent.ante.length)
                val b1 = ImplyLeftT(cPos) & AxiomCloseT
                val b2 = hideT(p)
                Some(cutT(Some(Imply(BoxModality(a, f), x))) & onBranch((cutUseLbl, b1), (cutShowLbl, b2)))
              case _ => None
            }
            override def applicable(node: ProofNode): Boolean = true
          }

          val useCase = prepareKMP & hideT(cutAPos) & kModalModusPonensT(cutSPos) & abstractionT(cutSPos) &
            hideT(cutSPos) & wipeContext(cutSPos, cutSPos) & LabelBranch(indUseCaseLbl)
          val branch1Tactic = ImplyLeftT(cutAPos) & (hideT(p) & LabelBranch(indInitLbl), useCase)
          val branch2Tactic = hideT(p) &
            ImplyRightT(cutSPos) &
            ind(cutSPos, LabelBranch(indStepLbl)) &
            onBranch(("Close Next", AxiomCloseT))
          getBody(node.sequent(p)) match {
            case Some(a) =>
              Some(cutT(Some(Imply(f, BoxModality(Loop(a), f)))) & onBranch((cutUseLbl, branch1Tactic), (cutShowLbl, branch2Tactic)))
            case None => None
          }
        case None => Some(ind(p, NilT) & LabelBranch(indStepLbl))
      }
    }
  }

  /**
   * Induction tactic that generates an invariant using the specified generator.
   * @param gen The invariant generator.
   * @return The induction tactic.
   */
  protected[tactics] def genInductionT(gen: Generator[Formula]): PositionTactic = new PositionTactic("Generate Invariant") {
    override def applies(s: Sequent, p: Position): Boolean = gen.peek(s, p) match {
      case Some(inv) => wipeContextInductionT(Some(inv)).applies(s, p)
      case None => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = gen(node.sequent, p) match {
        case Some(inv) => Some(wipeContextInductionT(Some(inv))(p))
        case None => None
      }

      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
    }
  }

  /*********************************************
   * Rule Tactics
   *********************************************/

  /**
   * Creates a new position tactic for the derivative assignment rule.
   * @return The assignment rule tactic.
   */
  protected[tactics] def derivativeAssignment = new PositionTactic("Assignment") {
    import FOQuantifierTacticsImpl.uniquify
    // for now only on top level
    override def applies(s: Sequent, p: Position): Boolean = {
      (p.inExpr == HereP) && ((if (p.isAnte) s.ante else s.succ)(p.index) match {
        case BoxModality(Assign(Derivative(_,Variable(_, _, _)), _), _) => true
        case DiamondModality(Assign(Derivative(_, Variable(_, _, _)), _), _) => true
        case _ => false
      })
    }

    override def apply(p: Position): Tactic = Tactics.weakSeqT(uniquify(p), new ApplyRule(new DerivativeAssignmentRule(p)) {
      override def applicable(n: ProofNode): Boolean = applies(n.sequent, p)
    })
  }

}
