package edu.cmu.cs.ls.keymaerax.parser

import scala.collection.immutable._
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.parser._
import org.scalatest.{PrivateMethodTester, Matchers, FlatSpec}

/**
 * Tests the parser on manually lexed inputs
 * @author aplatzer
 */
class PrelexedParserTests extends FlatSpec with Matchers with PrivateMethodTester {
  val parser = KeYmaeraXParser
  val pp = KeYmaeraXPrettyPrinter

  val x = Variable("x")
  val y = Variable("y")
  val z = Variable("z")

  val f0 = FuncOf(Function("f",None,Unit,Real),Nothing)
  val g0 = FuncOf(Function("g",None,Unit,Real),Nothing)
  val h0 = FuncOf(Function("h",None,Unit,Real),Nothing)

  val f = Function("f",None,Real,Real)
  val g = Function("g",None,Real,Real)
  val h = Function("h",None,Real,Real)

  val p0 = PredOf(Function("p",None,Unit,Bool),Nothing)
  val q0 = PredOf(Function("q",None,Unit,Bool),Nothing)
  val r0 = PredOf(Function("r",None,Unit,Bool),Nothing)

  val p = Function("p",None,Real,Bool)
  val q = Function("q",None,Real,Bool)
  val r = Function("r",None,Real,Bool)

  val f2 = Function("f",None,Tuple(Real,Real),Real)
  val g2 = Function("g",None,Tuple(Real,Real),Real)
  val h2 = Function("h",None,Tuple(Real,Real),Real)

  val p2 = Function("p",None,Tuple(Real,Real),Bool)
  val q2 = Function("q",None,Tuple(Real,Real),Bool)
  val r2 = Function("r",None,Tuple(Real,Real),Bool)

  private def toStream(input: Terminal*): List[Token] = input.toList.map (t=>Token(t, UnknownLocation)) :+ Token(EOF)

  def parseShouldBe(input: String, expr: Expression) = {
    val parse = parser(input)
    if (!(parse == expr)) {
      println(
        "\nInput:      " + input +
        "\nParsed:     " + parse + " @ " + parse.getClass.getSimpleName +
        "\nExpression: " + KeYmaeraXPrettyPrinter.fullPrinter(parse))
      parse should be (expr)
    }
  }



  "After lexing the token parser" should "parse x+y*z" in {
    val lex = KeYmaeraXLexer("x + y * z")
    val theStream = toStream(IDENT("x"), PLUS, IDENT("y"), STAR, IDENT("z"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(theStream) should be
      Plus(Variable("x"), Times(Variable("y"), Variable("z")))
  }

  it should "parse x*y+z" in {
    val lex = KeYmaeraXLexer("x*y+z")
    val theStream = toStream(IDENT("x"), STAR, IDENT("y"), PLUS, IDENT("z"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Plus(Times(Variable("x"), Variable("y")), Variable("z"))
  }

  it should "parse (x*y)+z" in {
    val lex = KeYmaeraXLexer("(x*y)+z")
    val theStream = toStream(LPAREN, IDENT("x"), STAR, IDENT("y"), RPAREN, PLUS, IDENT("z"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Plus(Times(Variable("x"), Variable("y")), Variable("z"))
  }

  it should "parse x*(y+z)" in {
    val lex = KeYmaeraXLexer("x * (y + z)")
    val theStream = toStream(IDENT("x"), STAR, LPAREN, IDENT("y"), PLUS, IDENT("z"), RPAREN)
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Times(Variable("x"), Plus(Variable("y"), Variable("z")))
  }

  it should "parse -x" in {
    val lex = KeYmaeraXLexer("-x")
    val theStream = toStream(MINUS, IDENT("x"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Neg(Variable("x"))
  }

  it should "parse x+y-z" in {
    val lex  = KeYmaeraXLexer("x+y-z")
    val theStream = toStream(IDENT("x"), PLUS, IDENT("y"), MINUS, IDENT("z"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Plus(Variable("x"), Minus(Variable("y"), Variable("z")))
  }

  it should "parse x-y" in {
    val lex = KeYmaeraXLexer("x - y")
    val theStream = toStream(IDENT("x"), MINUS, IDENT("y"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Minus(Variable("x"), Variable("y"))
  }

  it should "parse x+-y" in {
    val lex = KeYmaeraXLexer("x + -y")
    val theStream = toStream(IDENT("x"), PLUS, MINUS, IDENT("y"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Plus(Variable("x"), Neg(Variable("y")))
  }

  it should "parse -x+y" in {
    val lex = KeYmaeraXLexer("-x + y")
    val theStream = toStream(MINUS, IDENT("x"), PLUS, IDENT("y"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(theStream) should be
    Plus(Neg(Variable("x")), Variable("y"))
  }

  it should "parse -x-y" in {
    val lex = KeYmaeraXLexer("-x - y")
    val theStream = toStream(MINUS, IDENT("x"), MINUS, IDENT("y"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Minus(Neg(Variable("x")), Variable("y"))
  }

  it should "parse 2*-y" in {
    val lex = KeYmaeraXLexer("2*-y")
    val theStream = toStream(NUMBER("2"), STAR, MINUS, IDENT("y"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Times(Variable("x"), Neg(Variable("y")))
  }

  it should "could parse -2*y" in {
    val lex = KeYmaeraXLexer("-2 * y")
    val theStream = toStream(MINUS, NUMBER("2"), STAR, IDENT("y"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be (Times(Neg(Number(2)), Variable("y")),
      Times(Number(-2), Variable("y")))
  }

  it should "could lexed parse -2*y" in {
    val lex = KeYmaeraXLexer("-2 * y")
    val theStream = toStream(NUMBER("-2"), STAR, IDENT("y"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Times(Number(-2), Variable("y"))
  }

  //@todo the name of this test doesn't indicate what it's actually testing.
  //@todo disabling for now.
  it should "parse -(2)*y" in {
    val lex = KeYmaeraXLexer("-(2)*y")
    val theStream = toStream(MINUS, LPAREN, NUMBER("2"), RPAREN, STAR, IDENT("y"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(toStream(MINUS, NUMBER("2"), STAR, IDENT("y"))) should be
    Times(Neg(Number(2)), Variable("y"))
  }

  it should "parse x*-y" in {
    val lex = KeYmaeraXLexer("x*-y")
    val theStream = toStream(IDENT("x"), STAR, MINUS, IDENT("y"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Times(Variable("x"), Neg(Variable("y")))
  }

  it should "parse -x*y" in {
    val lex = KeYmaeraXLexer("-x*y")
    val theStream = toStream(MINUS, IDENT("x"), STAR, IDENT("y"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Neg(Times(Variable("x"), Variable("y")))
  }

  it should "parse x/-y" in {
    val lex = KeYmaeraXLexer("x/-y")
    val theStream = toStream(IDENT("x"), SLASH, MINUS, IDENT("y"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Divide(Variable("x"), Neg(Variable("y")))
  }

  it should "parse -x/y" in {
    val lex = KeYmaeraXLexer("-x/y")
    val theStream = toStream(MINUS, IDENT("x"), SLASH, IDENT("y"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Neg(Divide(Variable("x"), Variable("y")))
  }

  it should "parse x^-y" in {
    val lex = KeYmaeraXLexer("x^-y")
    val theStream = toStream(IDENT("x"), POWER, MINUS, IDENT("y"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Power(Variable("x"), Neg(Variable("y")))
  }

  it should "parse -x^y" in {
    val lex = KeYmaeraXLexer("-x^y")
    val theStream = toStream(MINUS, IDENT("x"), POWER, IDENT("y"))
    toStream(IDENT("x"), POWER, MINUS, IDENT("y"))
    parser.parse(lex) should be
    Neg(Power(Variable("x"), Variable("y")))
  }

  it should "parse x-y+z" in {
    val lex = KeYmaeraXLexer("x-y+z")
    val theStream = toStream(IDENT("x"), MINUS, IDENT("y"), PLUS, IDENT("z"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Plus(Minus(Variable("x"), Variable("y")), Variable("z"))
  }

  it should "parse x-(y+z)" in {
    val lex = KeYmaeraXLexer("x - (y+z)")
    val theStream = toStream(IDENT("x"), MINUS, LPAREN, IDENT("y"), PLUS, IDENT("z"), RPAREN)
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Minus(Variable("x"), Minus(Variable("y"), Variable("z")))
  }

  it should "parse x-y*z+a*b" in {
    val lex = KeYmaeraXLexer("x-y*z+a*b")
    val theStream =
      toStream(IDENT("x"), MINUS, IDENT("y"), STAR, IDENT("z"), PLUS, IDENT("a"), STAR, IDENT("b"))
    lex.map(_.tok) should be (theStream.map(_.tok))

    parser.parse(lex) should be
    Plus(Minus(Variable("x"), Times(Variable("y"), Variable("z"))), Times(Variable("a"), Variable("b")))
  }

  it should "parse x-y+z*a/b^c" in {
    val lex = KeYmaeraXLexer("x-y+z*a/b^c")
    val theStream = toStream(IDENT("x"), MINUS, IDENT("y"), PLUS, IDENT("z"), STAR, IDENT("a"), SLASH, IDENT("b"), POWER, IDENT("c"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Plus(Minus(Variable("x"), Variable("y")), Times(Variable("z"), Divide(Variable("a"), Power(Variable("b"), Variable("c")))))
  }

  it should "parse p()&q()|r()" in {
    val lex = KeYmaeraXLexer("p()&q()|r()")
    val theStream = toStream(IDENT("p"), LPAREN, RPAREN, AMP, IDENT("q"), LPAREN, RPAREN, OR, IDENT("r"), LPAREN, RPAREN)
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Or(And(p0, q0), r0)
  }

  it should "parse f()>0&g()<=2|r()<1" in {
    val lex = KeYmaeraXLexer("f()>0&g()<=2|r()<1")
    val theStream =
      toStream(IDENT("f"), LPAREN, RPAREN, RDIA, NUMBER("0"), AMP, IDENT("g"), LPAREN, RPAREN, LESSEQ, NUMBER("2"), OR, IDENT("r"), LPAREN, RPAREN, LDIA, NUMBER("1"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Or(And(Greater(f0, Number(0)), LessEqual(g0, Number(2))), Less(h0, Number(1)))
  }

  //@todo I have no idea where this stream came from...
  it should "parse f()>0&g(x)<=2|r()<1" in {
    val lex = KeYmaeraXLexer("f()>0&g(x)<=2|r()<1")
    val theStream =
      toStream(IDENT("f"), LPAREN, RPAREN, RDIA, NUMBER("0"), AMP, IDENT("g"), LPAREN, IDENT("x"), RPAREN, LESSEQ, NUMBER("2"), OR, IDENT("r"), LPAREN, RPAREN, LDIA, NUMBER("1"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Or(And(Greater(f0, Number(0)), LessEqual(FuncOf(Function("g",None,Real,Real),Variable("x")), Number(2))), Less(h0, Number(1)))
  }

  it should "parse x>0 & y<1 | z>=2" in {
    val lex = KeYmaeraXLexer("x>0 & y<1 | z>=2")
    val theStream =
      toStream(IDENT("x"), RDIA, NUMBER("0"), AMP, IDENT("y"), LDIA, NUMBER("1"), OR, IDENT("z"), GREATEREQ, NUMBER("2"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Or(And(Greater(Variable("x"), Number(0)), Less(Variable("y"),Number(1))), GreaterEqual(Variable("z"), Number(2)))
  }

  it should "parse x:=y+1;++z:=0;" in {
    val lex = KeYmaeraXLexer("x:=y+1;++z:=0;")
    val theStream = toStream(IDENT("x"), ASSIGN, IDENT("y"), PLUS, NUMBER("1"), SEMI, CHOICE, IDENT("z"), ASSIGN, NUMBER("0"), SEMI)
    lex.map(_.tok) should be (theStream.map(_.tok))
    if (OpSpec.statementSemicolon) parser.parse(lex) should be
    Choice(Assign(Variable("x"), Plus(Variable("y"),Number(1))), Assign(Variable("z"), Number(0)))
  }

  it should "parse x:=y+1;z:=0" in {
    val lex = KeYmaeraXLexer("x:=y+1;z:=0")
    val theStream = toStream(IDENT("x"), ASSIGN, IDENT("y"), PLUS, NUMBER("1"), SEMI, IDENT("z"), ASSIGN, NUMBER("0"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    if (!OpSpec.statementSemicolon) parser.parse(lex) should be
    Compose(Assign(Variable("x"), Plus(Variable("y"),Number(1))), Assign(Variable("z"), Number(0)))
  }

  it should "parse x:=y+1;z:=0;" in {
    val lex = KeYmaeraXLexer("x:=y+1;z:=0;")
    val theStream = toStream(IDENT("x"), ASSIGN, IDENT("y"), PLUS, NUMBER("1"), SEMI, IDENT("z"), ASSIGN, NUMBER("0"), SEMI)
    lex.map(_.tok) should be (theStream.map(_.tok))
    if (OpSpec.statementSemicolon) parser.parse(lex) should be
    Compose(Assign(Variable("x"), Plus(Variable("y"),Number(1))), Assign(Variable("z"), Number(0)))
  }

  it should "parse x-y'+z" in {
    val lex = KeYmaeraXLexer("x-y'+z")
    val theStream = toStream(IDENT("x"), MINUS, IDENT("y"), PRIME, PLUS, IDENT("z"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Plus(Minus(Variable("x"), DifferentialSymbol(Variable("y"))), Variable("z"))
  }

  it should "parse x-(y)'+z" in {
    val lex = KeYmaeraXLexer("x-(y)'+z")
    val theStream = toStream(IDENT("x"), MINUS, LPAREN, IDENT("y"), RPAREN, PRIME, PLUS, IDENT("z"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Plus(Minus(Variable("x"), Differential(Variable("y"))), Variable("z"))
  }

  it should "parse (x-y)'+z" in {
    val lex = KeYmaeraXLexer("(x-y)'+z")
    val theStream = toStream(LPAREN, IDENT("x"), MINUS, IDENT("y"), RPAREN, PRIME, PLUS, IDENT("z"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Plus(Differential(Minus(Variable("x"), Variable("y"))), Variable("z"))
  }

  it should "parse [x:=y+1]x>=0" in {
    val lex = KeYmaeraXLexer("[x:=y+1]x>=0")
    val theStream = toStream(LBOX, IDENT("x"), ASSIGN, IDENT("y"), PLUS, NUMBER("1"), RBOX, IDENT("x"), GREATEREQ, NUMBER("0"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    if (!OpSpec.statementSemicolon) parser.parse(lex) should be
    Box(Assign(Variable("x"), Plus(Variable("y"),Number(1))), GreaterEqual(Variable("x"), Number(0)))
  }

  it should "parse [x:=y+1;]x>=0" in {
    val lex = KeYmaeraXLexer("[x:=y+1;]x>=0")
    val theStream = toStream(LBOX, IDENT("x"), ASSIGN, IDENT("y"), PLUS, NUMBER("1"), SEMI, RBOX, IDENT("x"), GREATEREQ, NUMBER("0"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    if (OpSpec.statementSemicolon)  parser.parse(lex) should be
    Box(Assign(Variable("x"), Plus(Variable("y"),Number(1))), GreaterEqual(Variable("x"), Number(0)))
  }

  it should "parse [{x'=y+1}]x>=0" in {
    val lex = KeYmaeraXLexer("[{x'=y+1}]x>=0")
    val theStream = toStream(LBOX, LBRACE, IDENT("x"), PRIME, EQ, IDENT("y"), PLUS, NUMBER("1"), RBRACE, RBOX, IDENT("x"), GREATEREQ, NUMBER("0"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    if (OpSpec.statementSemicolon)  parser.parse(lex) should be
    Box(ODESystem(AtomicODE(DifferentialSymbol(Variable("x")), Plus(Variable("y"),Number(1))), True), GreaterEqual(Variable("x"), Number(0)))
  }

  it should "parse [{x'=y+1,y'=5}]x>=0" in {
    val lex = KeYmaeraXLexer("[{x'=y+1,y'=5}]x>=0")
    val theStream =
      toStream(LBOX, LBRACE, IDENT("x"), PRIME, EQ, IDENT("y"), PLUS, NUMBER("1"), COMMA, IDENT("y"), PRIME, EQ, NUMBER("5"), RBRACE, RBOX, IDENT("x"), GREATEREQ, NUMBER("0"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    if (OpSpec.statementSemicolon)  parser.parse(lex) should be
    Box(ODESystem(DifferentialProduct(AtomicODE(DifferentialSymbol(Variable("x")), Plus(Variable("y"),Number(1))),
      AtomicODE(DifferentialSymbol(Variable("y")), Number(5))), True), GreaterEqual(Variable("x"), Number(0)))
  }

  it should "parse [{x'=1&x>2}]x>=0" in {
    val lex = KeYmaeraXLexer("[{x'=1&x>2}]x>=0")
    val theStream = toStream(LBOX, LBRACE, IDENT("x"), PRIME, EQ, NUMBER("1"), AMP, IDENT("x"), RDIA, NUMBER("2"), RBRACE, RBOX, IDENT("x"), GREATEREQ, NUMBER("0"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    if (OpSpec.statementSemicolon)  parser.parse(lex) should be
    Box(ODESystem(AtomicODE(DifferentialSymbol(Variable("x")), Number(1)), Greater(Variable("x"),Number(2))), GreaterEqual(Variable("x"), Number(0)))
  }

  it should "parse [{x'=y+1&x>0}]x>=0" in {
    val lex = KeYmaeraXLexer("[{x'=y+1&x>0}]x>=0")
    val theStream =
      toStream(LBOX, LBRACE, IDENT("x"), PRIME, EQ, IDENT("y"), PLUS, NUMBER("1"), AMP, IDENT("x"), RDIA, NUMBER("0"), RBRACE, RBOX, IDENT("x"), GREATEREQ, NUMBER("0"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    if (OpSpec.statementSemicolon)  parser.parse(lex) should be
    Box(ODESystem(AtomicODE(DifferentialSymbol(Variable("x")), Plus(Variable("y"),Number(1))), Greater(Variable("x"),Number(0))), GreaterEqual(Variable("x"), Number(0)))
  }

  it should "parse [{x'=y+1,y'=5&x>y}]x>=0" in {
    if (OpSpec.statementSemicolon) {
      val lex = KeYmaeraXLexer("[{x'=y+1,y'=5&x>y}]x>=0")
      val theStream = toStream(LBOX, LBRACE, IDENT("x"), PRIME, EQ, IDENT("y"), PLUS, NUMBER("1"), COMMA, IDENT("y"), PRIME, EQ, NUMBER("5"), AMP, IDENT("x"), RDIA, IDENT("y"), RBRACE, RBOX, IDENT("x"), GREATEREQ, NUMBER("0"))
      lex.map(_.tok) should be (theStream.map(_.tok))
      parser.parse(lex) should be
      Box(ODESystem(DifferentialProduct(AtomicODE(DifferentialSymbol(Variable("x")), Plus(Variable("y"), Number(1))),
        AtomicODE(DifferentialSymbol(Variable("y")), Number(5))), Greater(Variable("x"), Variable("y"))), GreaterEqual(Variable("x"), Number(0)))
    }
  }

  it should "parse [a]x>=0" in {
    if (!OpSpec.statementSemicolon)  {
      val lex = KeYmaeraXLexer("[a]x>=0")
      val theStream = toStream(LBOX, IDENT("a"), RBOX, IDENT("x"), GREATEREQ, NUMBER("0"))
      lex.map(_.tok) should be (theStream.map(_.tok))
      parser.parse(lex) should be
      Box(ProgramConst("a"), GreaterEqual(Variable("x"), Number(0)))
    }

  }

  it should "parse [a;]x>=0" in {
    if (OpSpec.statementSemicolon) {
      val lex = KeYmaeraXLexer("[a;]x>=0")
      val theStream = toStream(LBOX, IDENT("a"), SEMI, RBOX, IDENT("x"), GREATEREQ, NUMBER("0"))
      lex.map(_.tok) should be (theStream.map(_.tok))
      parser.parse(lex) should be
      Box(ProgramConst("a"), GreaterEqual(Variable("x"), Number(0)))
    }
  }

  it should "parse <a>x>0" in {

    if (!OpSpec.statementSemicolon) {
      val lex = KeYmaeraXLexer("<a>x>0")
      val theStream = toStream(LDIA, IDENT("a"), RDIA, IDENT("x"), RDIA, NUMBER("0"))
      lex.map(_.tok) should be (theStream.map(_.tok))
      parser.parse(lex) should be
      Diamond(ProgramConst("a"), Greater(Variable("x"), Number(0)))
    }
  }

  it should "parse <a;>x>0" in {
    val lex = KeYmaeraXLexer("<a;>x>0")
    val theStream = toStream(LDIA, IDENT("a"), SEMI, RDIA, IDENT("x"), RDIA, NUMBER("0"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    if (OpSpec.statementSemicolon)  parser.parse(lex) should be
    Diamond(ProgramConst("a"), Greater(Variable("x"), Number(0)))
  }

  it should "parse [a;b]x>=0" in {
    if (!OpSpec.statementSemicolon) {
      val lex = KeYmaeraXLexer("[a;b]x>=0")
      val theStream = toStream(LBOX, IDENT("a"), SEMI, IDENT("b"), RBOX, IDENT("x"), GREATEREQ, NUMBER("0"))
      lex.map(_.tok) should be (theStream.map(_.tok))
      parser.parse(lex) should be
      Box(Compose(ProgramConst("a"), ProgramConst("b")), GreaterEqual(Variable("x"), Number(0)))
    }
  }

  it should "parse [a;b;]x>=0" in {
    if (OpSpec.statementSemicolon) {
      val lex = KeYmaeraXLexer("[a;b;]x>=0")
      val theStream = toStream(LBOX, IDENT("a"), SEMI, IDENT("b"), SEMI, RBOX, IDENT("x"), GREATEREQ, NUMBER("0"))
      lex.map(_.tok) should be (theStream.map(_.tok))
      parser.parse(lex) should be
      Box(Compose(ProgramConst("a"), ProgramConst("b")), GreaterEqual(Variable("x"), Number(0)))
    }
  }

  it should "parse <a;b>x>0" in {
    if (!OpSpec.statementSemicolon) {
      val lex = KeYmaeraXLexer("<a;b>x>0")
      val theStream = toStream(LDIA, IDENT("a"), SEMI, IDENT("b"), RDIA, IDENT("x"), RDIA, NUMBER("0"))
      lex.map(_.tok) should be (theStream.map(_.tok))
      parser.parse(lex) should be
      Diamond(Compose(ProgramConst("a"), ProgramConst("b")), Greater(Variable("x"), Number(0)))
    }
  }

  it should "parse [a;]p(x)" in {
    if (OpSpec.statementSemicolon) {
      val lex = KeYmaeraXLexer("[a;]p(x)")
      val theStream = toStream(LBOX, IDENT("a"), SEMI, RBOX, IDENT("p"), LPAREN, IDENT("x"), RPAREN)
      lex.map(_.tok) should be (theStream.map(_.tok))
      parser.parse(lex) should be
      Box(ProgramConst("a"), PredOf(p, Variable("x")))
    }

  }

  it should "parse [a;b;]p(x)" in {
    if (OpSpec.statementSemicolon) {
      val lex = KeYmaeraXLexer("[a;b;]p(x)")
      val theStream = toStream(LBOX, IDENT("a"), SEMI, IDENT("b"), SEMI, RBOX, IDENT("p"), LPAREN, IDENT("x"), RPAREN)
      lex.map(_.tok) should be (theStream.map(_.tok))
      parser.parse(lex) should be
      Box(Compose(ProgramConst("a"), ProgramConst("b")), PredOf(p, Variable("x")))
    }
  }

  it should "parse <a;b;>p(x)" in {
    val lex = KeYmaeraXLexer("<a;b;>p(x)")
    val theStream = toStream(LDIA, IDENT("a"), SEMI, IDENT("b"), SEMI, RDIA, IDENT("p"), LPAREN, IDENT("x"), RPAREN)
    lex.map(_.tok) should be (theStream.map(_.tok))
    if (OpSpec.statementSemicolon) {
      parser.parse(lex) should be
      Diamond(Compose(ProgramConst("a"), ProgramConst("b")), PredOf(p, Variable("x")))
    }
  }

  it should "parse <a;b;>x>0" in {
    if (OpSpec.statementSemicolon) {
      val lex = KeYmaeraXLexer("<a;b;>x>0")
      val theStream = toStream(LDIA, IDENT("a"), SEMI, IDENT("b"), SEMI, RDIA, IDENT("x"), RDIA, NUMBER("0"))
      lex.map(_.tok) should be(theStream.map(_.tok))
      parser.parse(lex) should be
      Diamond(Compose(ProgramConst("a"), ProgramConst("b")), Greater(Variable("x"), Number(0)))
    }
  }

  it should "parse [a++b]x>=0" in {
    if (!OpSpec.statementSemicolon) {
      val lex = KeYmaeraXLexer("[a++b]x>=0")
      val theStream = toStream(LBOX, IDENT("a"), CHOICE, IDENT("b"), RBOX, IDENT("x"), GREATEREQ, NUMBER("0"))
      lex.map(_.tok) should be(theStream.map(_.tok))
      parser.parse(lex) should be
      Box(Choice(ProgramConst("a"), ProgramConst("b")), GreaterEqual(Variable("x"), Number(0)))
    }
  }

  it should "parse [a;++b;]x>=0" in {
    if (OpSpec.statementSemicolon) {
      val lex = KeYmaeraXLexer("[a;++b;]x>=0")
      val theStream = toStream(LBOX, IDENT("a"), SEMI, CHOICE, IDENT("b"), SEMI, RBOX, IDENT("x"), GREATEREQ, NUMBER("0"))
      lex.map(_.tok) should be(theStream.map(_.tok))
      parser.parse(lex) should be
      Box(Choice(ProgramConst("a"), ProgramConst("b")), GreaterEqual(Variable("x"), Number(0)))
    }
  }

  it should "parse <a++b>x>0" in {
    if (!OpSpec.statementSemicolon) {
      val lex = KeYmaeraXLexer("<a++b>x>0")
      val theStream = toStream(LDIA, IDENT("a"), CHOICE, IDENT("b"), RDIA, IDENT("x"), RDIA, NUMBER("0"))
      lex.map(_.tok) should be(theStream.map(_.tok))
      parser.parse(lex) should be
      Diamond(Choice(ProgramConst("a"), ProgramConst("b")), Greater(Variable("x"), Number(0)))
    }
  }

  it should "parse <a;++b;>x>0" in {
    if (OpSpec.statementSemicolon) {
      val lex = KeYmaeraXLexer("<a;++b;>x>0")
      val theStream = toStream(LDIA, IDENT("a"), SEMI, CHOICE, IDENT("b"), SEMI, RDIA, IDENT("x"), RDIA, NUMBER("0"))
      lex.map(_.tok) should be(theStream.map(_.tok))
      parser.parse(lex) should be
      Diamond(Choice(ProgramConst("a"), ProgramConst("b")), Greater(Variable("x"), Number(0)))
    }
  }

  // pathetic cases

  "After lexing pathetic input the parser"  should "parse (((p())))&q()" in {
    val lex = KeYmaeraXLexer("(((p())))&q()")
    val theStream = toStream(LPAREN, LPAREN, LPAREN, IDENT("p"), LPAREN, RPAREN, RPAREN, RPAREN, RPAREN, AMP, IDENT("q"), LPAREN, RPAREN)
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    And(p0, q0)
  }

  it should "parse p()&(((q())))" in {
    val lex = KeYmaeraXLexer("p()&(((q())))")
    val theStream = toStream(IDENT("p"), LPAREN, RPAREN, AMP, LPAREN, LPAREN, LPAREN, IDENT("q"), LPAREN, RPAREN, RPAREN, RPAREN, RPAREN)
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    And(p0, q0)
  }

  it should "parse (((f())))+g()>=0" in {
    val lex = KeYmaeraXLexer("(((f())))+g()>=0")
    val theStream = toStream(LPAREN, LPAREN, LPAREN, IDENT("f"), LPAREN, RPAREN, RPAREN, RPAREN, RPAREN, PLUS, IDENT("g"), LPAREN, RPAREN, GREATEREQ, NUMBER("0"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    GreaterEqual(Plus(f0, g0), Number(0))
  }

  it should "parse 0<=f()+(((g())))" in {
    val lex = KeYmaeraXLexer("0<=f()+(((g())))")
    val theStream = toStream(NUMBER("0"), LESSEQ, IDENT("f"), LPAREN, RPAREN, PLUS, LPAREN, LPAREN, LPAREN, IDENT("g"), LPAREN, RPAREN, RPAREN, RPAREN, RPAREN)
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    LessEqual(Number(0), Plus(f0, g0))
  }

  it should "default to term when trying to parse p()" in {
    val lex = KeYmaeraXLexer("p()")
    val theStream = toStream(IDENT("p"), LPAREN, RPAREN)
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    FuncOf(Function("p",None,Unit,Real), Nothing)
    //a [ParseException] should be thrownBy
  }

  it should "default to formula when trying to parse x'=5" in {
    val lex = KeYmaeraXLexer("x' = 5")
    val theStream = toStream(IDENT("x"), PRIME, EQ, NUMBER("5"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Equal(DifferentialSymbol(Variable("x")), Number(5))
  }

  it should "parse f()>0" in {
    val lex = KeYmaeraXLexer("f()>0")
    val theStream = toStream(IDENT("f"), LPAREN, RPAREN, RDIA, NUMBER("0"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Greater(f0, Number(0))
  }

  "The string parser" should "not parse p()+x as a formula" in {
    a [ParseException] should be thrownBy parser.formulaParser("p()+x")
  }

  it should "not parse f()&x>0 as a term" in {
    a [ParseException] should be thrownBy parser.termParser("f()&x>0")
  }

  it should "parse a term when trying to parse p() as a term" in {
    parser.termParser("p()") should be (FuncOf(Function("p",None,Unit,Real), Nothing))
  }

  it should "parse a term when trying to parse p(x) as a term" in {
    parser.termParser("p(x)") should be (FuncOf(Function("p",None,Real,Real), Variable("x")))
  }

  it should "parse a formula when trying to parse p() as a formula" in {
    parser.formulaParser("p()") should be (PredOf(Function("p",None,Unit,Bool), Nothing))
  }

  it should "parse a formula when trying to parse p(x) as a formula" in {
    parser.formulaParser("p(x)") should be (PredOf(Function("p",None,Real,Bool), Variable("x")))
  }

  it should "default to formula when trying to parse x'=5" in {
    val lex = KeYmaeraXLexer("x' = 5")
    val theStream = toStream(IDENT("x"), PRIME, EQ, NUMBER("5"))
    lex.map(_.tok) should be (theStream.map(_.tok))
    parser.parse(lex) should be
    Equal(DifferentialSymbol(Variable("x")), Number(5))
    parser("x'=5") should be (Equal(DifferentialSymbol(Variable("x")), Number(5)))
  }

  it should "parse a formula when trying to parse x'=5 as a formula" in {
    parser.formulaParser("x'=5") should be (Equal(DifferentialSymbol(Variable("x")), Number(5)))
  }

  it should "default to formula when trying to parse x'=5&x>2" in {
    parser("x'=5&x>2") should be (And(Equal(DifferentialSymbol(Variable("x")), Number(5)), Greater(Variable("x"),Number(2))))
  }

  ignore should "probably not parse a simple program when trying to parse x'=5 as a program" in {
    parser.programParser("x'=5") should not be (AtomicODE(DifferentialSymbol(Variable("x")), Number(5)))
  }

  it should "perhaps parse an ODESystem program from [x'=5;]p(x) if parsed at all" in {
    try {
      parser("[x'=5;]p(x)") should be (Box(ODESystem(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), True), PredOf(p, Variable("x"))))
    } catch {
      case ignore: ParseException =>
    }
  }

  it should "perhaps parse an ODESystem program from <x'=5;>p(x) if parsed at all" in {
    try {
      parser("<x'=5;>p(x)") should be (Diamond(ODESystem(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), True), PredOf(p, Variable("x"))))
    } catch {
      case ignore: ParseException =>
    }
  }

  it should "parse [{x'=5&x>7}]p(x)" in {
    parser("[{x'=5&x>7}]p(x)") should be (Box(ODESystem(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), Greater(Variable("x"),Number(7))), PredOf(p, Variable("x"))))
  }

  it should "parse [{x'=5,y'=2}]p(x)" in {
    parser("[{x'=5,y'=2}]p(x)") should be (Box(ODESystem(DifferentialProduct(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), AtomicODE(DifferentialSymbol(Variable("y")), Number(2))), True), PredOf(p, Variable("x"))))
  }

  it should "parse [{y'=2,x'=5}]p(x)" in {
    parser("[{y'=2,x'=5}]p(x)") should be (Box(ODESystem(DifferentialProduct(AtomicODE(DifferentialSymbol(Variable("y")), Number(2)), AtomicODE(DifferentialSymbol(Variable("x")), Number(5))), True), PredOf(p, Variable("x"))))
  }

  it should "parse [{x'=5,y'=2&x>7}]p(x)" in {
    parser("[{x'=5,y'=2&x>7}]p(x)") should be (Box(ODESystem(DifferentialProduct(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), AtomicODE(DifferentialSymbol(Variable("y")), Number(2))), Greater(Variable("x"),Number(7))), PredOf(p, Variable("x"))))
  }

  it should "parse long evolution domains [{x'=5,y'=2&x>7->y<2}]p(x)" in {
    parser("[{x'=5,y'=2&x>7->y<2}]p(x)") should be (Box(ODESystem(DifferentialProduct(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), AtomicODE(DifferentialSymbol(Variable("y")), Number(2))), Imply(Greater(Variable("x"),Number(7)),Less(y,Number(2)))), PredOf(p, Variable("x"))))
  }

  it should "parse long evolution domains [{x'=5,y'=2&x>7&y<2}]p(x)" in {
    parser("[{x'=5,y'=2&x>7&y<2}]p(x)") should be (Box(ODESystem(DifferentialProduct(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), AtomicODE(DifferentialSymbol(Variable("y")), Number(2))), And(Greater(Variable("x"),Number(7)),Less(y,Number(2)))), PredOf(p, Variable("x"))))
  }

  it should "parse long evolution domains [{x'=5,y'=2&x>7|y<8}]p(x)" in {
    parser("[{x'=5,y'=2&x>7|y<8}]p(x)") should be (Box(ODESystem(DifferentialProduct(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), AtomicODE(DifferentialSymbol(Variable("y")), Number(2))), Or(Greater(Variable("x"),Number(7)),Less(y,Number(8)))), PredOf(p, Variable("x"))))
  }

  it should "parse long evolution domains [{x'=5,y'=2&!x>7}]p(x)" in {
    parser("[{x'=5,y'=2&!x>7}]p(x)") should be (Box(ODESystem(DifferentialProduct(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), AtomicODE(DifferentialSymbol(Variable("y")), Number(2))), Not(Greater(Variable("x"),Number(7)))), PredOf(p, Variable("x"))))
  }

  it should "parse lexically disambiguated x< -y not as REVIMPLY" in {
    parser("x< -y") should be (Less(x,Neg(y)))
    parser(pp(Less(x,Neg(y)))) should be (Less(x,Neg(y)))
  }

  it should "parse [x:=q();]f()->r()+c(x)>0" in {
    parser("[x:=q();]f()->r()+c(x)>0") should be (Imply(
      Box(Assign(x, FuncOf(Function("q",None,Unit,Real),Nothing)), PredOf(Function("f",None,Unit,Bool),Nothing)),
      Greater(Plus(FuncOf(Function("r",None,Unit,Real),Nothing), FuncOf(Function("c",None,Real,Real),x)), Number(0))))
  }

  it should "parse [x:=q(x);]f(x)->r(x)+c(x)>0" in {
    parser("[x:=q(x);]f(x)->r(x)+c(x)>0") should be (Imply(
      Box(Assign(x, FuncOf(Function("q",None,Real,Real),x)), PredOf(Function("f",None,Real,Bool),x)),
      Greater(Plus(FuncOf(Function("r",None,Real,Real),x), FuncOf(Function("c",None,Real,Real),x)), Number(0))))
  }

  it should "parse [x:=q(??);]f(??)->r(??)+c(x)>0" in {
    parser("[x:=q(??);]f(??)->r(??)+c(x)>0") should be (Imply(
      Box(Assign(x, FuncOf(Function("q",None,Real,Real),Anything)), PredOf(Function("f",None,Real,Bool),Anything)),
      Greater(Plus(FuncOf(Function("r",None,Real,Real),Anything), FuncOf(Function("c",None,Real,Real),x)), Number(0))))
  }

  it should "parse [x:=q();]f()->g()" in {
    parseShouldBe("[x:=q();]f()->g()", Imply(
      Box(Assign(x, FuncOf(Function("q",None,Unit,Real),Nothing)), PredOf(Function("f",None,Unit,Bool),Nothing)),
      PredOf(Function("g",None,Unit,Bool),Nothing)))
  }

  it should "parse [x:=q(x);]f(x)->g(x)" in {
    parser("[x:=q(x);]f(x)->g(x)") should be (Imply(
      Box(Assign(x, FuncOf(Function("q",None,Real,Real),x)), PredOf(Function("f",None,Real,Bool),x)),
      PredOf(Function("g",None,Real,Bool),x)))
  }

  it should "parse [x:=q(??);]f(??)->g(??)" in {
    parser("[x:=q(??);]f(??)->g(??)") should be (Imply(
      Box(Assign(x, FuncOf(Function("q",None,Real,Real),Anything)), PredOf(Function("f",None,Real,Bool),Anything)),
      PredOf(Function("g",None,Real,Bool),Anything)))
  }

  ignore should "parse an ODESystem program when trying to parse x'=5 as a program" in {
    parser.programParser("x'=5") should be (ODESystem(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), True))
  }

  it should "parse a formula when trying to parse x'=5&x>2 as a formula" in {
    parser.formulaParser("x'=5&x>2") should be (And(Equal(DifferentialSymbol(Variable("x")), Number(5)), Greater(Variable("x"),Number(2))))
  }

  it should "parse a program when trying to parse x'=5&x>2 as a program" in {
    parser.programParser("x'=5&x>2") should be (ODESystem(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), Greater(Variable("x"),Number(2))))
  }

  it should "perhaps always parse x'=5,y'=7&x>2 as a program" in {
    parser.programParser("x'=5,y'=7&x>2") should be (ODESystem(DifferentialProduct(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), AtomicODE(DifferentialSymbol(Variable("y")), Number(7))), Greater(Variable("x"),Number(2))))
    try {
      parser("x'=5,y'=7&x>2") should be (ODESystem(DifferentialProduct(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), AtomicODE(DifferentialSymbol(Variable("y")), Number(7))), Greater(Variable("x"),Number(2))))
    }
    catch {case ignore: ParseException => }
  }

  it should "always parse {x'=5,y'=7&x>2} as a program" in {
    parser("{x'=5,y'=7&x>2}") should be (ODESystem(DifferentialProduct(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), AtomicODE(DifferentialSymbol(Variable("y")), Number(7))), Greater(Variable("x"),Number(2))))
    parser.programParser("{x'=5,y'=7&x>2}") should be (ODESystem(DifferentialProduct(AtomicODE(DifferentialSymbol(Variable("x")), Number(5)), AtomicODE(DifferentialSymbol(Variable("y")), Number(7))), Greater(Variable("x"),Number(2))))
  }

  it should "not parse x'=5,y'=7&x>2 as a formula" in {
    a [ParseException] should be thrownBy parser.formulaParser("x'=5,y'=7&x>2")
  }

  it should "parse (<a;b;>p(x))&q()" in {
    parser("(<a;b;>p(x))&q()") should be
    And(Diamond(Compose(ProgramConst("a"), ProgramConst("b")), PredOf(p,Variable("x"))), q0)
  }

  it should "parse \\forall x x>=0" in {
    parser("\\forall x x>=0") should be
    Forall(Seq(Variable("x")), GreaterEqual(Variable("x"),Number(0)))
  }

  it should "parse \\forall x p(x)" in {
    parser("\\forall x p(x)") should be
    Forall(Seq(Variable("x")), PredOf(p, Variable("x")))
  }

  it should "parse \\forall x x>=0&x<0" in {
    parser("\\forall x x>=0&x<0") should be
    And(Forall(Seq(Variable("x")), GreaterEqual(Variable("x"),Number(0))), Less(Variable("x"),Number(0)))
  }

  it should "parse \\forall x p(x)&q(x)" in {
    parser("\\forall x p(x)&q(x)") should be
    And(Forall(Seq(Variable("x")), PredOf(p, Variable("x"))), PredOf(q,Variable("x")))
  }

  "Predicate/function parser" should "parse" in {
    parser("p(x,y)->f(x,y)>g(x)") shouldBe Imply(PredOf(p2, Pair(x,y)), Greater(FuncOf(f2,Pair(x,y)), FuncOf(g,x)))
  }

  it should "refuse to parse type mess p(x,y)->f(x,y)>p(x)" in {
    a [ParseException] should be thrownBy parser("p(x,y)->f(x,y)>p(x)")
  }

  it should "refuse to parse type mess p(x,y)->!p(x)" in {
    a [ParseException] should be thrownBy parser("p(x,y)->!p(x)")
  }

  it should "refuse to parse type mess p()->!p(x)" in {
    a [ParseException] should be thrownBy parser("p()->!p(x)")
  }

  it should "refuse to parse type mess p() -> [x:=p();]true" in {
    a [ParseException] should be thrownBy parser("p() -> [x:=p();]true")
  }

  it should "refuse to parse type mess p() -> [{x'=p()}]true" in {
    a [ParseException] should be thrownBy parser("p() -> [{x'=p()}]true")
  }

  it should "refuse to parse type mess x() -> [x:=x(x);]x()>x(x,x())" in {
    a [ParseException] should be thrownBy parser("x() -> [x:=x(x);]x()>x(x,x())")
  }


  /////////////////////////////////////


  "Parser documentation" should "compile and run printer 1" in {
    val pp = KeYmaeraXPrettyPrinter
    // "x < -y"
    val fml0 = Less(Variable("x"), Neg(Variable("y")))
    val fml0str = pp(fml0)
    // "true -> [x:=1;]x>=0"
    val fml1 = Imply(True, Box(Assign(Variable("x"), Number(1)), GreaterEqual(Variable("x"), Number(0))))
    val fml1str = pp(fml1)
  }

  it should "compile and run printer 2" in {
    val pp = KeYmaeraXPrettyPrinter
    // "x < -(y)"
    val fml0 = Less(Variable("x"), Neg(Variable("y")))
    val fml0str = pp(fml0)
    // "true -> ([x:=1;](x>=0))"
    val fml1 = Imply(True, Box(Assign(Variable("x"), Number(1)), GreaterEqual(Variable("x"), Number(0))))
    val fml1str = pp(fml1)
  }

  it should "compile and run parser 1" in {
    val parser = KeYmaeraXParser
    val fml0 = parser("x!=5")
    val fml1 = parser("x>0 -> [x:=x+1;]x>1")
    val fml2 = parser("x>=0 -> [{x'=2}]x>=0")
  }

  it should "compile and run parser 2" in {
    val parser = KeYmaeraXParser
    // formulas
    val fml0 = parser("x!=5")
    val fml1 = parser("x>0 -> [x:=x+1;]x>1")
    val fml2 = parser("x>=0 -> [{x'=2}]x>=0")
    // terms
    val term0 = parser("x+5")
    val term1 = parser("x^2-2*x+7")
    val term2 = parser("x*y/3+(x-1)^2+5*z")
    // programs
    val prog0 = parser("x:=1;")
    val prog1 = parser("x:=1;x:=5;x:=-1;")
    val prog2 = parser("x:=1;{{x'=5}++x:=0;}")
  }

    it should "compile and run formula parser 1" in {
      // the formula parser only accepts formulas
      val parser = KeYmaeraXParser.formulaParser
      // formulas
      val fml0 = parser("x!=5")
      val fml1 = parser("x>0 -> [x:=x+1;]x>1")
      val fml2 = parser("x>=0 -> [{x'=2}]x>=0")
      // terms will cause exceptions
      try { parser("x+5") } catch {case e: ParseException => println("Rejected")}
      // programs will cause exceptions
      try { parser("x:=1;") } catch {case e: ParseException => println("Rejected")}
    }

  it should "compile and run parse of print 1" in {
    val parser = KeYmaeraXParser
    val pp = KeYmaeraXPrettyPrinter
    val fml = Imply(True, Box(Assign(Variable("x"), Number(1)), GreaterEqual(Variable("x"), Number(0))))
    // something like "true -> [x:=1;]x>=0" modulo spacing
    val print = pp(fml)
    val reparse = parser(print)
    if (fml == reparse) println("Print and reparse successful") else println("Discrepancy")
  }

  it should "compile and run parse of print of parse" in {
    val parser = KeYmaeraXParser
    val pp = KeYmaeraXPrettyPrinter
    val input = "x^2>=0 & x<44 -> [x:=2;{x'=1&x<=10}]x>=1"
    val parse = parser(input)
    println("Parsed:   " + parse)
    val print = pp(parse)
    println("Printed:  " + print)
    println("Original: " + input)
    println("Can differ slightly by spacing and parentheses")
  }

  it should "compile and run full print" in {
    val parser = KeYmaeraXParser
    val pp = FullPrettyPrinter
    val input = "x^2>=0 & x<44 -> [x:=2;{x'=1&x<=10}]x>=1"
    val parse = parser(input)
    println("Parsed:   " + parse)
    val print = pp(parse)
    println("Printed:  " + print)
    println("Original: " + input)
  }
}
