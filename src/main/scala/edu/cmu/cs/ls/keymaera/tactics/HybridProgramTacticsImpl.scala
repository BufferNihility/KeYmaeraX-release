package edu.cmu.cs.ls.keymaera.tactics

import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.tactics.BranchLabels._
import edu.cmu.cs.ls.keymaera.tactics.EqualityRewritingImpl._
import edu.cmu.cs.ls.keymaera.tactics.EqualityRewritingImpl.equalityRewriting
import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl.AndRightT
import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl.AxiomCloseT
import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl.ImplyLeftT
import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl.ImplyRightT
import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl.cutT
import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl.hideT
import edu.cmu.cs.ls.keymaera.tactics.Tactics._

import edu.cmu.cs.ls.keymaera.tactics.PropositionalTacticsImpl._
import edu.cmu.cs.ls.keymaera.tactics.TacticLibrary.{alphaRenamingT,abstractionT}
import SearchTacticsImpl.{locateSucc,locateAnte,onBranch}

import scala.collection.immutable.{Set, List, Seq}

import AlphaConversionHelper._

/**
 * Implementation of tactics for handling hybrid programs.
 */
object HybridProgramTacticsImpl {

  /*********************************************
   * Axiom Tactics
   *********************************************/

  /**
   * Creates a new axiom tactic for differential box assignment [x := t;]
   * @author Nathan Fulton
   * @return The axiom tactic.
   *
   */
  protected[tactics] def boxDerivativeAssignT: PositionTactic = new AxiomTactic("[':=] derivative assignment equal", "[':=] derivative assignment equal") {
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(Assign(Derivative(_,Variable(_)),_),_) => true
      case Modality(Assign(Derivative(Variable(_), _), _),_) => true //@todo Why do we need to match on both so often?
      case _ => throw new Exception("wtf?")
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
    override def constructInstanceAndSubst(f: Formula, axiom: Formula): Option[(Formula, Formula, Substitution, Option[PositionTactic])] = f match {
      case BoxModality(Assign(Derivative(Real, v: Variable), t), p) => {
        // Construct the axiom substitution.
        val axiomV = Variable("v", None, Real)
        val axiomDV = Derivative(Real, axiomV)
        val axiomT = Variable("t", None, Real)
        val axiomP = Function("p", None, Real, Bool)
        // substitution in axiom = [v' := t;]p(v') <-> \forall v . (v'=t -> p(v'))
        val substitution = Substitution(
          List(
            new SubstitutionPair(axiomDV, Derivative(Real, v)), //@todo why is this not included in the other assignment axiom?
            new SubstitutionPair(axiomV, v), //@todo why is this not included in the other assignment axiom?
            new SubstitutionPair(axiomT, t),
            // TODO replace with new CDot notation, probably: new SubstitutionPair(ApplyPredicate(axiomP, CDot), replace(p)(axiomDV, CDot))
            new SubstitutionPair(ApplyPredicate(axiomP, axiomDV), p) //used to be just apply(..,axiomDV) and p.
          )
        )

        //construct the RHS of the axiom instance: \forall v . (v'=t -> p(v'))
        val fv = Variable(v.name, getFreshIndex(v,f), v.sort) //fresh variable.
        val dfv = Derivative(Real, fv)
        val g = Forall(Seq(fv), Imply(Equals(Real, dfv, t), termReplace(p)(Derivative(Real, v), dfv)))

        val axiomInstance = Equiv(f, g)

        // Construct the axiom, but ensure the naming is correct.
//        val Equiv(left, right) = axiom
//        val (alphaAxiom, cont) = {
//          if (v.name == "v" && v.index == None) {
//            (Equiv(left, replace(right)(axiomV, xx)), Some(alpha(left = false)))
//          }
//          else {
//            (Equiv(replace(lef)(aX, x), replace(right)(aX, xx)), Some(alpha(left = true)))
//          }
//        }


        Some(axiom, axiomInstance, substitution, None) //?



//        val g = Forall(Seq(Derivative(v)),)
//        val axiomInstance =


      }
    }

    /**
     * Replaces old with new in target, where old and new can be terms.
     * @param target
     * @param oldTerm
     * @param newTerm
     * @return [new / old] target
     */
    private def termReplace(target: Formula)(oldTerm: Term, newTerm: Term) = {
      ???
    }

    /**
     * returns an index value which is not used by variables named ``v.name"
     * @param v the variable whose name we care about.
     * @param f the formula in which the new variable v_newIndex must be fresh.
     * @return newIndex a new index.
     */
    private def getFreshIndex(v : Variable, f : Formula):Option[Int] = {
      val vars = Helper.names(f).map(n => (n.name, n.index)).filter(_._1 == v.name)
      require(vars.size > 0)
      val maxIdx: Option[Int] = vars.map(_._2).foldLeft(None: Option[Int])((acc: Option[Int], i: Option[Int]) => acc match {
        case Some(a) => i match {
          case Some(b) => if (a < b) Some(b) else Some(a)
          case None => Some(a)
        }
        case None => i
      })
      val tIdx: Option[Int] = maxIdx match {
        case None => Some(0)
        case Some(a) => Some(a + 1)
      }

      tIdx
    }
  }

  /**
   * Creates a new axiom tactic for box assignment [x := t;]
   * @return The axiom tactic.
   */
  protected[tactics] def boxAssignT: PositionTactic = new AxiomTactic("[:=] assignment equal", "[:=] assignment equal") {
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(Assign(Variable(_, _,_), _), _) => true
      case _ => false
    }

    override def constructInstanceAndSubst(f: Formula, axiom: Formula):
        Option[(Formula, Formula, Substitution, Option[PositionTactic])] = f match {
      case BoxModality(Assign(v: Variable, t), p) =>
        // TODO check that axiom is of the expected form [v:=t]p(v) <-> \forall v_tIdx . (v_tIdx=t -> p(v_tIdx))
        // construct substitution
        val aV = Variable("v", None, Real)
        val aT = Variable("t", None, Real)
        val aP = Function("p", None, Real, Bool)
        val l = List(new SubstitutionPair(aT, t), new SubstitutionPair(ApplyPredicate(aP, v), p))

        // construct a new name for the quantified variable
        val vars = Helper.names(f).map(n => (n.name, n.index)).filter(_._1 == v.name)
        require(vars.size > 0)
        val maxIdx: Option[Int] = vars.map(_._2).foldLeft(None: Option[Int])((acc: Option[Int], i: Option[Int]) =>
            acc match {
          case Some(a) => i match {
            case Some(b) => if (a < b) Some(b) else Some(a)
            case None => Some(a)
          }
          case None => i
        })
        val tIdx: Option[Int] = maxIdx match {
          case None => Some(0)
          case Some(a) => Some(a + 1)
        }
        val newV = Variable(v.name, tIdx, v.sort)

        // construct axiom instance: [v:=t]p(v) <-> \forall v_tIdx . (v_tIdx=t -> p(v_tIdx))
        val g = Forall(Seq(newV), Imply(Equals(Real, newV,t), replace(p)(v, newV)))
        val axiomInstance = Equiv(f, g)

        // rename to match axiom if necessary
        def alpha(left: Boolean) = new PositionTactic("Alpha") {
          override def applies(s: Sequent, p: Position): Boolean = s(p) match {
            case Equiv(BoxModality(Assign(_, _), _), Forall(_, _)) => true
            case _ => false
          }

          override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
              if(left)
                Some(alphaRenamingT(v.name, v.index, aV.name, None)(p.first)
                  & alphaRenamingT(newV.name, newV.index, aV.name, None)(p.second))
              else
                Some(alphaRenamingT(newV.name, newV.index, aV.name, None)(p.second))

            override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
          }
        }
        val Equiv(left, right) = axiom
        val (ax, cont) =
          if (v.name == aV.name && v.index == None)
            (Equiv(left, replaceFree(right)(aV, newV)), Some(alpha(left = false)))
          else (Equiv(replaceFree(left)(aV, v, None), replaceFree(right)(aV, newV)), Some(alpha(left = true)))

        // return tactic
        Some(ax, axiomInstance, Substitution(l), cont)
      case _ => None
    }
  }

  /**
   * Creates a new axiom tactic for reversing box assignment [v := t;], i.e., introduces a ghost v for term t
   * @return The axiom tactic.
   */
  protected[tactics] def discreteGhostT(ghost: Option[Variable], t: Term): PositionTactic = new AxiomTactic("[:=] assignment", "[:=] assignment") {
    override def applies(f: Formula): Boolean = true

    override def constructInstanceAndSubst(f: Formula, axiom: Formula):
        Option[(Formula, Formula, Substitution, Option[PositionTactic])] = {
      // TODO check that axiom is of the expected form [v:=t]p(v) <-> p(t)
      // construct substitution
      val aV = Variable("v", None, Real)
      val aT = Variable("t", None, Real)
      val aP = Function("p", None, Real, Bool)
      val l = List(new SubstitutionPair(aT, t), new SubstitutionPair(ApplyPredicate(aP, CDot),
        replaceFree(f)(t, CDot)))

      // check specified name, or construct a new name for the ghost variable if None
      val v = ghost match {
        case Some(gv) => require(gv == t || (!Helper.names(f).contains(gv))); gv
        case None => t match {
          case Variable(tname, _, _) => val vars = Helper.names(f).map(n => (n.name, n.index)).filter(_._1 == tname)
            require(vars.size > 0)
            val maxIdx: Option[Int] = vars.map(_._2).foldLeft(None: Option[Int])((acc: Option[Int], i: Option[Int]) =>
              acc match {
                case Some(a) => i match {
                  case Some(b) => if (a < b) Some(b) else Some(a)
                  case None => Some(a)
                }
                case None => i
              })
            val tIdx: Option[Int] = maxIdx match {
              case None => Some(0)
              case Some(a) => Some(a + 1)
            }
            Variable(tname, tIdx, t.sort)
          case _ => throw new IllegalArgumentException("Only variables allowed when ghost name should be auto-provided")
        }
      }

      // construct axiom instance: [v:=t]p(v) <-> p(t)
      val g = BoxModality(Assign(v, t), replaceFree(f)(t, v))
      val axiomInstance = Equiv(g, f)

      // rename to match axiom if necessary
      val alpha = new PositionTactic("Alpha") {
        override def applies(s: Sequent, p: Position): Boolean = s(p) match {
          case Equiv(BoxModality(Assign(_, _), _), _) => true
          case _ => false
        }

        def alphaRenameTerm(p: Position) = t match {
          case Variable(tname, tindex, _) => alphaRenamingT(tname, tindex, aT.name, None)(p.second)
          // TODO need to setup axiom apply tactic the other way around to avoid replacing t with aT
          case _ => ???
        }

        override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
          override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
              Some(alphaRenamingT(v.name, v.index, aV.name, None)(p.first)
                & alphaRenameTerm(p))

          override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
        }
      }
      val Equiv(left, right) = axiom
      val (ax, cont) = (Equiv(replace(left)(aV, v), replace(right)(aT, t)), Some(alpha))

      // return tactic
      Some(ax, axiomInstance, Substitution(l), cont)
    }
  }

  /**
   * Creates a new axiom tactic for box assignment [x := t;]
   * @return The axiom tactic.
   */
  /*protected[tactics]*/ def predicateReplaceBoxAssignT: PositionTactic = new AxiomTactic("[:=] assignment", "[:=] assignment") {
    override def applies(f: Formula): Boolean = f match {
//      case BoxModality(Assign(v: Variable, t: Variable), _) => true
      case BoxModality(Assign(v: Variable, t: Term), _) => t match {
        case tv: Variable => true
        case _ => !Helper.names(t).contains(v)
      }
      case _ => false
    }

    override def constructInstanceAndSubst(f: Formula, axiom: Formula):
    Option[(Formula, Formula, Substitution, Option[PositionTactic])] = f match {
      case BoxModality(Assign(v: Variable, t: Term), p) =>
        // TODO check that axiom is of the expected form [v:=t]p(v) <-> p(t))
        // construct substitution
        val aV = Variable("v", None, Real)
        val aT = Variable("t", None, Real)
        val aP = Function("p", None, Real, Bool)
        val l = List(new SubstitutionPair(aT, t), new SubstitutionPair(ApplyPredicate(aP, v), p))

        // construct axiom instance: [v:=t]p(v) <-> p(t)
        val g = t match {
          case _: Variable => replace(p)(v, t)
          case _ => replaceFree(p)(v, t)
        }
        val axiomInstance = Equiv(f, g)

        // rename to match axiom if necessary
        def alpha(left: Boolean) = new PositionTactic("Alpha") {
          override def applies(s: Sequent, p: Position): Boolean = s(p) match {
            case Equiv(BoxModality(Assign(_, _), _), _) => true
            case _ => false
          }

          def replaceT(o: Term, n: Variable, p: Position) = o match {
            case tv: Variable => alphaRenamingT(tv.name, tv.index, n.name, None)(p)
            case _ => NilT // TODO requires uniform substitution on axiom in "axiom |- axInstance", instead of alpha renaming on axInstance as is required by AxiomTactic right now
          }

          override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
              if (left)
                Some(alphaRenamingT(v.name, v.index, aV.name, None)(p.first))
              else
                Some(replaceT(t, aT, p.second))

            override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
          }
        }

        val Equiv(lhs, rhs) = axiom
        val (ax, cont) = {
          val lhsrepl = if (v.name != aV.name || v.index != None) replace(lhs)(aV, v)
                        else lhs
          val rhsrepl = replaceFree(rhs)(aT, t)
          val thealpha =
            if (v.name != aV.name || v.index != None) Some(alpha(left = true) & alpha(left = false))
            else Some(alpha(left = false))

          (Equiv(lhsrepl, rhsrepl), thealpha)
        }

        // return tactic
        Some(ax, axiomInstance, Substitution(l), cont)
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
        val aH = PredicateConstant("H")
        val aP = PredicateConstant("p")
        val l = List(new SubstitutionPair(aH, h), new SubstitutionPair(aP, p))
        // construct axiom instance: [?H]p <-> (H -> p).
        val g = Imply(h, p)
        val axiomInstance = Equiv(f, g)
        Some(axiomInstance, Substitution(l))
      case _ => None
    }
  }

  /**
   * Creates a new axiom tactic for non-deterministic assignment [x := *].
   * @return The new tactic.
   */
  protected[tactics] def boxNDetAssign: PositionTactic = new AxiomTactic("[:*] assignment", "[:*] assignment") {
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(NDetAssign(_), _) => true
      case _ => false
    }
    
    override def constructInstanceAndSubst(f: Formula, axiom: Formula): Option[(Formula, Formula, Substitution, Option[PositionTactic])] = f match {
      case BoxModality(NDetAssign(x), p) if Variable.unapply(x).isDefined =>
        val v = x.asInstanceOf[Variable]
        // construct substitution
        val aV = Variable("v", None, Real)
        val aP = Function("p", None, Real, Bool)
        val l = List(new SubstitutionPair(ApplyPredicate(aP, x), p))
        // construct axiom instance: [v:=*]p(v) <-> \forall v. p(v).
        val g = Forall(Seq(v), p)
        val axiomInstance = Equiv(f, g)
        val alpha = new PositionTactic("Alpha") {
          override def applies(s: Sequent, p: Position): Boolean = s(p) match {
            case Equiv(BoxModality(NDetAssign(_), _), Forall(_, _)) => true
            case _ => false
          }

          override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
            override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] =
              Some(alphaRenamingT(v.name, v.index, aV.name, None)(p.first)
                & alphaRenamingT(v.name, v.index, aV.name, None)(p.second))

            override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
          }
        }
        // rename to match axiom if necessary
        val (ax, cont) =
          if (v.name != aV.name || v.index != None) (replaceFree(axiom)(aV, v, None), Some(alpha))
          else (axiom, None)
        Some(ax, axiomInstance, Substitution(l), cont)
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
        val aP = PredicateConstant("p")
        val l = List(new SubstitutionPair(aA, a), new SubstitutionPair(aB, b), new SubstitutionPair(aP, p))
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
        val aP = PredicateConstant("p")
        val l = List(new SubstitutionPair(aA, a), new SubstitutionPair(aP, p))
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
  protected[tactics] def boxChoiceT: PositionTactic = new AxiomTactic("[++] choice", "[++] choice") {
    override def applies(f: Formula): Boolean = f match {
      case BoxModality(Choice(_, _), _) => true
      case _ => false
    }

    override def constructInstanceAndSubst(f: Formula): Option[(Formula, Substitution)] = f match {
      case BoxModality(Choice(a, b), p) =>
        // construct substitution
        val aA = ProgramConstant("a")
        val aB = ProgramConstant("b")
        val aP = PredicateConstant("p")
        val l = List(new SubstitutionPair(aA, a), new SubstitutionPair(aB, b), new SubstitutionPair(aP, p))
        // construct axiom instance: [ a ++ b ]p <-> [a]p & [b]p.
        val g = And(BoxModality(a, p), BoxModality(b, p))
        val axiomInstance = Equiv(f, g)
        Some(axiomInstance, Substitution(l))
      case _ => None
    }

  }

  /*********************************************
   * Rule Tactics
   *********************************************/

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

      def ind(cutSPos: Position, cont: Tactic) = boxInductionT(cutSPos) & AndRightT(cutSPos) & (LabelBranch("Close Next"), abstractionT(cutSPos) & hideT(cutSPos) & cont)
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
          val useCase = prepareKMP & hideT(cutAPos) & kModalModusPonensT(cutSPos) & abstractionT(cutSPos) & hideT(cutSPos) & LabelBranch(indUseCaseLbl)
          val branch1Tactic = ImplyLeftT(cutAPos) & (hideT(p) & LabelBranch(indInitLbl), useCase)
          val branch2Tactic = hideT(p) &
            ImplyRightT(cutSPos) &
            ind(cutSPos, hideT(cutAPos) & LabelBranch(indStepLbl)) &
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
      case Some(inv) => inductionT(Some(inv)).applies(s, p)
      case None => false
    }

    override def apply(p: Position): Tactic = new ConstructionTactic(this.name) {
      override def constructTactic(tool: Tool, node: ProofNode): Option[Tactic] = gen(node.sequent, p) match {
        case Some(inv) => Some(inductionT(Some(inv))(p))
        case None => None
      }

      override def applicable(node: ProofNode): Boolean = applies(node.sequent, p)
    }
  }

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

  /**
   * Creates a new position tactic for the assignment rule.
   * @return The assignment rule tactic.
   */
  protected[tactics] def assignment = new PositionTactic("Assignment") {
    import FOQuantifierTacticsImpl.uniquify
    // for now only on top level
    override def applies(s: Sequent, p: Position): Boolean = {
      (p.inExpr == HereP) && ((if (p.isAnte) s.ante else s.succ)(p.index) match {
        case BoxModality(Assign(Variable(_, _, _), _), _) => true
        case DiamondModality(Assign(Variable(_, _, _), _), _) => true
        case _ => false
      })
    }

    override def apply(p: Position): Tactic = Tactics.weakSeqT(uniquify(p), new ApplyRule(new AssignmentRule(p)) {
      override def applicable(n: ProofNode): Boolean = applies(n.sequent, p)
    })
  }

  // it would be great if we could access the same position to apply the imply right rule
  // FIXME: this only works for toplevel positions since there the positions are stable
  private def assignmentFindImpl = locateSucc(assignment & ImplyRightT) | locateAnte(assignment & ImplyLeftT)

}
