package edu.cmu.cs.ls.keymaera.tacticsinterface

import edu.cmu.cs.ls.keymaera.core.{Position, SuccPosition, AntePosition}

import scala.util.parsing.combinator.{PackratParsers, RegexParsers}


object CLParser {
  def apply(s: String, loggingOn:Boolean=false): Option[CLTerm] = {
    val parser = new TheParser(loggingOn)
    parser.parseAll(parser.parser, s) match {
      case parser.Success(result, next) => Some(result.asInstanceOf[CLTerm])
      case f: parser.Failure => None
      case e: parser.Error => None
    }
  }

  def log(s: String) : Option[CLTerm] = {
    apply(s, true)
  }

  /**
   * A parser for the tactics interface language.
   *
   * Everything is right-associative? and there is no precedence ordering. @todo actually think about precedence.
   *
   * CLTerm s,t ::= s & t
   * | s && t
   * | s ~ t
   * | s | t
   * | Branch(s, ..., t)
   * | Label(str)
   * | onLabel(str, s)
   * | (s)
   *
   * Created by nfulton on 2/26/15.
   */
  private class TheParser(enabledLogging: Boolean = false) extends RegexParsers with PackratParsers {

    import CLSymbolTable._

    def log[T](p: Parser[T])(name: String) = if (!enabledLogging) p else super.log(p)(name)


    lazy val parsers: List[PackratParser[CLTerm]] =
      strongSeqP ::
      weakSeqP   ::
      seqP       ::
      orP        ::
      branchP    ::
      onLabelP   ::
      kleeneP    ::
      labelP     ::
      posApplyP  ::
      builtinP   ::
      groupP     ::
      Nil

    lazy val cltermP = parsers.reduce(_ | _)
    lazy val parser = cltermP

    protected override val whiteSpace =
      """(\s|(?m)\(\*(\*(?!/)|[^*])*\*\)|/\*(.)*?\*/|\/\*[\w\W\s\S\d\D]+?\*\/)+""".r
    protected val space = """[\ \t\n]*""".r
    protected val ident = """[a-zA-Z][a-zA-Z0-9\_]*""".r

    lazy val groupP : PackratParser[CLTerm] = {
      lazy val pattern = "(" ~> cltermP <~ ")"
      log(pattern)("Grouping") ^^ {
        case x => x
      }
    }

    lazy val strongSeqP: PackratParser[CLTerm] = {
      lazy val pattern = cltermP ~ STRONG_SEQ ~ ("(".? ~> repsep(cltermP,",") <~ ")".?)
      log(pattern)("&&") ^^ {
        case s ~ STRONG_SEQ ~ t => StrongSeqC(s, t)
      }
    }

    lazy val weakSeqP: PackratParser[CLTerm] = {
      lazy val pattern = cltermP ~ WEAK_SEQ ~ cltermP
      log(pattern)("~") ^^ {
        case s ~ WEAK_SEQ ~ t => WeakSeqC(s, t)
      }
    }

    lazy val seqP: PackratParser[CLTerm] = {
      lazy val pattern = cltermP ~ SEQ ~ cltermP
      log(pattern)("&") ^^ {
        case s ~ SEQ ~ t => SeqC(s, t)
      }
    }

    lazy val orP: PackratParser[CLTerm] = {
      lazy val pattern = cltermP ~ OR ~ cltermP
      log(pattern)("|") ^^ {
        case s ~ OR ~ t => OrC(s, t)
      }
    }

    lazy val kleeneP: PackratParser[CLTerm] = {
      lazy val pattern = cltermP ~ KLEENE
      log(pattern)("*") ^^ {
        case t ~ KLEENE => KleeneC(t)
      }
    }

    lazy val labelP: PackratParser[CLTerm] = {
      lazy val pattern = LABEL ~ ("(" ~> ident <~ ")")
      log(pattern)("label") ^^ {
        case LABEL ~ s => LabelC(s)
      }
    }

    lazy val branchP: PackratParser[CLTerm] = {
      lazy val pattern = BRANCH ~ ("(" ~> (cltermP ~ ",".?).* <~ ")")
      log(pattern)("branch") ^^ {
        case BRANCH ~ xs => BranchC(xs.map(p => p._1))
      }
    }

    lazy val onLabelP: PackratParser[CLTerm] = {
      lazy val pattern = ON_LABEL ~ "(" ~ ident ~ cltermP ~ ")"
      log(pattern)("onLabel") ^^ {
        case ON_LABEL ~ "(" ~ name ~ term ~ ")" => OnLabelC(name, term)
      }
    }

    lazy val builtinP: PackratParser[CLTerm] = {
      lazy val pattern = ident
      log(pattern)("builtin") ^^ {
        case name => BuiltInC(name)
      }
    }

    lazy val posApplyP: PackratParser[CLTerm] = {
      lazy val pattern = ident ~ ("(" ~> ("""s|a""".r ~ numberP) <~ ")")
      log(pattern)("ApplyP") ^^ {
        case name ~ (marker ~ pos) => PosApplyC(name, numberToPosition(marker, pos))
      }
    }

    lazy val numberP : PackratParser[Int] = {
      lazy val pattern = """[0-9]+(\.[0-9]+)?""".r
      log(pattern)("Integer position.") ^^ {
        case n => Integer.parseInt(n)
      }
    }

    def numberToPosition(marker : String, n : Int) = {
      if(marker == "s") {
        SuccPosition(n)
      }
      else if(marker == "a") {
        AntePosition(n)
      }
      else {
        ???
      }
    }
  }

  private object CLSymbolTable {
    val STRONG_SEQ = "&&"
    val WEAK_SEQ = "~"
    val SEQ = "&"
    val OR = "|"
    val KLEENE = "*"
    val LABEL = "Label"
    val BRANCH = "Branch"
    val ON_LABEL = "onLabel"
  }

}