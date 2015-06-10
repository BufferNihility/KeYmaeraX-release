/**
 * Differential Dynamic Logic lexer for concrete KeYmaera X notation.
 * @author aplatzer
 * @see "Andre Platzer. A uniform substitution calculus for differential dynamic logic.  arXiv 1503.01981, 2015."
 */
package edu.cmu.cs.ls.keymaerax.parser

import scala.collection.immutable._
import scala.util.matching.Regex

/**
 * Terminal symbols of the differential dynamic logic grammar.
 * @author aplatzer
 */
sealed abstract class Terminal(val img: String) {
  override def toString = getClass.getSimpleName + "\"" + img + "\""
  /**
   * @return The regex that identifies this token.
   */
  def regexp : scala.util.matching.Regex = img.r

  val startPattern: Regex = ("^" + regexp.pattern.pattern + ".*").r
}
abstract class OPERATOR(val opcode: String) extends Terminal(opcode) {
  //final def opcode: String = img
  override def toString = getClass.getSimpleName //+ "\"" + img + "\""
}
case class IDENT(name: String) extends Terminal(name) {
  override def toString = "ID(\"" + name + "\")"
  override def regexp = """[a-zA-Z0-9_]+""".r
}
case class NUMBER(value: String) extends Terminal(value) {
  override def toString = "NUM(" + value + ")"
  override def regexp = """[0-9]+""".r
}

/**
 * End Of Stream
 */
object EOS extends Terminal("<EOS>") {
  override def regexp = "$^".r //none.
}

object LPAREN  extends Terminal("(") {
  override def regexp = """\(""".r
}
object RPAREN  extends Terminal(")") {
  override def regexp = """\)""".r
}
object LBRACE  extends Terminal("{") {
  override def regexp = """\{""".r
}
object RBRACE  extends Terminal("}") {
  override def regexp = """\}""".r
}
object LBOX    extends Terminal("[") {
  override def regexp = """\[""".r
}
object RBOX    extends Terminal("]") {
  override def regexp = """\]""".r
}
object LDIA    extends OPERATOR("<") {
  override def regexp = """\<""".r
}//@todo really operator or better not?
object RDIA    extends OPERATOR(">") {
  override def regexp = """\>""".r
}

object COMMA   extends OPERATOR(",")

object PRIME   extends OPERATOR("'")
object POWER   extends OPERATOR("^")
object STAR    extends OPERATOR("*")
object SLASH   extends OPERATOR("/")
object PLUS    extends OPERATOR("+")
object MINUS   extends OPERATOR("-")

object NOT     extends OPERATOR("!")
object AND     extends OPERATOR("&")
object OR      extends OPERATOR("|")
object EQUIV   extends OPERATOR("<->")
object IMPLY   extends OPERATOR("->")
object REVIMPLY extends OPERATOR("<-")

object FORALL  extends OPERATOR("\\forall")
object EXISTS  extends OPERATOR("\\exists")

object EQ      extends OPERATOR("=")
object NOTEQ   extends OPERATOR("!=")
object GREATEREQ extends OPERATOR(">=")
object LESSEQ  extends OPERATOR("<=")

object TRUE    extends OPERATOR("true")
object FALSE   extends OPERATOR("false")

object ASSIGNANY extends OPERATOR(":=*")
object ASSIGN  extends OPERATOR(":=")
object TEST    extends OPERATOR("?")
object SEMI    extends OPERATOR(";")
object CHOICE  extends OPERATOR("++")

// pseudos: could probably demote so that some are not OPERATOR
object NOTHING extends Terminal("")
object DOT     extends OPERATOR("•") //(".")
object PLACE   extends OPERATOR("⎵") //("_")
object PSEUDO  extends Terminal("<pseudo>")


sealed abstract class Location
object UnknownLocation extends Location {
  override def toString = "<somewhere>"
}
case class Region(line: Int, column: Int, endLine: Int, endColumn: Int) extends Location
/**
 * Like a region, but extends until the end of the input.
 * @param line The starting line.
 * @param column The ending line.
 */
case class SuffixRegion(line: Int, column: Int) extends Location

/**
 * Created by aplatzer on 6/8/15.
 */
object KeYmaeraXLexer extends (String => List[Token]) {

  /** Lexer's token stream with first token at head. */
  type TokenStream = List[Token]

  def apply(input: String) = lex(input, SuffixRegion(1,1))


  /**
   * Finds the next token in a string.
   * Untested correctness condition: If a token's regex pattern contains another's, then the more
   * restrictive token is processed first in the massive if/else.
   * @param s The string to process.
   * @param loc The location of s.
   * @return A triple containing:
   *          _1: the next token,
   *          _2: the portion of the string following the next token,
   *          _3: The location of the beginning of the next string.
   */
  private def findNextToken(s: String, loc: Location): Option[(String, Token, Location)] = {
    val whitespace = """^(\ +).*""".r
    val newline = """(?s)(^\n).*""".r

    /**
     *
     * @param cols Number of columns to move cursor.
     * @param terminal terminal to generate a token for.
     * @param location Current location.
     * @return Return value of findNextToken
     */
    def consumeColumns(cols: Int, terminal: Terminal, location: Location) = {
      assert(cols > 0, "Cannot move cursor less than 1 columns.")
      Some((
        s.tail,
        Token(terminal, spanningRegion(loc, cols-1)),
        suffixOf(loc, cols+2)))
    }
    def consumeTerminalLength(terminal: Terminal, location: Location) =
      consumeColumns(terminal.img.length, terminal, location)

    s match {
      //update location if we encounter whitespace.
      case whitespace(spaces) => {
        findNextToken(s.substring(spaces.length), loc match {
          case UnknownLocation => UnknownLocation
          case Region(sl,sc,el,ec) => Region(sl, sc+spaces.length, el, ec)
          case SuffixRegion(sl,sc) => SuffixRegion(sl, sc+ spaces.length)
        })
      }
      case newline(_*) => {
        findNextToken(s.tail, loc match {
          case UnknownLocation     => UnknownLocation
          case Region(sl,sc,el,ec) => Region(sl+1,1,el,ec)
          case SuffixRegion(sl,sc) => SuffixRegion(sl+1, 1)
        })
      }

      case LPAREN.startPattern(_*) => consumeTerminalLength(LPAREN, loc)
      case RPAREN.startPattern(_*) => consumeTerminalLength(RPAREN, loc)
      case LBOX.startPattern(_*) => consumeTerminalLength(LBOX, loc)
      case RBOX.startPattern(_*) => consumeTerminalLength(RBOX, loc)
      case LDIA.startPattern(_*) => consumeTerminalLength(LDIA, loc)
      case RDIA.startPattern(_*) => consumeTerminalLength(RDIA, loc)
      


      case "" => None
      case _ => throw new Exception("Lexer did not understand input at " + loc + " in ." + s +". First character was ." + s(0) + ".")
    }
  }
/*

 */



  /**
   * Returns the region containing everything between the starting position of the current location
   * location and the indicated offset of from the starting positiong of the current location,
   * inclusive.
   * @param location Current location
   * @param endColOffset Column offset of the region
   * @return The region spanning from the start of ``location" to the offset from the start of ``location".
   */
  private def spanningRegion(location: Location, endColOffset: Int) =
    location match {
      case UnknownLocation        => UnknownLocation
      case Region(sl, sc, el, ec) => Region(sl, sc, sl, sc + endColOffset)
      case SuffixRegion(sl, sc)   => Region(sl, sc, sl, sc + endColOffset)
    }

  /**
   *
   * @param location Current location
   * @param colOffset Number of columns to chop off from the starting position of location.
   * @return A region containing all of location except the indicated columns in the initial row.
   *         I.e., the colOffset-suffix of location.
   */
  private def suffixOf(location: Location, colOffset: Int) : Location =
    location match {
      case UnknownLocation        => UnknownLocation
      case Region(sl, sc, el, ec) => Region(sl, sc + colOffset, el, ec)
      case SuffixRegion(sl, sc)   => SuffixRegion(sl, sc + colOffset)
    }

  private def lex(input: String, inputLocation:Location): TokenStream =
    if(input.trim.length == 0) {
      List(Token(EOS, inputLocation match {
        case UnknownLocation =>  UnknownLocation
        case x:Region => x
        case SuffixRegion(sl,sc) => Region(sl,sc,sl,sc)
      }))
    }
    else {
      findNextToken(input, inputLocation) match {
        case Some((nextInput, token, nextLoc)) => token +: lex(nextInput, nextLoc)
        case None => throw new Exception("Have not reached EOF but could not find next token in ." + input + ".")
      }
    }
}
