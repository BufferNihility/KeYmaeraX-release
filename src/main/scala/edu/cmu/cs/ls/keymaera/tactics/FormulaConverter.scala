package edu.cmu.cs.ls.keymaera.tactics

import edu.cmu.cs.ls.keymaera.core.ExpressionTraversal.{StopTraversal, ExpressionTraversalFunction, TraverseToPosition}
import edu.cmu.cs.ls.keymaera.core._

/**
 * Created by smitsch on 3/23/15.
 * @author Stefan Mitsch
 */
object FormulaConverter {
  import scala.language.implicitConversions
  implicit def FormulaToFormulaConverter(f: Formula): FormulaConverter = new FormulaConverter(f)
}
class FormulaConverter(val fml: Formula) {
  /**
   * Returns the subformula of fml at position pos.
   * @param pos The position pointing to the subformula.
   * @return The subformula.
   */
  def subFormulaAt(pos: PosInExpr): Formula = {
    if (pos.pos.isEmpty) fml
    else {
      var fAtPos: Option[Formula] = None
      ExpressionTraversal.traverse(TraverseToPosition(pos, new ExpressionTraversalFunction {
        override def preF(p: PosInExpr, e: Formula): Either[Option[StopTraversal], Formula] = {
          fAtPos = Some(e)
          Left(Some(ExpressionTraversal.stop))
        }
      }), fml)
      fAtPos match {
        case Some(f) => f
        case None => throw new IllegalArgumentException(s"Formula $fml at position $pos is not a formula")
      }
    }
  }

  /**
   * Returns the term at position pos in fml.
   * @param pos The position pointing to the term.
   * @return The term.
   */
  def termAt(pos: PosInExpr): Term = {
    if (pos.pos.isEmpty) throw new IllegalArgumentException(s"Formula $fml is not a term")
    else {
      var tAtPos: Option[Term] = None
      ExpressionTraversal.traverse(TraverseToPosition(pos, new ExpressionTraversalFunction {
        override def preT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = {
          tAtPos = Some(e)
          Left(Some(ExpressionTraversal.stop))
        }
      }), fml)
      tAtPos match {
        case Some(t) => t
        case None => throw new IllegalArgumentException(s"Formula $fml at position $pos is not a term")
      }
    }
  }

  /**
   * Extracts a sub-expression from its context and returns both.
   * @param pos The position pointing to the expression.
   * @return A tuple (p(.), e) of context p(.) and sub-expression e, where p(e) is equivalent to fml.
   */
  def extractContext(pos: PosInExpr): (Context, Expr) = {
    var eInCtx: Option[Expr] = None
    ExpressionTraversal.traverse(TraverseToPosition(pos, new ExpressionTraversalFunction {
      override def preF(p: PosInExpr, e: Formula): Either[Option[StopTraversal], Formula] =
        if (p == pos) {
          eInCtx = Some(e)
          Right(CDotFormula)
        } else {
          Left(None)
        }
      override def preT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] =
        if (p == pos) {
          eInCtx = Some(e)
          Right(CDot)
        } else {
          Left(None)
        }
    }), fml) match {
      case Some(f) => (new Context(f), eInCtx.get)
      case None => ???
    }
  }

  /**
   * Transforms the formula into its structural form (all variables and functions substituted with CDot).
   * @return The dottified formula.
   */
  def dottify: Formula = ExpressionTraversal.traverse(new ExpressionTraversalFunction {

    override def preF(p: PosInExpr, e: Formula): Either[Option[StopTraversal], Formula] = e match {
      case Forall(vars, phi) => Right(Forall(vars.map(_ => CDot), new FormulaConverter(phi).dottify))
      case Exists(vars, phi) => Right(Exists(vars.map(_ => CDot), new FormulaConverter(phi).dottify))
      case _ => Left(None)
    }

    override def preT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = e match {
      case _: Variable => Right(CDot)
      case _: Apply => Right(CDot)
      case _ => Left(None)
    }
  }, fml) match {
    case Some(dottified) => dottified
  }

  /**
   * Renames according to names.
   * @param names Maps from old names to new names.
   * @return The renamed formula
   */
  def renameAll(names: Map[NamedSymbol, NamedSymbol]): Formula = {
    ExpressionTraversal.traverse(new ExpressionTraversalFunction {
      override def preT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = e match {
        case v: Variable if names.contains(v) => names(v) match {
          case rv: Variable => Right(rv)
        }
        case _ => Left(None)
      }
    }, fml) match {
      case Some(f) => f
    }
  }

  /**
   * Renames all names according to the example.
   * @param example The example with original names.
   * @param renamedExample The examples with renamed names.
   * @return The renamed formula.
   */
  def renameAllByExample(example: Formula, renamedExample: Formula): Formula = {
    val fmlSymbols = StaticSemantics.symbols(fml)
    val renamedSymbols = StaticSemantics.symbols(example) -- StaticSemantics.symbols(renamedExample)
    val names = renamedSymbols.map(mapName(_, example, renamedExample)).toMap
    if (fmlSymbols.intersect(renamedSymbols).isEmpty) fml
    else renameAll(names)
  }

  /**
   * Returns the name mapping from fml to other.
   * @param other The other formula.
   * @return The name mapping.
   */
  def nameMapping(other: Formula): Map[NamedSymbol, NamedSymbol] = {
    val fmlSymbols = StaticSemantics.symbols(fml)
    val otherSymbols = StaticSemantics.symbols(other)
    (fmlSymbols -- otherSymbols).map(mapName(_, fml, other)).toMap
  }

  private def mapName(orig: NamedSymbol, example: Formula, renamedExample: Formula): (NamedSymbol, NamedSymbol) = {
    var renamedPos: Option[PosInExpr] = None
    ExpressionTraversal.traverse(new ExpressionTraversalFunction {
      override def preT(p: PosInExpr, e: Term): Either[Option[StopTraversal], Term] = e match {
        case v: Variable if v == orig => renamedPos = Some(p); Left(Some(ExpressionTraversal.stop))
        case _ => Left(None)
      }
    }, example)
    renamedPos match {
      case Some(p) => (orig, new FormulaConverter(renamedExample).termAt(p) match { case n: NamedSymbol => n })
      case None => (orig, orig)
    }
  }
}