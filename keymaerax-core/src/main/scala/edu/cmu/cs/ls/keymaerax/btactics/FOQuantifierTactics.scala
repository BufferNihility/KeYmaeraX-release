package edu.cmu.cs.ls.keymaerax.btactics

import edu.cmu.cs.ls.keymaerax.bellerophon._
import edu.cmu.cs.ls.keymaerax.btactics.TactixLibrary._
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.parser.StringConverter._
import edu.cmu.cs.ls.keymaerax.tactics._
import edu.cmu.cs.ls.keymaerax.tactics.Augmentors._

import scala.collection.immutable
import scala.language.postfixOps


/**
 * [[FOQuantifierTactics]] provides tactics for instantiating quantifiers.
 */
object FOQuantifierTactics {
  def allL(quantified: Option[Variable], instance: Term): DependentPositionTactic =
    new DependentPositionTactic("allL") {
      override def apply(pos: Position): DependentTactic = pos match {
        case ante: AntePosition => new DependentTactic(name) {
          override def computeExpr(v : BelleValue): BelleExpr = v match {
            case BelleProvable(provable) => createTactic(provable, ante)
          }
        }
        case _ => throw new BelleUserGeneratedError("All instantiate is only applicable in antecedent, but got " + pos)
      }

      def createTactic(provable: Provable, pos: AntePosition): BelleExpr = {
        require(provable.subgoals.size == 1, "Provable must have exactly 1 subgoal, but got " + provable.subgoals.size)
        val sequent = provable.subgoals.head
        def vToInst(vars: Seq[Variable]) = if (quantified.isEmpty) vars.head else quantified.get
        sequent.sub(pos) match {
          case Some(f@Forall(vars, qf)) if quantified.isEmpty || vars.contains(quantified.get) =>
            def forall(h: Formula) = if (vars.length > 1) Forall(vars.filter(_ != vToInst(vars)), h) else h
            // construct axiom instance: \forall x. p(x) -> p(t)
            val g = SubstitutionHelper.replaceFree(qf)(vToInst(vars), instance)
            val axiomInstance = Imply(f, forall(g))

            val pattern = SequentType(Sequent(
              Nil,
              immutable.IndexedSeq(),
              immutable.IndexedSeq("(\\forall x p(x)) -> p(t())".asFormula)))
            val allInstantiateAxiom = USubstPatternTactic((pattern, (ru:RenUSubst) =>
              ru.getRenamingTactic & ProofRuleTactics.axiomatic("all instantiate", ru.substitution.usubst))::Nil
            )

            ProofRuleTactics.cut(axiomInstance) <(
              (modusPonens(pos, AntePos(sequent.ante.length)) & hideL(pos)) partial,
              cohide(new SuccPosition(sequent.succ.length)) & allInstantiateAxiom
              )
          case Some(f@Forall(v, _)) if quantified.isDefined && !v.contains(quantified.get) =>
            throw new BelleError("Cannot instantiate: universal quantifier " + f + " does not bind " + quantified.get)
          case Some(f) =>
            throw new BelleError("Cannot instantiate: formula " + f.prettyString + " at pos " + pos + " is not a universal quantifier")
          case _ =>
            throw new BelleError("Position " + pos + " is not defined in " + sequent.prettyString)
        }
      }
  }

  def existsR(quantified: Option[Variable], instance: Term): DependentPositionTactic =
    new DependentPositionTactic("existsR") {
      override def apply(pos: Position): DependentTactic = pos match {
        case succ: SuccPosition => new DependentTactic(name) {
          override def computeExpr(v : BelleValue): BelleExpr =
            useAt("exists dual", PosInExpr(1::Nil))(succ) &
            notR(succ) & allL(quantified, instance)('Llast) & notL('Llast)
        }
        case _ => throw new BelleUserGeneratedError("Exists instantiate is only applicable in succedent, but got " + pos)
      }
    }
}
