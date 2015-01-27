package edu.cmu.cs.ls.keymaera.tactics

import edu.cmu.cs.ls.keymaera.core.ExpressionTraversal.{StopTraversal, ExpressionTraversalFunction}
import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.core.Substitution._

/**
 * Performs alpha conversion on formulas and terms. Used in tactics to setup axiom instances and substitution
 * definitions for uniform substitution.
 *
 * Created by smitsch on 1/15/15.
 * @author Stefan Mitsch
 */
object AlphaConversionHelper {
  /**
   * Replaces the old term (usually a variable) o with a new named term (variable or CDot) n in formula f, but only if
   * the old term is contained in the set of free names.
   * @param f The formula.
   * @param o The old term.
   * @param n The new named term (i.e., a variable or CDot).
   * @param free The optional set of free names. If None, all names are considered free. Defaults to the names
   *             that may be read in f.
   */
  def replaceFree(f: Formula)(o: Term, n: Term,
                              free: Option[Set[NamedSymbol]] = Some(maybeFreeVariables(f))): Formula = {
    // TODO might no longer be necessary to have free an option
    ExpressionTraversal.traverse(
      new ExpressionTraversalFunction {
        override def preF(p: PosInExpr, e: Formula): Either[Option[StopTraversal], Formula] = e match {
          case fa@Forall(v, fo) => n match {
            case nname: Variable => Right(Forall(v.map((name: NamedSymbol) => if (name == o) nname else name),
              replaceFree(fo)(o, n, Some(maybeFreeVariables(fo)))))
            case _ => Right(fa)
          }
          case ex@Exists(v, fo) => n match {
            case nname: Variable => Right(Exists(v.map((name: NamedSymbol) => if (name == o) nname else name),
              replaceFree(fo)(o, n, Some(maybeFreeVariables(fo)))))
            case _ => Right(ex)
          }
          case BoxModality(Assign(x: Variable, t), pred) => free match {
            case Some(freeVars) => Right(BoxModality(Assign(x, replaceFree(t)(o, n, free)),
              replaceFree(pred)(o, n, Some(freeVars - x))))
            case None => Right(BoxModality(Assign(replaceFree(x)(o, n, None), replaceFree(t)(o, n, None)),
              replaceFree(pred)(o, n, None)))
          }
          case BoxModality(NFContEvolve(v, xprime@Derivative(d, x: Variable), t, h), pred) => free match {
            case Some(freeVars) => Right(BoxModality(NFContEvolve(v, xprime, replaceFree(t)(o, n, Some(freeVars - x)),
              replaceFree(h)(o, n, Some(freeVars - x))), replaceFree(pred)(o, n, Some(freeVars - x))))
            case None => Right(BoxModality(NFContEvolve(v, Derivative(d, n), replaceFree(t)(o, n, None),
              replaceFree(h)(o, n, None)), replaceFree(pred)(o, n, None)))
          }
          case BoxModality(ContEvolveProduct(lode, rode), pred) => free match {
            case Some(freeVars) => Right(BoxModality(ContEvolveProduct(
              replaceFree(lode)(o, n, Some(certainlyFreeVariables(lode, freeVars))),
              replaceFree(rode)(o, n, Some(certainlyFreeVariables(rode, freeVars)))),
              replaceFree(pred)(o, n, Some(certainlyFreeVariables(lode, freeVars).intersect(certainlyFreeVariables(rode, freeVars))))))
            case None => Right(BoxModality(ContEvolveProduct(
              replaceFree(lode)(o, n, None),
              replaceFree(rode)(o, n, None)), replaceFree(pred)(o, n, None)))
          }
          case BoxModality(Loop(a), pred) => free match {
            case Some(freeVars) => Right(BoxModality(Loop(replaceFree(a)(o, n, Some(freeVariables(a)))),
              replaceFree(pred)(o, n, Some(certainlyFreeVariables(a, freeVars)))))
            case None => Left(None)
          }
          case DiamondModality(Assign(x: Variable, t), pred) => free match {
            case Some(freeVars) => Right(DiamondModality(Assign(x, replaceFree(t)(o, n, free)),
              replaceFree(pred)(o, n, Some(freeVars - x))))
            case None => Right(DiamondModality(Assign(replaceFree(x)(o, n, None), replaceFree(t)(o, n, None)),
              replaceFree(pred)(o, n, None)))
          }
          case DiamondModality(Loop(a), pred) => free match {
            case Some(freeVars) => Right(DiamondModality(Loop(replaceFree(a)(o, n, Some(freeVariables(a)))),
              replaceFree(pred)(o, n, Some(certainlyFreeVariables(a, freeVars)))))
            case None => Left(None)
          }
          case _ => Left(None)
        }
        override def preT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = e match {
          case v: Variable if v == o => free match {
            case Some(freeVars) => if (freeVars.contains(v)) Right(n) else Left(None)
            case None => Right(n)
          }
          case _ => Left(None)
        }

      }, f) match {
      case Some(g) => g
      case None => throw new IllegalStateException("Replacing one variable by another should not fail")
    }
  }

  /**
   * Replace the old term o (usually a variable) by a new named term (variable or CDot) n in term t, but only if the
   * old term is contained in the set of free names.
   * @param t The term.
   * @param o The old term.
   * @param n The new named term (i.e., a variable or CDot).
   * @param free The optional set of free names. If None, all names are considered free.
   */
  def replaceFree(t: Term)(o: Term, n: Term, free: Option[Set[NamedSymbol]]): Term =
      ExpressionTraversal.traverse(
    new ExpressionTraversalFunction {
      override def preT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = e match {
        case v: Variable if v == o => free match {
          case Some(freeVars) => if (freeVars.contains(v)) Right(n) else Left(None)
          case _ => Right(n)
        }
        case _ => Left(None)
      }
    }, t) match {
    case Some(g) => g
    case None => throw new IllegalStateException("Replacing one variable by another should not fail")
  }

  def replaceFree[T <: Program](p: T)(o: Term, n: Term, free: Option[Set[NamedSymbol]]): T =
    ExpressionTraversal.traverse(
      new ExpressionTraversalFunction {
        override def preT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = e match {
          case v: Variable if v == o => free match {
            case Some(freeVars) => if (freeVars.contains(v)) Right(n) else Left(None)
            case _ => Right(n)
          }
          case _ => Left(None)
        }
      }, p) match {
      case Some(g) => g
      case None => throw new IllegalStateException("Replacing one variable by another should not fail")
    }

  /**
   * Replace the old term o by a new named term n in formula f.
   * @param f The formula.
   * @param o The old term.
   * @param n The new term
   * @return The formula where the old term o is replaced with the new term n
   */
  def replace(f: Formula)(o: Term, n: Term): Formula =
    ExpressionTraversal.traverse(
      new ExpressionTraversalFunction {
        override def preT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = e match {
          case t: Term if t == o => Right(n)
          case _ => Left(None)
        }
      }, f) match {
      case Some(g) => g
      case None => throw new IllegalStateException("Replacing one variable by another should not fail")
    }

  private def certainlyFreeVariables(p: Program, initial: Set[NamedSymbol]) =
    (initial -- maybeFreeVariables(p)) ++ freeVariables(p)
}
