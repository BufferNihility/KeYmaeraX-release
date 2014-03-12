package edu.cmu.cs.ls.keymaera.parser

import scala.util.parsing.combinator._
import scala.util.parsing.combinator.lexical._
import scala.util.parsing.combinator.syntactical._
import edu.cmu.cs.ls.keymaera.core._
import edu.cmu.cs.ls.keymaera.core.Add


/**
 * The KeyMaera Parser
 * @author Nathan Fulton
 */
class KeYmaeraParser extends RegexParsers with PackratParsers {    
  //////////////////////////////////////////////////////////////////////////////
  // Public-facing interface.
  //////////////////////////////////////////////////////////////////////////////

  def parse(s:String):Expr = {
    val parser = new KeYmaeraParser()
    
    //Parse file.
    val (functions,predicateConstants,variables,problemText) = 
      parser.parseAll(parser.fileParser, s) match {
        case parser.Success(result,next) => result
        case parser.Failure(_,_) => throw new Exception("parse failed.")
        case parser.Error(_,_) => throw new Exception("parse errored.")
      }
    
    //Parse problem.
    val exprParser = parser.makeExprParser(variables, functions, predicateConstants)
    parser.parseAll(exprParser, problemText) match {
        case parser.Success(result,next) => result
        case parser.Failure(_,_) => throw new Exception("parse failed.")
        case parser.Error(_,_) => throw new Exception("parse errored.")
    }
  }
  
  import edu.cmu.cs.ls.keymaera.parser.ParseSymbols._
  
  type Tokens = StdLexical
  
  //////////////////////////////////////////////////////////////////////////////
  // Primitive syntactic constructs
  //////////////////////////////////////////////////////////////////////////////
  
  protected override val whiteSpace = 
    """(\s|(?m)\(\*(\*(?!/)|[^*])*\*\)|/\*(.)*?\*/)+""".r
  protected val space               = """[\ \t\n]*""".r
  /**
   * ``ident" should match function, variable and sort names.
   */
  protected val ident               = """[a-zA-Z][a-zA-Z0-9\_]*""".r
  
  //TODO-nrf not sure what this is for.
  val lexical = new StdLexical
  lexical.delimiters ++= List("\\functions", "{", "}", "(", ")", "+", "-", "*", "/")
  
  //////////////////////////////////////////////////////////////////////////////
  // Function Definition Section.
  //////////////////////////////////////////////////////////////////////////////

  /**
   * @returns A list of defined function sorts.
   */
  lazy val functionsP = "\\functions" ~ "{" ~ repsep(funcdefP, ";") ~ "}" ^^ {
    case  "\\functions" ~ "{" ~ definitions ~ "}" => definitions
  }

  lazy val funcdefP = ident ~ ident ~ "(" ~ repsep(ident, ",") ~ ")" ^^ {
    case rsort ~ name ~ _ ~ argsorts ~ _ => 
      if(argsorts.length > 0)
        Function(name, None, identsToSorts(argsorts), identToSort(rsort))
      else
        PredicateConstant(name)
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // State Variable Definition Section.
  //////////////////////////////////////////////////////////////////////////////

  /**
   * @returns A list of defined program sorts.
   */
  lazy val programVariablesP = 
    "\\programVariables" ~ "{" ~ repsep(vardefP, ";") ~ "}" ^^ {
      case "\\programVariables" ~ "{" ~ variables ~ "}" => variables
    }

  lazy val vardefP = ident ~ ident ^^ {
    //Note: it might be necessary to give these arguments indices.
    case rsort ~ name => Variable(name, None, identToSort(rsort))
  }

  //////////////////////////////////////////////////////////////////////////////
  // Problem Section.
  //////////////////////////////////////////////////////////////////////////////
  lazy val problemP = 
    "\\problem" ~ "{" ~> """*""".r <~ "}" ^^ {
      case programText => programText
    }
    
  lazy val fileParser = functionsP.? ~ programVariablesP.? ~ problemP ^^ {
    case functionSection ~ programVarsSection ~ problemText => {
      val functions:List[Function] = functionSection match {
        case Some(l) => l.filter(_.isInstanceOf[Function]).map(_.asInstanceOf[Function])
        case None    => List[Function]()
      }
      val predicateConstants:List[PredicateConstant] = functionSection match {
        case Some(l) => l.filter(_.isInstanceOf[PredicateConstant]).map(_.asInstanceOf[PredicateConstant])
        case None    => List[PredicateConstant]()
      }
      val variables:List[Variable] = programVarsSection match {
        case Some(l) => l
        case None    => List[Variable]()
      }
      (functions,predicateConstants,variables,problemText)
    }
  }
 
  /* ***************************************************************************
   * Begin parsing actual programs and formulas.
   * **************************************************************************/

  def makeExprParser(variables:List[Variable], functions:List[Function],
      predicates:List[PredicateConstant]):PackratParser[Expr] =
  {
    val programs = List[ProgramConstant]()
    
    lazy val termParser = new TermParser(variables,functions).parser
    lazy val formulaParser = new FormulaParser(variables,functions,predicates).parser
    lazy val programParser = new ProgramParser(variables,functions,predicates,programs).parser
    lazy val ret = termParser | formulaParser | programParser ^^ {
      case e => e
    }
    ret
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // Terms.
  //////////////////////////////////////////////////////////////////////////////
  class TermParser(variables:List[Variable], functions:List[Function]) {
    type SubtermParser = PackratParser[Term]
    
    //TODO-nrf Some of these parsers assign sorts somewhat arbitrarily, and I'm
    //not sure about the correct thing to do. see e.g. subtractP.
    lazy val parser = precedence.reduce(_|_)
    
    val precedence : List[SubtermParser] =
      multiplyP ::
      divP ::
      addP ::
      subtractP ::
      negativeP ::
      applyP ::
      variableP(variables) ::
      numberP   ::
      groupP    ::
      Nil
    
    //Compound terms
      
    lazy val multiplyP:SubtermParser = {
      lazy val pattern = leftAssociative(precedence, addP, Some(MULTIPLY))
      log(pattern)("Multiplication") ^^ {
        case left ~ MULTIPLY ~ right => Multiply(left.sort, left,right)
      }
    }
    lazy val divP:SubtermParser = {
      lazy val pattern = leftAssociative(precedence, addP, Some(DIVIDE))
      log(pattern)("Division") ^^ {
        case left ~ DIVIDE ~ right => Divide(left.sort, left,right)
      }
    }
    
    lazy val addP:SubtermParser = {
      lazy val pattern = leftAssociative(precedence, addP, Some(PLUS))
      log(pattern)("Addition") ^^ {
        case left ~ PLUS ~ right => Add(left.sort, left,right)
      }
    }
    lazy val subtractP:SubtermParser = {
      lazy val pattern = leftAssociative(precedence, subtractP, Some(MINUS))
      log(pattern)("Subtract") ^^ {
        case left ~ MINUS ~ right => Subtract(left.sort, left,right)
      }
    }
    
    //Unary term operators
    
    lazy val negativeP:SubtermParser = {
      lazy val pattern = NEGATIVE ~ parser
      log(pattern)("negate") ^^ {
        case NEGATIVE ~ term => Neg(term.sort, term)
      }
    }
    
    //Function application
    
    lazy val applyP:SubtermParser = {
      lazy val pattern = functionP(functions,parser) ~ "(" ~ repsep(parser, ",") ~ ")"
      log(pattern)("Function Application") ^^ {
        case function ~ "(" ~ args ~ ")" => 
          Apply(function, 
              args.reduce( (l,r) => Pair( TupleT(l.sort,r.sort), l, r) ) )
      }
    }

    //Groupings
    
    lazy val groupP:SubtermParser = {
      lazy val pattern = "(" ~ precedence.reduce(_|_) ~ ")"
      log(pattern)("Subterm Grouping") ^^ {
        case "(" ~ p ~ ")" => p
      }
    }
    
    //Numbers
    
    lazy val numberP:SubtermParser = {
      lazy val pattern = """[0-9]+(\.[0-9]+)?""".r
      log(pattern)("NUMBER") ^^ {
        case n => Number(BigDecimal(n))
      }
    }
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // Formulas.
  //////////////////////////////////////////////////////////////////////////////
  class FormulaParser(variables:List[Variable],
      functions:List[Function],
      predicates:List[PredicateConstant]) 
  {
    type SubformulaParser = PackratParser[Formula]
    
    lazy val parser = precedence.reduce(_|_)
    
    val precedence : List[SubformulaParser] =
      implP  ::
      equivP ::
      leP    ::
      geP    ::
      gtP    ::
      ltP    :: 
      notP ::
      derivativeP ::
      predicateP ::
      groupP ::
      trueP ::
      falseP ::
      Nil
    
    //Predicates
    
    lazy val predicateP:SubformulaParser = {
      log(ident)("Predicate") ^^ {
        case name => {
          predicates.find(PredicateConstant.unapply(_) match {
            case Some(p) => p._1.equals(name)
            case None => false
          }) match {
            case Some(p) => p
            case None => 
              throw new Exception("Predicate was mentioned out of context: " + name)
          }
        }
      }
    }
      
    //Unary Formulas
    
    lazy val notP:SubformulaParser = {
      lazy val pattern = NEGATE ~ parser
      log(pattern)(NEGATE) ^^ {
        case NEGATE ~ f => Not(f.asInstanceOf[Formula])
      }
    }
    
    lazy val derivativeP:SubformulaParser = {
      log(parser ~ PRIME)("Formula derivative") ^^ {
        case v ~ "'" => new FormulaDerivative(v)
      }
    }
    
    //Binary Formulas
    
    lazy val equivP:SubformulaParser = {
      lazy val pattern = rightAssociative(precedence, equivP, Some(ARROW))
      log(pattern)(ARROW) ^^ {
        case left ~ _ ~ right => Imply(left,right)
      }
    }
    
    lazy val implP:SubformulaParser = {
      lazy val pattern = rightAssociative(precedence, implP, Some(ARROW))
      log(pattern)(ARROW) ^^ {
        case left ~ _ ~ right => Imply(left,right)
      }
    }
    
    lazy val andP : SubformulaParser = {
      lazy val pattern = leftAssociative(precedence, andP, Some(OR))
      log(pattern)(AND) ^^ {
        case left ~ _ ~ right => And(left,right)
      }
    }
    
    lazy val orP : SubformulaParser = {
      lazy val pattern = leftAssociative(precedence, orP, Some(OR))
      log(pattern)(OR) ^^ {
        case left ~ _ ~ right => Or(left,right)
      }
    }
    //Binary Relations

    lazy val termParser = new TermParser(variables,functions).parser
    
    lazy val leP:SubformulaParser = {
      lazy val pattern = termParser ~ LEQ ~ termParser
      log(pattern)(LEQ) ^^ {
        case left ~ LEQ ~ right =>  
          LessEquals(left.sort,left,right)
      }
    }
    
    lazy val geP:SubformulaParser = {
      lazy val pattern = termParser ~ GEQ ~ termParser
      log(pattern)(GEQ) ^^ {
        case left ~ GEQ ~ right =>  
          GreaterEquals(left.sort,left,right)
      }
    }
    
    lazy val gtP:SubformulaParser = {
      lazy val pattern = termParser ~ GT ~ termParser
      log(pattern)(GT) ^^ {
        case left ~ GT ~ right =>  
          GreaterThan(left.sort,left,right)
      }
    }
    
    lazy val ltP:SubformulaParser = {
      lazy val pattern = termParser ~ LT ~ termParser
      log(pattern)(LT) ^^ {
        case left ~ LT ~ right =>  
          LessThan(left.sort,left,right)
      }
    }

    lazy val falseP:SubformulaParser = {
      lazy val pattern = FALSE
      log(pattern)("False") ^^ {
        case FALSE => False
      }
    }
    
    lazy val trueP:SubformulaParser = {
      lazy val pattern = TRUE
      log(pattern)("true") ^^ {
        case TRUE => True
      }
    }
    
    lazy val groupP:SubformulaParser = {
      lazy val pattern = "(" ~ precedence.reduce(_|_) ~ ")"
      log(pattern)("Subterm Grouping") ^^ {
        case "(" ~ p ~ ")" => p
      }
    }
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // Programs.
  //////////////////////////////////////////////////////////////////////////////
  class ProgramParser(variables:List[Variable],
      functions:List[Function],
      predicates:List[PredicateConstant],
      programVariables:List[ProgramConstant]) 
  {
    type SubprogramParser = PackratParser[Program]
    
    lazy val parser = precedence.reduce(_|_)
    //TODO do we need to make the program variables into predicates so that they
    //can be assigned to and such? Actually, I think that the stuff in ProgramVariables
    // should all be put into the predicates in the first place because programVariables
    //should only hold variables which hold arbitrary programs.
    lazy val formulaParser = new FormulaParser(variables, functions, predicates).parser
    lazy val termParser = new TermParser(variables,functions).parser
    
    val precedence : List[SubprogramParser] =
      choiceP     ::
      sequenceP   ::
      ifThenElseP ::
      ifThenP     ::
      whileP      ::
      closureP    ::
      assignP     ::
      ndassignP   ::
//      evolutionP ::
      testP       ::
      pvarP       ::
      groupP      ::
      Nil
      
//      parallelP ::
//      choiceP ::
//      sequenceP ::
//      ifElseP ::
//      ifP ::
//      whileP ::
//      closureP ::
//      assignmentP ::
//      nonDetAssignmentP ::
//      evolutionP ::
//      testP ::
//      receiveP ::
//      receiveMultipleP ::
//      sendP :: 
//      pvarP ::
//      groupP     ::
//      Nil  
    
    lazy val sequenceP:SubprogramParser = {
      lazy val pattern = rightAssociativeOptional(precedence,sequenceP,Some(SCOLON))
      log(pattern)("program" + SCOLON + "program") ^^ {
        case left ~ SCOLON ~ right => right match {
          case Some(rightDefined) => new Sequence(left,rightDefined)
          case None               => left
        }
      }
    }
    
    lazy val choiceP:SubprogramParser = {
      lazy val pattern = rightAssociative(precedence,choiceP,Some(CHOICE))
      log(pattern)(CHOICE) ^^ {
        case left ~ _ ~ right => new Choice(left,right)
      }
    }
    
    lazy val ifThenP:SubprogramParser = {
      lazy val pattern = "if" ~ formulaParser ~ "then" ~ parser
      log(pattern)("if-then") ^^ {
        case "if" ~ f ~ "then" ~ p => new IfThen(f,p)
      }
    }
    
    lazy val ifThenElseP:SubprogramParser = {
      lazy val pattern = "if" ~ formulaParser ~ "then" ~ parser ~ "else" ~ parser
      log(pattern)("if-then") ^^ {
        case "if" ~ f ~ "then" ~ p1 ~ "else" ~ p2 => new IfThenElse(f,p1,p2)
      }
    }
    
    lazy val whileP:SubprogramParser = {
      lazy val pattern = "while(" ~ formulaParser ~ ")" ~ parser ~ "end"
      log(pattern)("while") ^^ {
        case "while(" ~ test ~ ")" ~ alpha ~ "end" => new Sequence(
          new Loop( new Sequence( new Test(test), alpha ) ),
          new Test(Not(test))
        )
      }
    }
    
    lazy val closureP:SubprogramParser = {
      lazy val pattern = tighterParsers(precedence, closureP).reduce(_|_) ~ KSTAR
      log(pattern)("*") ^^ {
        case p ~ KSTAR => new Loop(p)
      }
    }
    
    lazy val assignP:SubprogramParser = {
      lazy val pattern = termParser ~ ASSIGN ~ termParser
      log(pattern)("Assignment") ^^ {
        case pvar ~ ASSIGN ~ term => new Assign(pvar, term)
      }
    }
    
    lazy val ndassignP:SubprogramParser = {
      lazy val pattern = termParser ~ ASSIGN ~ KSTAR
      log(pattern)("Non-det assign") ^^ {
        case t ~ ASSIGN ~ KSTAR => new NDetAssign(t)
      }
    }
    
//    lazy val nfodeP:SubprogramParser = null
//    
//    lazy val evolutionP:SubprogramParser = {
//      lazy val pattern = (OPEN_CBRACKET ~
//                          repsep(nfodeP, ",") ~
//                          CLOSE_CBRACKET) |
//                         (OPEN_CBRACKET ~ 
//                          repsep(nfodeP, ",") ~
//                          COMMA ~
//                          formulaParser ~
//                          CLOSE_CBRACKET)
//      log(pattern)("Cont Evolution") ^^ {
//        case OPEN_CBRACKET ~ odes ~ CLOSE_CBRACKET => 
//      }
//    }
    
    lazy val testP:SubprogramParser = {
      lazy val pattern = TEST ~ formulaParser
      log(pattern)(TEST) ^^ {
        case TEST ~ f => new Test(f)
      }
    }
    
    lazy val pvarP:SubprogramParser = {
      log(ident)("Program Variable") ^^ {
        case name => programVariables.find(ProgramConstant.unapply(_) match {
          case Some(t) => t._1.equals(name)
          case None    => false
        }) match {
          case Some(p ) => p
          case None     => throw new Exception("Program variable was mentioned but not in context: " + name)
        }
      }
    }
    
    lazy val groupP:SubprogramParser = {
      lazy val pattern = "(" ~ precedence.reduce(_|_) ~ ")"
      log(pattern)("Subterm Grouping") ^^ {
        case "(" ~ p ~ ")" => p
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Utility methods for converting strings into relevant portions of the core.
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Maps identifiers for common types to their core representation. Example:
   *   "R" -> Real
   *   "MyOwnSort" -> UserDefinedSort("MyOwnSort")
   */
  def identToSort(ident:String):Sort = ident match {
    case "R" => Real
    case "B" => Bool
    case _   => UserSort(ident)
  }

  /**
   * Maps a list of sort identifiers to the appropriate tuple sort.
   * @see identToSort
   */
  def identsToSorts(idents : List[String]):Sort = {
    val sortList = idents.map(ident => identToSort(ident))
    sortList.reduceRight( (l,r) => TupleT.apply(l, r) )
  }
  
  def projectName(v:Variable):String = Variable.unapply(v) match {
    case Some(t) => t._1
    case None    => ""
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // Utility methods for precedence lists
  //////////////////////////////////////////////////////////////////////////////
  //TODO-nrf clean up this section.
  def tighterParsers[T](precedence:List[PackratParser[T]], parser:PackratParser[T]):List[PackratParser[T]] = 
    precedence.dropWhile(candidate => !candidate.equals(parser)).drop(1)
  
  def asTightAsParsers[T](precedence:List[PackratParser[T]],parser:PackratParser[T]):List[PackratParser[T]] =
    precedence.dropWhile(candidate => !candidate.equals(parser))

  def leftAssociative[T](precedence:List[PackratParser[T]],parser:PackratParser[T],separator:Option[String]) = {
    val tighter = tighterParsers(precedence,parser)
    val asTightAs = asTightAsParsers(precedence, parser)
    
    separator match {
      case Some(s:String)	=> asTightAs.reduce(_|_) ~ s  ~ tighter.reduce(_|_)
      case None				=> asTightAs.reduce(_|_) ~ "" ~ tighter.reduce(_|_)
    }
  }
  
  def rightAssociative[T](precedence:List[PackratParser[T]],parser:PackratParser[T],separator:Option[String]) = {
    val tighter = tighterParsers(precedence,parser)
    val asTightAs = asTightAsParsers(precedence, parser)
    
    separator match {
      case Some(s:String) => tighter.reduce(_|_) ~ s ~ asTightAs.reduce(_|_)
      case None			=> tighter.reduce(_|_) ~ "" ~ asTightAs.reduce(_|_)
    }
  }
  
  def rightAssociativeOptional[T](precedence:List[PackratParser[T]],parser:PackratParser[T],separator:Option[String]) = {
    val tighter = tighterParsers(precedence,parser)
    val asTightAs = asTightAsParsers(precedence, parser)
    
    separator match {
      case Some(s:String) => tighter.reduce(_|_) ~ s ~ asTightAs.reduce(_|_).?
      case None			=> tighter.reduce(_|_) ~ "" ~ asTightAs.reduce(_|_).?
    }
  }
  
  
  //////////////////////////////////////////////////////////////////////////////
  // Utility Methods for variables
  //////////////////////////////////////////////////////////////////////////////
    def variableP(variables:List[Variable]):PackratParser[Term] = {
      lazy val ret = log(ident)("Variable") ^^ {
        case name => variables.find(Variable.unapply(_) match {
          case Some(t) => t._1.equals(name)
          case None    => false
        }) match {
          case Some(v) => v
          case None    => 
            throw new Exception("Variable " + name + " not in context.")
        }
      }
      ret
    }
  def functionP(functions:List[Function],subtermP:PackratParser[Expr]):PackratParser[Function] = {
      lazy val ret = log(ident ~ "(" ~> repsep(subtermP, ",") <~ ")")("Function") ^^ 
      {
        case name => functions.find(Function.unapply(_) match {
          case Some(t) => t._1.equals(name)
          case None    => false
        }) match {
          case Some(f) => f
          case None    => 
            throw new Exception("Variable " + name + " not in context.")
        }
      }
      ret
    }   
  


}

// vim: set ts=4 sw=4 et:
