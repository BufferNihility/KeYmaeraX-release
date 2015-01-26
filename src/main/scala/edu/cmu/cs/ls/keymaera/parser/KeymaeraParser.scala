package edu.cmu.cs.ls.keymaera.parser

import edu.cmu.cs.ls.keymaera.tactics.{ConfigurableGenerate, Generator}

import scala.util.parsing.combinator._
import scala.util.parsing.combinator.lexical._
import scala.util.parsing.combinator.syntactical._
import edu.cmu.cs.ls.keymaera.core._
import scala.util.matching.Regex
import scala.annotation.elidable
import scala.annotation.elidable._
import scala.util.parsing.input.Reader
import scala.util.parsing.input.CharSequenceReader
import edu.cmu.cs.ls.keymaera.core.Term
import java.io.File


/**
 * The KeYmaera Parser
 * @author Nathan Fulton
 * @author Stefan Mitsch
 * TODO:
 *  * cleaner distinction between:
 *  	predicates (boolean-valued, rigid)
 *      rigid variables (currently predicateconstants, but that implies boolean-valued) 
 *      "assignables" (non-rigid, currently called variables)
 *      functions (terms that take arguments
 */
class KeYmaeraParser(enabledLogging: Boolean = false,
                     env: { val generator: ConfigurableGenerate[Formula]} = new { lazy val generator = new ConfigurableGenerate[Formula]()})
  extends RegexParsers with PackratParsers {
  def log[T](p : Parser[T])(name : String) = if(!enabledLogging) p else super.log(p)(name)
  //////////////////////////////////////////////////////////////////////////////
  // Public-facing interface.
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Returns all occurances of variables in s.
   * @param s
   * @return All variables occcuring in s.
   */
  private def allVariableOccurances(s:String) = ident.findAllIn(s).map(s => {
    val varParts = s.split("_")
    if (varParts.length == 1 || (!varParts.last.forall(_.isDigit))) new Variable(s, None, Real)
    else new Variable(varParts.slice(0, varParts.length - 1).mkString("_"), Some(varParts.last.toInt), Real)
  }).toSet.toList

  /**
   *
   * @param s the string to parse into a expr
   * @return The expression.
   */
  def parseBareExpression(s:String) : Option[Expr] = {
    val variables = allVariableOccurances(s)
    val parser = new KeYmaeraParser(false, env)

    val formulaParser = new FormulaParser(variables,Nil,Nil,Nil,Nil).parser
    val exprParser = parser.makeExprParser(variables, Nil, Nil, Nil, Nil)
    parser.parseAll(exprParser, s) match {
      case parser.Success(result, next) => Some(result.asInstanceOf[Expr])
      case f: parser.Failure => throw new IllegalArgumentException(f.toString())
      case e: parser.Error => throw new IllegalArgumentException(e.toString())
      case _ => None //todo actually, pass back an error
    }
  }

  /**
   *
   * @param s the string to parse into a expr
   * @return The expression.
   */
  def parseBareTerm(s:String) : Option[Term] = {
    val variables = allVariableOccurances(s)
    val parser = new KeYmaeraParser(false, env)

    val exprParser = parser.makeTermParser(variables, Nil)
    parser.parseAll(exprParser, s) match {
      case parser.Success(result, next) => Some(result.asInstanceOf[Term])
      case _ => None //todo actually, pass back an error
    }
  }

  def parseBareFormula(s : String) : Option[Formula] = {
    try {
      val expr      = this.parseBareExpression(s).get
      val formula   = expr.asInstanceOf[Formula]
      val variables = allVariableOccurances(s)
      Some(Forall(variables, formula))
    }
    catch {
      case e:Exception => None
    }
  }

  def parseBareFormulaUnquantified(s : String) : Option[Formula] = {
    try {
      val expr      = this.parseBareExpression(s).get
      val formula   = expr.asInstanceOf[Formula]
      Some(formula)
    }
    catch {
      case e:Exception => None
    }
  }

  def runParser(s:String):Expr = {
    val parser = new KeYmaeraParser(enabledLogging, env)

    //Parse file.
    val (functions : List[Function],predicateConstants : List[PredicateConstant], variables : List[Variable],problemText : String) =
      parser.parseAll(parser.fileParser, s) match {
        case parser.Success(result,next) => result
        case f: parser.Failure => throw new IllegalArgumentException(f.toString())
        case f: parser.Error => throw new IllegalArgumentException(f.toString())
      }


    val programs = List[ProgramConstant]() //TODO support these.
    val contEvolvePrograms = List[ContEvolveProgramConstant]()
    
    
    /**
     * The failure message for parse failures during problem parsing.
     */
    def failureMessage(result : String, next : parser.Input):String = {
      "Failed to parse problem (line: " + next.pos.line + ", column: " + next.pos.column + ")\n" +
      "Error message: " + result + " in:\n" + next.pos.longString
    }
    
    //Parse the problem.
    val exprParser = parser.makeExprParser(variables, functions, predicateConstants, programs, contEvolvePrograms)
    val parseResult : Expr = parser.parseAll(exprParser, problemText) match {
        case parser.Success(result,next) => result.asInstanceOf[Expr]
        case parser.Failure(result,next) => throw new Exception(failureMessage(result,next))
        case parser.Error(result,next) => throw new Exception(failureMessage(result,next))
    }
    
    //Ensure that parse( print(parse(problemText)) ) = parse(problemText)
    val printOfParse = KeYmaeraPrettyPrinter.stringify(parseResult)
    checkParser(functions, predicateConstants, variables, programs, contEvolvePrograms, parseResult,printOfParse)
    
    parseResult
  }
  
  
  /**
   * Ensures that parse( print(parse(input)) ) = parse(input)
   */
  @elidable(ASSERTION) 
  def checkParser(functions:List[Function],
    predicateConstants:List[PredicateConstant],
    variables:List[Variable],
    programVariables:List[ProgramConstant],
    contEvolveProgramVariables: List[ContEvolveProgramConstant],
    parse:Expr,
    printOfParse:String) = 
  {
    /* don't let the checking parser communicate with the objects injected into the real parser */
    val parser = new KeYmaeraParser(enabledLogging, new { val generator = new ConfigurableGenerate[Formula]() } )
    val exprParser = parser.makeExprParser(variables, functions, predicateConstants,programVariables, contEvolveProgramVariables)
    try{
      val printofparseParse = parser.parseAll(exprParser, printOfParse) match {
        case parser.Success(result,next) => result
        case parser.Failure(_,_) => throw new Exception("parse failed.")
        case parser.Error(_,_) => throw new Exception("parse error.")
      }
      require(parse.equals(printofparseParse), "Parse not equals parse(pp(parse(_))): " + parse + " != " + printofparseParse )
    }
    catch {
      case e : Exception => require(false, "Parse of print did not succeed on: " + printOfParse + "\nExpected: " +
        KeYmaeraPrettyPrinter.stringify(parse) +
        "\n Exception was: " + e)
    }

  }
   
  import edu.cmu.cs.ls.keymaera.parser.ParseSymbols._
  
  type Tokens = StdLexical
  
  //////////////////////////////////////////////////////////////////////////////
  // Primitive syntactic constructs
  //////////////////////////////////////////////////////////////////////////////
  
  protected override val whiteSpace = 
    """(\s|(?m)\(\*(\*(?!/)|[^*])*\*\)|/\*(.)*?\*/|\/\*[\w\W\s\S\d\D]+?\*\/)+""".r
  protected val space               = """[\ \t\n]*""".r
  
  /**
   * ``ident" should match function, variable and sort names.
   */
  protected val ident               = """[a-zA-Z][a-zA-Z0-9\_]*""".r
  
  protected val everything = """[\w\W\s\S\d\D\n\r]""".r
  
  protected val notDblQuote = """[^\"]*""".r
  
  val lexical = new StdLexical
  //TODO should we add the rest of the \\'s to the delimiters list?
  lexical.delimiters ++= List("{", "}", "(", ")", "+", "-", "*", "/")
  
  //////////////////////////////////////////////////////////////////////////////
  // Function Definition Section.
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Returns a list of defined function sorts.
   */
  lazy val functionsP = {	
    lazy val pattern = ParseSymbols.FUNCTIONS_SECT ~> START_SECT ~> rep1sep(funcdefP, ".") <~ ".".? <~ END_SECT 
    log(pattern)("Functions section") ^^ {
      case  definitions => definitions
    }
  }

  lazy val funcdefP = ParseSymbols.EXTERNAL_FUNCTION.?  ~ ident ~ ident ~ ("(" ~ rep1sep(ident, ",") ~ ")").? ^^ {
    case external ~ rsort ~ name ~ tail =>
      tail match {
        case Some(_ ~ argsorts ~ _) => {
          val isExternal = external match {
            case Some(EXTERNAL_FUNCTION) => true
            case Some(_) => throw new Exception("Parser combinator library is buggy...")
            case None    => false
          }
          val result = {
            val (n, idx) = nameAndIndex(name)
            Function(n, idx, identsToSorts(argsorts), identToSort(rsort))
          }
          if(isExternal) {
            result.markExternal()
          }
          result
        }
        case None => {
          val (n, idx) = nameAndIndex(name)
          PredicateConstant(n, idx)
        }
      }
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // State Variable Definition Section.
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Returns a list of defined program sorts.
   */
  lazy val programVariablesP = {
    lazy val pattern = PVARS_SECT ~> START_SECT ~> rep1sep(vardefP, ".") <~ ".".? <~ END_SECT
    
    log(pattern)("ProgramVariables section.") ^^ {
      case variables => variables
    }
  }

  lazy val vardefP = ident ~ ident ^^ {
    //Note: it might be necessary to give these arguments indices.
    case rsort ~ name => val (n, i) = nameAndIndex(name); Variable(n, i, identToSort(rsort))
  }

  //////////////////////////////////////////////////////////////////////////////
  // Problem Section.
  //////////////////////////////////////////////////////////////////////////////
  lazy val problemP = {
    lazy val textP = ("""[\w\W\s\S\d\D]+""" + END_SECT).r
  
    lazy val pattern = PROBLEM_SECT ~> "." ~> textP 
    log(pattern)("\\problem section (extract a string)") ^^ {
      case programText => programText.replace(END_SECT, "")
    }
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
  
  //////////////////////////////////////////////////////////////////////////////
  // Terms.
  //////////////////////////////////////////////////////////////////////////////
  class TermParser(variables:List[Variable], functions:List[Function]) {
    type SubtermParser = PackratParser[Term]
    
    //TODO-nrf Some of these parsers assign sorts somewhat arbitrarily, and I'm
    //not sure about the correct thing to do. see e.g. subtractP.
    lazy val parser = precedence.reduce(_|_)
    
    val precedence : List[SubtermParser] =
      addP ::
      subtractP ::
      multiplyP ::
      divP ::
      expP ::
      negativeP ::
      applyP ::
      termDerivativeP ::
      variableP :: //real-valued.
      numberP   ::
      identP :: //variableP should match first.
      groupP    ::
      Nil

    /**
     * Terms containing no derivatives.
     * @todo This does not actually work. All this does is make sure the *top level* parser is not a termDerivative.
     *      But, for instance, -x' is still allowed because the negativeP parser does a "tighterThan" on the original
     *      precedence list. In order to fix this problem, we should make this class parameterized by a precedence list.
     */
    lazy val nonDerivativeTermP =   (precedence diff (termDerivativeP::Nil)).reduce(_|_)

    //non-variable ident parser. TODO-nrf add logging and check anything
    //parsed by identP is always bound.
    lazy val identP : PackratParser[Term] = {
      log(ident)("Arbitrary Identifier") ^^ {
        case id =>
          val (n, idx) = nameAndIndex(id)
          Variable(n, idx, Real)
      }
    }

    //variable parser
    lazy val variableP:PackratParser[Term] = {
      lazy val pattern = {
        val stringList =  variables.map(Variable.unapply(_) match {
          case Some((n, i, _)) => n + (i match {case Some(idx) => "_" + idx case None => ""})
          case None => ???
        })
        if(stringList.isEmpty) { """$^""".r/*match nothing.*/ }
        else new scala.util.matching.Regex( stringList.sortWith(_.length > _.length).reduce(_+"|"+_) )
      }
      
      log(pattern)("Variable") ^^ {
        case name => {
          val (n, i) = nameAndIndex(name)
          variables.find(Variable.unapply(_) match {
            case Some((nn, ii, _)) => nn == n && ii == i
            case None => false
          }) match {
            case Some(p) => p
            case None =>
              throw new Exception("Variable was mentioned out of context: " + nameAndIndex(name) + " context: " +
                variables.map(Variable.unapply(_) match { case Some((n, i, _)) => "(" + n + ", " + i + ")" case None => ??? }))
          }
        }
      } 
    }
    
    lazy val termDerivativeP:SubtermParser = {
      lazy val pattern = tighterParsers(precedence, termDerivativeP).reduce(_|_)
      log(pattern ~ PRIME)(PRIME + " parser") ^^ {
        case t ~ PRIME => new Derivative(t.sort, t)
      }
    }
    
    //Compound terms
      
    lazy val multiplyP:SubtermParser = {
      lazy val pattern = leftAssociative(precedence, multiplyP, Some(MULTIPLY))
      log(pattern)("Multiplication") ^^ {
        case left ~ MULTIPLY ~ right => Multiply(left.sort, left,right)
      }
    }
    lazy val divP:SubtermParser = {
      lazy val pattern = leftAssociative(precedence, divP, Some(DIVIDE))
      log(pattern)("Division") ^^ {
        case left ~ DIVIDE ~ right => Divide(left.sort, left,right)
      }
    }
    
    lazy val expP:SubtermParser = {
      lazy val pattern = tighterParsers(precedence,expP).reduce(_|_) ~ EXP ~ asTightAsParsers(precedence,expP).reduce(_|_) 
      log(pattern)("Exponentiation") ^^ {
        case left ~ EXP ~ right => Exp(left.sort, left,right)
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
      lazy val pattern = NEGATIVE ~ asTightAsParsers(precedence, negativeP).reduce(_|_)
      log(pattern)("negate") ^^ {
        case NEGATIVE ~ term => Neg(term.sort, term)
      }
    }
    
    //Function application
    
    lazy val applyP:SubtermParser = {      
      lazy val pattern = ident ~ ("(" ~> rep1sep(tighterParsers(precedence,applyP).reduce(_|_), ",") <~ ")")
      
      log(pattern)("Function Application") ^^ {
        case name ~ args => 
          Apply(functionFromName(name, functions), 
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
        case n => Number.apply(BigDecimal(n))
      }
    }
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // Formulas.
  //////////////////////////////////////////////////////////////////////////////
  class FormulaParser(variables:List[Variable],
      functions:List[Function],
      predicates:List[PredicateConstant],
      programVariables:List[ProgramConstant],
      contEvolveProgramVariables: List[ContEvolveProgramConstant])
  {
    type SubformulaParser = PackratParser[Formula]
    
    lazy val programParser = 
      new ProgramParser(variables,functions,predicates,programVariables,contEvolveProgramVariables).parser

    /**
     * @todo Is this unused? If so, why?
     */
    lazy val termParserObj =
      new TermParser(variables, functions)

    lazy val parser = precedence.reduce(_|_)

    /**
     * This is not included in the precedence list, and is merely a helper for parsing NFContEvolve-form diffeq's.
     * @todo this doesn't work because inside the inditividual parser we acutally use the precendence list. We would
     *      have to make the entire parser parameterized by the precedence list...
     */
    lazy val nonDerivativeFormulaP = (precedence diff formulaDerivativeP::Nil).reduce(_|_)

    val precedence : List[SubformulaParser] =
      equivP ::
      implP  ::
      backwardImplP :: //makes writing axioms less painful.
      orP ::
      andP ::
      notP ::
      boxP :: //Magic: keep box before diamond.
      diamondP ::
      forallP :: 
      existsP :: 
      equalsP ::
      notEqualsP ::
      leP    ::
      geP    ::
      gtP    ::
      ltP    ::  
      formulaDerivativeP ::
      applyPredicateP ::
      predicateP ::
      trueP ::
      falseP ::
      groupP ::
      Nil

    lazy val forallP : PackratParser[Formula] = {      
      lazy val pattern = (FORALL ~> rep1sep(ident,",") <~ ".") ~ asTightAsParsers(precedence, boxP).reduce(_|_)
      log(pattern)("Forall") ^^ {
        case idents ~ formula => {
          val boundVariables = idents.map(str => {val (n, i) = nameAndIndex(str); Variable.apply(n, i, Real)}) //TODO?
          new Forall(boundVariables, formula)
        }
      }
    }
    
    lazy val existsP : PackratParser[Formula] = { 
      lazy val fP = 
        asTightAsParsers(precedence, boxP).reduce(_|_)
      lazy val pattern = (EXISTS ~> rep1sep(ident,",") <~ ".") ~ fP
      log(pattern)("Exists") ^^ {
        case idents ~ formula => {
          val boundVariables = idents.map(str => {val (n, i) = nameAndIndex(str); Variable.apply(n, i, Real)}) //TODO?
          new Exists(boundVariables, formula)
        }
      }
    }
    
    
    lazy val variableP:PackratParser[Term] = {
      lazy val pattern = {
        val stringList =  variables.map(Variable.unapply(_) match {
          case Some(t) => t._1
          case None => ???
        })
        if(stringList.isEmpty) { """$^""".r/*match nothing.*/ }
        else new scala.util.matching.Regex( stringList.sortWith(_.length > _.length).reduce(_+"|"+_) )
      }
      
      log(pattern)("Variable") ^^ {
        case name => {
          variables.find(Variable.unapply(_) match {
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
      
    //Modalities
    lazy val boxP : SubformulaParser = {
      lazy val pattern = BOX_OPEN ~ 
    		  			 programParser ~ 
    		  			 BOX_CLOSE ~ 
    		  			 asTightAsParsers(precedence, boxP).reduce(_|_)
      log(pattern)("box: " + BOX_OPEN + PROGRAM_META + BOX_CLOSE + FORMULA_META) ^^ {
        case BOX_OPEN ~ p ~ BOX_CLOSE ~ f => BoxModality(p,f)
      }
    }
    
    lazy val diamondP : SubformulaParser = {
      lazy val pattern = DIA_OPEN ~ 
                         programParser ~ 
                         DIA_CLOSE ~ 
                         asTightAsParsers(precedence, boxP).reduce(_|_)
      log(pattern)("Diamond: " + DIA_OPEN + PROGRAM_META + DIA_CLOSE + FORMULA_META) ^^ {
        case DIA_OPEN ~ p ~ DIA_CLOSE ~ f => DiamondModality(p,f)
      }
    }

    //Predicate application

    lazy val applyPredicateP:SubformulaParser = {
      lazy val pattern = ident ~ ("(" ~> rep1sep(termParser, ",") <~ ")")

      log(pattern)("Predicate Application") ^^ {
        case name ~ args =>
          ApplyPredicate(functionFromName(name, functions),
            args.reduce( (l,r) => Pair( TupleT(l.sort,r.sort), l, r) ) )
      }
    }

    //Predicates
    lazy val predicateP:SubformulaParser = {
      lazy val pattern = {
        val stringList =  predicates.map(PredicateConstant.unapply(_) match {
          case Some((n, i)) => n + (i match { case Some(idx) => "_" + idx case None => ""})
          case None => ???
        })
        if(stringList.isEmpty) { """$^""".r/*match nothing.*/ }
        else new scala.util.matching.Regex( stringList.sortWith(_.length > _.length).reduce(_+"|"+_) )
      }
      
      log(pattern)("Predicate") ^^ {
        case name => {
          val (n, i) = nameAndIndex(name)
          val p = PredicateConstant(n, i)
          require(predicates.contains(p), "All predicates have to be declared " + p.prettyString() + " not found in " + predicates)
          p
        }
      } 
    }

    //Unary Formulas
    
    lazy val notP:SubformulaParser = {
      lazy val pattern = NEGATE ~ tighterParsers(precedence, notP).reduce(_|_)
      log(pattern)(NEGATE) ^^ {
        case NEGATE ~ f => Not(f.asInstanceOf[Formula])
      }
    }
    
    lazy val formulaDerivativeP:SubformulaParser = {
      log(tighterParsers(precedence, formulaDerivativeP).reduce(_|_) ~ PRIME)("Formula derivative") ^^ {
        case v ~ PRIME => new FormulaDerivative(v)
      }
    }
    
    
    //Binary Formulas
    
    lazy val equivP:SubformulaParser = {
      lazy val pattern = 
        asTightAsParsers(precedence, equivP).reduce(_|_) ~ 
        EQUIV ~
        tighterParsers(precedence, equivP).reduce(_|_)
        
      log(pattern)(EQUIV) ^^ {
        case left ~ _ ~ right => Equiv(left,right)
      }
    }
    
    
    lazy val equalsP:SubformulaParser = {
      lazy val pattern = 
        termParser ~ 
        EQ ~
        termParser 
        
      log(pattern)(EQ + " formula parser (on terms)") ^^ {
        case left ~ _ ~ right => Equals(right.sort,left,right)
      }
    }
    
    lazy val notEqualsP:SubformulaParser = {
      lazy val pattern = 
        termParser ~ 
        NEQ ~
        termParser 
        
      log(pattern)(NEQ + " formula parser (on terms)") ^^ {
        case left ~ _ ~ right => NotEquals(right.sort,left,right)
      }
    }
    
    lazy val implP:SubformulaParser = {
      lazy val pattern = rightAssociative(precedence, implP, Some(ARROW))
      log(pattern)(ARROW) ^^ {
        case left ~ _ ~ right => Imply(left,right)
      }
    }
    
    lazy val backwardImplP:SubformulaParser = {
      lazy val pattern = rightAssociative(precedence, backwardImplP, Some(LARROW))
      log(pattern)(ARROW) ^^ {
        case left ~ _ ~ right => Imply(right,left)
      }
    }
    
    lazy val andP : SubformulaParser = {
      lazy val pattern = leftAssociative(precedence, andP, Some(AND))
      log(pattern)(AND + " parser") ^^ {
        case left ~ _ ~ right => And(left,right)
      }
    }
    
    lazy val orP : SubformulaParser = {
      lazy val pattern = leftAssociative(precedence, orP, Some(OR))
      log(pattern)(OR + " parser") ^^ {
        case left ~ OR ~ right => Or(left,right)
      }
    }
    //Binary Relations

    lazy val termParser = new TermParser(variables,functions).parser
    
    lazy val leP:SubformulaParser = {
      lazy val pattern = termParser ~ LEQ ~ termParser
      log(pattern)(LEQ) ^^ {
        case left ~ LEQ ~ right =>  
          LessEqual(left.sort,left,right)
      }
    }
    
    lazy val geP:SubformulaParser = {
      lazy val pattern = termParser ~ GEQ ~ termParser
      log(pattern)(GEQ) ^^ {
        case left ~ GEQ ~ right =>  
          GreaterEqual(left.sort,left,right)
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
      log(pattern)(LT + " parser") ^^ {
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
      programVariables:List[ProgramConstant],
      contEvolveProgramVariables: List[ContEvolveProgramConstant])
  {
    type SubprogramParser = PackratParser[Program]
    
    //This is not precedence.reduce(_|_) because some things need trailing ;
    //This list should contain precedence in order, with all elements which
    //require a trailing ; removed. These will be captured by sequenceP.
    lazy val parser = choiceP | sequenceP | ifThenElseP | ifThenP | whileP | closureP | groupP

    //@todo do we need to make the program variables into predicates so that they
    //can be assigned to and such? Actually, I think that the stuff in ProgramVariables
    // should all be put into the predicates in the first place because programVariables
    //should only hold variables which hold arbitrary programs.
    val theFormulaParser = new FormulaParser(variables, functions, predicates,programVariables, contEvolveProgramVariables)
    lazy val formulaParser = theFormulaParser.parser

    val theTermParser = new TermParser(variables,functions)
    lazy val termParser = theTermParser.parser
   
    val precedence : List[SubprogramParser] =
      choiceP     ::
      sequenceP   ::
      ifThenElseP ::
      ifThenP     ::
      whileP      ::
      closureP    ::
      assignP     ::
      ndassignP   ::
      incompleteSystemP ::
      normalFormEvolutionSystemP :: //@todo document this.
      evolutionP  :: //@todo should be deprecated; for now we just prefer the normal form where it applies.
      testP       ::
      pvarP       ::
      contEvolvePVarP ::
      groupP      ::
      Nil

   
    lazy val pvarP:PackratParser[ProgramConstant] = {
      lazy val pattern = {
        val stringList =  programVariables.map(ProgramConstant.unapply(_) match {
          case Some((n, i)) => n + (i match {case Some(idx) => "_" + idx case None => ""})
          case None => ???
        })
        if(stringList.isEmpty) { """$^""".r/*match nothing.*/ }
        else new scala.util.matching.Regex( stringList.sortWith(_.length > _.length).reduce(_+"|"+_) )
      }
      
      log(pattern)("Program Variable") ^^ {
        case name => {
          val (n, i) = nameAndIndex(name)
          val p = ProgramConstant(n, i)
          require(programVariables.contains(p), "All program constants have to be declared " + p.prettyString() + " not found in " + programVariables)
          p
        }
      } 
    }

    lazy val contEvolvePVarP:PackratParser[ContEvolveProgramConstant] = {
      lazy val pattern = {
        val stringList =  contEvolveProgramVariables.map(ContEvolveProgramConstant.unapply(_) match {
          case Some((n, i)) => n + (i match {case Some(idx) => "_" + idx case None => ""})
          case None => ???
        })
        if(stringList.isEmpty) { """$^""".r/*match nothing.*/ }
        else new scala.util.matching.Regex( stringList.sortWith(_.length > _.length).reduce(_+"|"+_) )
      }

      log(pattern)("ContEvolveProgram Variable") ^^ {
        case name => {
          val (n, i) = nameAndIndex(name)
          val p = ContEvolveProgramConstant(n, i)
          require(contEvolveProgramVariables.contains(p), "All program constants have to be declared; '" + p.prettyString() + "' not found in " + programVariables)
          p
        }
      }
    }
    
    lazy val sequenceP:SubprogramParser = {
      lazy val pattern = rightAssociativeOptional(precedence,sequenceP,Some(SCOLON))
      log(pattern)("program" + SCOLON + "program") ^^ {
        case left ~ SCOLON ~ right => right match {
          case Some(r) => new Sequence(left,r)
          case None => left
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
      lazy val pattern = "if" ~ ("(" ~> formulaParser <~ ")") ~ "then" ~ parser ~ "fi"
      log(pattern)("if-then") ^^ {
        case "if" ~ f ~ "then" ~ p ~ "fi" => new IfThen(f,p)
      }
    }
    
    lazy val ifThenElseP:SubprogramParser = {
      lazy val pattern =
        "if" ~ ("(" ~> formulaParser <~ ")") ~ "then" ~ 
        parser ~ 
        "else" ~ 
        parser ~
        "fi"
        
      log(pattern)("if-then-else") ^^ {
        case "if" ~ f ~ "then" ~ p1 ~ "else" ~ p2 ~ "fi" => new IfThenElse(f,p1,p2)
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
      lazy val pattern = (tighterParsers(precedence, closureP).reduce(_|_) <~ KSTAR) ~ opt("@invariant" ~> "(" ~> formulaParser <~ ")")
      log(pattern)("*") ^^ {
        case p ~ Some(f) => val result = new Loop(p); env.generator.products += result -> f; result
        case p ~ None => new Loop(p)
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

    //@todo Per December 2014 systems of diff eqs meeting, ContEvolves are temporarily deprecated.
    lazy val evolutionP:SubprogramParser = {
      lazy val pattern = (
                          rep1sep(formulaParser, COMMA) ~
                          AND.? ~ formulaParser.?
                         )
      log(pattern)("Cont Evolution") ^^ {
        case des ~ andOption ~ constraintOption => constraintOption match {
          case Some(constraint) => ContEvolve( And(des.reduceRight(And(_,_)) , constraint) )
          case None => ContEvolve( des.reduceRight(And(_,_)) )
        }
      }
    }

    lazy val normalFormEvolutionSystemP: SubprogramParser = {
      lazy val pattern = rep1sep(contEvolvePVarP | normalFormEvolutionP, COMMA)
      log(pattern)("NFContEvolve (" + COMMA + " NFContEvolve)*") ^^ {
        case odes => odes.foldRight[ContEvolveProgram](EmptyContEvolveProgram()) {
          (a: ContEvolveProgram, b: ContEvolveProgram) => ContEvolveProduct(a, b)
        }
      }
    }

    // Normal form is: f' = g & H.
    lazy val normalFormEvolutionP: PackratParser[NFContEvolve] = {
      /* cannot use AND ~> formulaParser because then x' = y & y' = x because "normal form", even though y' = x is not
       * intended as an ev. dom. constraint
       */
      lazy val pattern = (theTermParser.termDerivativeP <~ EQ) ~ theTermParser.nonDerivativeTermP ~ (AND ~> theFormulaParser.nonDerivativeFormulaP).?

      log(pattern)("NFContEvolve Parser") ^^ {
        case lhs ~ rhs ~ constraintOption => {
          val constraint = constraintOption match {
            case Some(f) => f
            case None    => True
          }
          lhs match {
            case t: Derivative => new NFContEvolve(Nil, t, rhs, constraint)
            case _ => throw new Exception("Expected form f' = g from termDerivativeParser but found non-derivative on LHS when parsing NFContEvolve")
          }
        }
      }
    }

    //@todo find a better term for this construct
    lazy val incompleteSystemP: PackratParser[IncompleteSystem] = { //PackratParser[IncompleteSystem]
      lazy val pattern = START_INCOMPLETE_SYSTEM ~> (normalFormEvolutionSystemP.? <~ END_INCOMPLETE_SYSTEM)

      log(pattern)("Incomplete Differential Equation System") ^^ {
        case system => system match {
          case Some(x: ContEvolveProgram) => IncompleteSystem(x)
          case None => IncompleteSystem()
          case _ => throw new Exception("Expected the interior of an IncompleteSystem to be a continuous evolution, but found: " + system.getClass.getCanonicalName + ".")
        }
      }
    }
    
    lazy val testP:SubprogramParser = {
      lazy val pattern = TEST ~ formulaParser
      log(pattern)(TEST + " parser") ^^ {
        case TEST ~ f => new Test(f)
      }
    }
    
    //this need rewriting, but we dont suppor these at the moment anyway.
//    lazy val pvarP:SubprogramParser = {
//      log(ident)("Program Variable") ^^ {
//        case name => programVariables.find(ProgramConstant.unapply(_) match {
//          case Some(t) => t._1.equals(name)
//          case None    => false
//        }) match {
//          case Some(p ) => p
//          case None     => throw new Exception("Program variable was mentioned but not in context: " + name)
//        }
//      }
//    }
    
    lazy val groupP:SubprogramParser = {
    lazy val pattern = "{" ~> parser <~ "}"
      log(pattern)("Subterm Grouping") ^^ {
        case  p => p
      }
    }
    
  }
  
  /**
   * Gets an expression parser based upon the function and programVariable sections.
   */
  def makeExprParser(variables:List[Variable], functions:List[Function],
      predicates:List[PredicateConstant],programs:List[ProgramConstant],contEvolvePrograms:List[ContEvolveProgramConstant]):PackratParser[Expr] =
  {
    
    lazy val formulaParser = new FormulaParser(variables,functions,predicates,programs,contEvolvePrograms).parser
    lazy val ret = formulaParser ^^ {
      case e => e
    }
    ret
  }

  /**
   * Gets a term parser based upon the function and programVariable sections.
   */
  def makeTermParser(variables:List[Variable], functions:List[Function]):PackratParser[Expr] = {
    lazy val termParser = new TermParser(variables,functions).parser
    lazy val ret = termParser ^^ {
      case e => e
    }
    ret
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
  
  def projectName(v:Variable): Option[(String, Option[Int])] = Variable.unapply(v) match {
    case Some((n, idx, _)) => Some((n, idx))
    case None    => None
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
  // Misc helper methods
  //////////////////////////////////////////////////////////////////////////////
  /**
   * gets a Function object from the ``functions" list with name ``name"
   */
  def functionFromName(name:String, functions:List[Function]) = {
    val (n, idx) = nameAndIndex(name)
    functions.find(Function.unapply(_) match {
      case Some(f) => f._1.equals(n) && f._2.equals(idx)
      case None    => false
    }) match {
      case Some(function) => function
      case None           => throw new Exception("The function " + name + " is used but not declared.")
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////
  // KeYmaera Proof files
  // 	ProofFileParser -- Top-level parser for an axiom/lemma/proof file.
  // 	ProofParser 	-- parser for a LISPy proof.
  //////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////
  object ProofFileParser {
    /**
     * Type of the Variable Parser
     */
    sealed trait VType
      case class VProgram(variable: ProgramConstant) extends VType
      case class VContEvolveProgram(variable: ContEvolveProgramConstant) extends VType
      case class VFormula(variable: PredicateConstant) extends VType
      case class VTerm(variable: Variable) extends VType
      case class VFunction(fn: Function) extends VType
    /**
     * Type of the Axiom and Lemma parsers
     */
    type ALPType = PackratParser[List[LoadedKnowledge]]
    
    /**
     * The main parser
     */
    def runParser(in:String) : List[LoadedKnowledge] = {
      //The parse happens in two phases. First, we parse the program and formula
      //variable definitions. Then, we parse the axioms and lemmas.
      
      val inReader = new PackratReader(new CharSequenceReader(in))
      val (programs, contEvolvePrograms, formulas, terms, funs, nextIn) = parse(firstPassParser, inReader) match {
        case Success(result, next) => (result._1, result._2, result._3, result._4, result._5, next)
        case Failure(msg, next)    => 
          throw new Exception("Failed to parse variables section:"  + msg)
        case Error(msg,next)       =>
          throw new Exception("Error while parsing variables section:" + msg)
      }
      
      def posStr(msg:String, next : Input) = {
        "(line: " + next.pos.line + ", column:" + next.pos.column + ")"
      }
      
      val alParser = makeAxiomLemmaParser(programs, contEvolvePrograms, formulas, terms, funs) //axiomlemmaParser
      val knowledge = parseAll(alParser, nextIn) match {
        case Success(result, next) => result
        case Failure(msg, next)    => 
          throw new Exception("Failed to parse Lemmas & Axioms at " + posStr(msg,next) + ": "  + msg)
        case Error(msg,next)       =>
          throw new Exception("Error while parsing Lemmas & Axioms at " + posStr(msg,next) + ":" + msg)
      }
      knowledge
    }
  
    /**
     * This is the parser for the first pass
     */
    lazy val firstPassParser 
    : PackratParser[(List[ProgramConstant], List[ContEvolveProgramConstant], List[PredicateConstant], List[Variable], List[Function])] =
    {
      lazy val pattern = variablesP
      log(pattern)("Parsing variable declarations in proof file.") ^^ {
        case vars => {
          val programs = vars collect {
            case VProgram(p) => p
          }
          val contEvolvePrograms = vars collect {
            case VContEvolveProgram(p) => p
          }
          val formulas = vars collect {
            case VFormula(f) => f
          }
          val terms = vars collect {
            case VTerm(t) => t
          }
          val funs = vars collect {
            case VFunction(f) => f
          }
          (programs, contEvolvePrograms, formulas, terms, funs)
        }
      }
    }

    /**
     * Maps a string representation of a type and a name to an Expr
     */
    private def makeVariable(ty : String, name : String) : VType = {
      val (n, idx) = nameAndIndex(name)
      ty match {
        case "P" => VProgram(ProgramConstant(n, idx))
        case "CP" => VContEvolveProgram(ContEvolveProgramConstant(n, idx))
        case "F" => VFormula(PredicateConstant(n, idx))
        case "T" => VTerm(Variable(n, idx, Real))
        case _ => throw new Exception("Type " + ty + " is unknown! Expected P (program) or F (formula) or T (term)")
      }
    }

    private def makeFunction(ty : String, name : String, argT: String) : VType = {
      require(argT == "T", "can only handle unary functions")
      val (n, idx) = nameAndIndex(name)
      ty match {
        case "F" => VFunction(Function(n, idx, Real, Bool))
        case "T" => VFunction(Function(n, idx, Real, Real))
        case _ => throw new Exception("Type " + ty + " is unknown! Expected F (formula) or T (term)")
      }
    }

    /**
     * Parses the Variables section of the file
     */
    lazy val variablesP = {
      //e.g., T x or T f(x:T) or T f(x). In the latter case, x:T implicitly.
      // @todo But currently, T f(x:T  y:T) isn't supported. Use product sorts to implement.
      lazy val variablesP = ident ~ ident ~ ("(" ~> rep1sep(ident ~ (":" ~> ident).?, ",") <~ ")").? ^^ {
        //if tail is defined then this is a function; else it's a variable.
        case codomainSort ~ name ~ parameters => parameters match {
          case Some(arguments:List[(String, Option[String])]) => {
            //@todo support product domains sorts. Also requires modifying makeFunction
            require(arguments.length == 1, "don't support functions w/ artiy > 1")
            val domainType = arguments.last._2 match {
              case Some(sort) => sort
              case None => codomainSort
            }
            //@todo NRFb4c but then ultimately just ignore that and use "T" like it wants us to...
//            makeFunction(codomainSort,name,domainType)
            makeFunction(codomainSort, name, "T")
          }
          case Some(_) => throw new Exception("value didn't match expected type.")
          case None => makeVariable(codomainSort.toString, name.toString)
        }
      }
      lazy val pattern =
        VARIABLES_SECT ~> START_SECT ~> ((repsep(variablesP, """\.""".r) <~ ".".?) <~ END_SECT)
      log(pattern)("Variable declarations") ^^ {
        case  variables => variables
      }
    }

    /**
     * Parses the lemmas/axioms section of the file
     */
    def makeAxiomLemmaParser(
          programs : List[ProgramConstant],
          contEvolvePrograms: List[ContEvolveProgramConstant],
          formulas : List[PredicateConstant],
          terms : List[Variable],
          funs: List[Function]) : ALPType  =
    {
      //Names of lemmas and axioms may contain pretty much everything except "
      val alName = notDblQuote
      
      //Create the Formula and Evidence parsers.
      val formulaParser = new FormulaParser(terms,
          funs,
          formulas,
          programs,
          contEvolvePrograms)
      lazy val formulaP = formulaParser.parser
      
      lazy val evidenceP : PackratParser[Evidence] = EvidenceParser.evidenceParser
      
      lazy val axiomParser : ALPType = {
        lazy val pattern = ("Axiom" ~> "\"" ~> alName) ~ 
                           (("\"" ~ ".") ~> repsep(formulaP, ".") <~ ".".?) <~ "End."

        log(pattern)("Axiom Parser") ^^ {
          case name ~ formulas => formulas.map(f => new LoadedAxiom(name, f))
        }
      } 
      
      /**
       * Each Lemma block may have only one formula
       * The result type is a Parser[List[Formula]] so that lemma and axiom parse
       * results are easy to combine.
       */
      lazy val lemmaParser : ALPType = {
        val pattern = ("Lemma" ~> "\"" ~> alName) ~ 
                      (("\"" ~ ".") ~> formulaP <~ "End.") ~ //only allow a single formula.
                      evidenceP.* 

        log(pattern)("Lemma Parser") ^^ {
          case name ~ formula ~ evidence => List(new LoadedLemma(name, formula, evidence))
        }
      }
        
      (axiomParser | lemmaParser).* ^^ { 
        case kList => 
          if(kList.isEmpty) 
            List() 
          else 
            kList.reduce( (left,right) => left ++ right)
      }
    }
  }
  
  object EvidenceParser {
    lazy val evidenceParser = proofParser | 
                              toolInfoParser | 
                              externalInfoParser
    
    lazy val proofParser = {
      lazy val pattern = "Proof." ~> ProofLanguageParser.proofParser <~ "End."
      log(pattern)("Proof evidence") ^^ {
        case proof => new ProofEvidence(proof)
      }
    }
    
    lazy val toolInfoParser = {
      //Parses a single KVP
      lazy val kvpParser =  {
        lazy val pattern = ident ~ ("\"" ~> notDblQuote <~ "\"")
        log(pattern)("Parsing tool info KVP") ^^ {
          case name ~ value => (name, value)
        }
      }
      
      lazy val pattern = "Tool." ~> kvpParser.* <~ "End." 
      
      log(pattern)("KVPs containing tool info") ^^ {
        case kvps => {
          //Translate a list of KVPs into a map (left = key, right = value).
          val toolInfo =
            kvps.foldLeft(Map[String,String]())( (h,kvp) => h + kvp)
          new ToolEvidence(toolInfo )
        }
      }
    }
    
    lazy val externalInfoParser = {
      lazy val pattern = "External." ~> """.*""".r <~ "End."
      log(pattern)("External Proof") ^^ {
        case file => new ExternalEvidence(new File(file))
      }
    }
  }
  
  /**
   * Parser for LISPy serialized proofs. The target of the parse are the Loaded*
   * types in core/ProofFile.scala.
   * 
   * The Lispy syntax follows:
   * (branch "name"
   *    (rule "name" <ruleInfo>)
   *    ...(more rules)...
   * )
   * ...(more branches)...
   * 
   * where <ruleInfo> is like (identifier "value")
   * The identifier of <ruleInfo> rules determines which subtype of 
   * ``LoadedRuleInfo" in generated. This mapping is defined in 
   * LoadedRuleInfo.fromName. 
   */
  object ProofLanguageParser {
    /**
     * Type of parsers for proof steps.
     */
    type StepParser = PackratParser[edu.cmu.cs.ls.keymaera.core.ProofNode.ProofStep]
    
    lazy val parserList = 
      branchP ::
      ruleP ::
      ruleInfoP ::
      Nil
      
    lazy val proofParser = log(branchP.*)("Top-level proof tree parser -- searching for a sequence of branches") ^^ {
      case branches => branches
    }
    
    //branch ::= (branch "name" <expr>?)
    lazy val branchP = {
      lazy val pattern = ("(" ~> "branch" ~> "\"" ~> ident <~ "\"") ~ 
                    ruleP.*  <~ ")"
      log(pattern)("Proof branch parser") ^^ {
        case name ~ rules => new LoadedBranch(name, rules)
      }
    }
    
    lazy val ruleP = {
      lazy val pattern = ("(" ~> "rule" ~> "\"" ~> ident <~ "\"") ~
    		  		ruleInfoP.*
      log(pattern)("Proof rule parser") ^^ {
        case name ~ ruleInfo => new LoadedRule(name, ruleInfo)
      }
    }
    
    lazy val ruleInfoP = {
      lazy val pattern = "(" ~> ident ~
                         ("\"" ~> notDblQuote <~ "\"") <~ ")"
      log(pattern)("Rule Information parser") ^^ {
        case name ~ info => EmptyRule().fromName(name, info)
      }
    }
  }

  def nameAndIndex(id: String): (String, Option[Int]) =
    try {
      val i = id.lastIndexOf("_")
      if(i <= 0) {
        (id, None)
      } else {
        val pref = id.substring(0, i)
        val idxCandidate = id.substring(i + 1)
        (pref, Some(Integer.parseInt(idxCandidate)))
      }
    } catch {
      case e: NumberFormatException => (id, None)
    }

}

// vim: set ts=4 sw=4 et:
