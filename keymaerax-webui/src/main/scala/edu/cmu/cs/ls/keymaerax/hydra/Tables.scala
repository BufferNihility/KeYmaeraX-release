package 
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object Tables extends {
  val profile = scala.slick.driver.SQLiteDriver
} with Tables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: scala.slick.driver.JdbcProfile
  import profile.simple._
  import scala.slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import scala.slick.jdbc.{GetResult => GR}
  
  /** DDL for all tables. Call .create to execute. */
  lazy val ddl = Config.ddl ++ Executableparameter.ddl ++ Executables.ddl ++ Executionsteps.ddl ++ Models.ddl ++ Patterns.ddl ++ Proofs.ddl ++ Provables.ddl ++ Scalatactics.ddl ++ Sequentformulas.ddl ++ Tacticexecutions.ddl ++ Users.ddl ++ Valuetypes.ddl
  
  /** Entity class storing rows of table Config
   *  @param configid Database column configId DBType(TEXT), PrimaryKey
   *  @param configname Database column configName DBType(TEXT)
   *  @param key Database column key DBType(TEXT)
   *  @param value Database column value DBType(TEXT) */
  case class ConfigRow(configid: Option[String], configname: Option[String], key: Option[String], value: Option[String])
  /** GetResult implicit for fetching ConfigRow objects using plain SQL queries */
  implicit def GetResultConfigRow(implicit e0: GR[Option[String]]): GR[ConfigRow] = GR{
    prs => import prs._
    ConfigRow.tupled((<<?[String], <<?[String], <<?[String], <<?[String]))
  }
  /** Table description of table config. Objects of this class serve as prototypes for rows in queries. */
  class Config(_tableTag: Tag) extends Table[ConfigRow](_tableTag, "config") {
    def * = (configid, configname, key, value) <> (ConfigRow.tupled, ConfigRow.unapply)
    
    /** Database column configId DBType(TEXT), PrimaryKey */
    val configid: Column[Option[String]] = column[Option[String]]("configId", O.PrimaryKey)
    /** Database column configName DBType(TEXT) */
    val configname: Column[Option[String]] = column[Option[String]]("configName")
    /** Database column key DBType(TEXT) */
    val key: Column[Option[String]] = column[Option[String]]("key")
    /** Database column value DBType(TEXT) */
    val value: Column[Option[String]] = column[Option[String]]("value")
  }
  /** Collection-like TableQuery object for table Config */
  lazy val Config = new TableQuery(tag => new Config(tag))
  
  /** Entity class storing rows of table Executableparameter
   *  @param parameterid Database column parameterId DBType(TEXT), PrimaryKey
   *  @param executableid Database column executableId DBType(TEXT)
   *  @param idx Database column idx DBType(INT)
   *  @param valuetypeid Database column valueTypeId DBType(TEXT)
   *  @param value Database column value DBType(TEXT) */
  case class ExecutableparameterRow(parameterid: Option[String], executableid: Option[String], idx: Option[Int], valuetypeid: Option[String], value: Option[String])
  /** GetResult implicit for fetching ExecutableparameterRow objects using plain SQL queries */
  implicit def GetResultExecutableparameterRow(implicit e0: GR[Option[String]], e1: GR[Option[Int]]): GR[ExecutableparameterRow] = GR{
    prs => import prs._
    ExecutableparameterRow.tupled((<<?[String], <<?[String], <<?[Int], <<?[String], <<?[String]))
  }
  /** Table description of table executableParameter. Objects of this class serve as prototypes for rows in queries. */
  class Executableparameter(_tableTag: Tag) extends Table[ExecutableparameterRow](_tableTag, "executableParameter") {
    def * = (parameterid, executableid, idx, valuetypeid, value) <> (ExecutableparameterRow.tupled, ExecutableparameterRow.unapply)
    
    /** Database column parameterId DBType(TEXT), PrimaryKey */
    val parameterid: Column[Option[String]] = column[Option[String]]("parameterId", O.PrimaryKey)
    /** Database column executableId DBType(TEXT) */
    val executableid: Column[Option[String]] = column[Option[String]]("executableId")
    /** Database column idx DBType(INT) */
    val idx: Column[Option[Int]] = column[Option[Int]]("idx")
    /** Database column valueTypeId DBType(TEXT) */
    val valuetypeid: Column[Option[String]] = column[Option[String]]("valueTypeId")
    /** Database column value DBType(TEXT) */
    val value: Column[Option[String]] = column[Option[String]]("value")
    
    /** Foreign key referencing Executables (database name executables_FK_1) */
    lazy val executablesFk = foreignKey("executables_FK_1", executableid, Executables)(r => r.executableid, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Executableparameter */
  lazy val Executableparameter = new TableQuery(tag => new Executableparameter(tag))
  
  /** Entity class storing rows of table Executables
   *  @param executableid Database column executableId DBType(TEXT), PrimaryKey
   *  @param scalatacticid Database column scalaTacticId DBType(TEXT)
   *  @param belleexpr Database column belleExpr DBType(TEXT) */
  case class ExecutablesRow(executableid: Option[String], scalatacticid: Option[String], belleexpr: Option[String])
  /** GetResult implicit for fetching ExecutablesRow objects using plain SQL queries */
  implicit def GetResultExecutablesRow(implicit e0: GR[Option[String]]): GR[ExecutablesRow] = GR{
    prs => import prs._
    ExecutablesRow.tupled((<<?[String], <<?[String], <<?[String]))
  }
  /** Table description of table executables. Objects of this class serve as prototypes for rows in queries. */
  class Executables(_tableTag: Tag) extends Table[ExecutablesRow](_tableTag, "executables") {
    def * = (executableid, scalatacticid, belleexpr) <> (ExecutablesRow.tupled, ExecutablesRow.unapply)
    
    /** Database column executableId DBType(TEXT), PrimaryKey */
    val executableid: Column[Option[String]] = column[Option[String]]("executableId", O.PrimaryKey)
    /** Database column scalaTacticId DBType(TEXT) */
    val scalatacticid: Column[Option[String]] = column[Option[String]]("scalaTacticId")
    /** Database column belleExpr DBType(TEXT) */
    val belleexpr: Column[Option[String]] = column[Option[String]]("belleExpr")
  }
  /** Collection-like TableQuery object for table Executables */
  lazy val Executables = new TableQuery(tag => new Executables(tag))
  
  /** Entity class storing rows of table Executionsteps
   *  @param stepid Database column stepId DBType(TEXT), PrimaryKey
   *  @param exeuctionid Database column exeuctionId DBType(TEXT)
   *  @param previousstep Database column previousStep DBType(TEXT)
   *  @param parentstep Database column parentStep DBType(TEXT)
   *  @param branchorder Database column branchOrder DBType(TEXT)
   *  @param branchlabel Database column branchLabel DBType(INT)
   *  @param alternativeorder Database column alternativeOrder DBType(INT)
   *  @param isinterrupt Database column isInterrupt DBType(BOOLEAN)
   *  @param executableid Database column executableId DBType(TEXT)
   *  @param inputprovableid Database column inputProvableId DBType(TEXT)
   *  @param resultprovableid Database column resultProvableId DBType(TEXT)
   *  @param userexecuted Database column userExecuted DBType(BOOLEAN) */
  case class ExecutionstepsRow(stepid: Option[String], exeuctionid: Option[String], previousstep: Option[String], parentstep: Option[String], branchorder: Option[String], branchlabel: Option[Int], alternativeorder: Option[Int], isinterrupt: Option[String], executableid: Option[String], inputprovableid: Option[String], resultprovableid: Option[String], userexecuted: Option[String])
  /** GetResult implicit for fetching ExecutionstepsRow objects using plain SQL queries */
  implicit def GetResultExecutionstepsRow(implicit e0: GR[Option[String]], e1: GR[Option[Int]]): GR[ExecutionstepsRow] = GR{
    prs => import prs._
    ExecutionstepsRow.tupled((<<?[String], <<?[String], <<?[String], <<?[String], <<?[String], <<?[Int], <<?[Int], <<?[String], <<?[String], <<?[String], <<?[String], <<?[String]))
  }
  /** Table description of table executionSteps. Objects of this class serve as prototypes for rows in queries. */
  class Executionsteps(_tableTag: Tag) extends Table[ExecutionstepsRow](_tableTag, "executionSteps") {
    def * = (stepid, exeuctionid, previousstep, parentstep, branchorder, branchlabel, alternativeorder, isinterrupt, executableid, inputprovableid, resultprovableid, userexecuted) <> (ExecutionstepsRow.tupled, ExecutionstepsRow.unapply)
    
    /** Database column stepId DBType(TEXT), PrimaryKey */
    val stepid: Column[Option[String]] = column[Option[String]]("stepId", O.PrimaryKey)
    /** Database column exeuctionId DBType(TEXT) */
    val exeuctionid: Column[Option[String]] = column[Option[String]]("exeuctionId")
    /** Database column previousStep DBType(TEXT) */
    val previousstep: Column[Option[String]] = column[Option[String]]("previousStep")
    /** Database column parentStep DBType(TEXT) */
    val parentstep: Column[Option[String]] = column[Option[String]]("parentStep")
    /** Database column branchOrder DBType(TEXT) */
    val branchorder: Column[Option[String]] = column[Option[String]]("branchOrder")
    /** Database column branchLabel DBType(INT) */
    val branchlabel: Column[Option[Int]] = column[Option[Int]]("branchLabel")
    /** Database column alternativeOrder DBType(INT) */
    val alternativeorder: Column[Option[Int]] = column[Option[Int]]("alternativeOrder")
    /** Database column isInterrupt DBType(BOOLEAN) */
    val isinterrupt: Column[Option[String]] = column[Option[String]]("isInterrupt")
    /** Database column executableId DBType(TEXT) */
    val executableid: Column[Option[String]] = column[Option[String]]("executableId")
    /** Database column inputProvableId DBType(TEXT) */
    val inputprovableid: Column[Option[String]] = column[Option[String]]("inputProvableId")
    /** Database column resultProvableId DBType(TEXT) */
    val resultprovableid: Column[Option[String]] = column[Option[String]]("resultProvableId")
    /** Database column userExecuted DBType(BOOLEAN) */
    val userexecuted: Column[Option[String]] = column[Option[String]]("userExecuted")
    
    /** Foreign key referencing Executables (database name executables_FK_1) */
    lazy val executablesFk = foreignKey("executables_FK_1", executableid, Executables)(r => r.executableid, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing Executionsteps (database name executionSteps_FK_2) */
    lazy val executionstepsFk = foreignKey("executionSteps_FK_2", (parentstep, previousstep), Executionsteps)(r => (r.stepid, r.stepid), onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing Provables (database name provables_FK_3) */
    lazy val provablesFk = foreignKey("provables_FK_3", (resultprovableid, inputprovableid), Provables)(r => (r.provableid, r.provableid), onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing Tacticexecutions (database name tacticExecutions_FK_4) */
    lazy val tacticexecutionsFk = foreignKey("tacticExecutions_FK_4", exeuctionid, Tacticexecutions)(r => r.executionid, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Executionsteps */
  lazy val Executionsteps = new TableQuery(tag => new Executionsteps(tag))
  
  /** Entity class storing rows of table Models
   *  @param modelid Database column modelId DBType(TEXT), PrimaryKey
   *  @param userid Database column userId DBType(TEXT)
   *  @param name Database column name DBType(TEXT)
   *  @param date Database column date DBType(TEXT)
   *  @param description Database column description DBType(TEXT)
   *  @param filecontents Database column fileContents DBType(TEXT)
   *  @param publink Database column publink DBType(TEXT)
   *  @param title Database column title DBType(TEXT)
   *  @param tactic Database column tactic DBType(TEXT) */
  case class ModelsRow(modelid: Option[String], userid: Option[String], name: Option[String], date: Option[String], description: Option[String], filecontents: Option[String], publink: Option[String], title: Option[String], tactic: Option[String])
  /** GetResult implicit for fetching ModelsRow objects using plain SQL queries */
  implicit def GetResultModelsRow(implicit e0: GR[Option[String]]): GR[ModelsRow] = GR{
    prs => import prs._
    ModelsRow.tupled((<<?[String], <<?[String], <<?[String], <<?[String], <<?[String], <<?[String], <<?[String], <<?[String], <<?[String]))
  }
  /** Table description of table models. Objects of this class serve as prototypes for rows in queries. */
  class Models(_tableTag: Tag) extends Table[ModelsRow](_tableTag, "models") {
    def * = (modelid, userid, name, date, description, filecontents, publink, title, tactic) <> (ModelsRow.tupled, ModelsRow.unapply)
    
    /** Database column modelId DBType(TEXT), PrimaryKey */
    val modelid: Column[Option[String]] = column[Option[String]]("modelId", O.PrimaryKey)
    /** Database column userId DBType(TEXT) */
    val userid: Column[Option[String]] = column[Option[String]]("userId")
    /** Database column name DBType(TEXT) */
    val name: Column[Option[String]] = column[Option[String]]("name")
    /** Database column date DBType(TEXT) */
    val date: Column[Option[String]] = column[Option[String]]("date")
    /** Database column description DBType(TEXT) */
    val description: Column[Option[String]] = column[Option[String]]("description")
    /** Database column fileContents DBType(TEXT) */
    val filecontents: Column[Option[String]] = column[Option[String]]("fileContents")
    /** Database column publink DBType(TEXT) */
    val publink: Column[Option[String]] = column[Option[String]]("publink")
    /** Database column title DBType(TEXT) */
    val title: Column[Option[String]] = column[Option[String]]("title")
    /** Database column tactic DBType(TEXT) */
    val tactic: Column[Option[String]] = column[Option[String]]("tactic")
    
    /** Foreign key referencing Users (database name users_FK_1) */
    lazy val usersFk = foreignKey("users_FK_1", userid, Users)(r => r.email, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Models */
  lazy val Models = new TableQuery(tag => new Models(tag))
  
  /** Entity class storing rows of table Patterns
   *  @param patternid Database column patternId DBType(TEXT), PrimaryKey
   *  @param executableid Database column executableId DBType(TEXT)
   *  @param idx Database column idx DBType(INT)
   *  @param patternformula Database column patternFormula DBType(TEXT)
   *  @param resultingexecutable Database column resultingExecutable DBType(TEXT) */
  case class PatternsRow(patternid: Option[String], executableid: Option[String], idx: Option[Int], patternformula: Option[String], resultingexecutable: Option[String])
  /** GetResult implicit for fetching PatternsRow objects using plain SQL queries */
  implicit def GetResultPatternsRow(implicit e0: GR[Option[String]], e1: GR[Option[Int]]): GR[PatternsRow] = GR{
    prs => import prs._
    PatternsRow.tupled((<<?[String], <<?[String], <<?[Int], <<?[String], <<?[String]))
  }
  /** Table description of table patterns. Objects of this class serve as prototypes for rows in queries. */
  class Patterns(_tableTag: Tag) extends Table[PatternsRow](_tableTag, "patterns") {
    def * = (patternid, executableid, idx, patternformula, resultingexecutable) <> (PatternsRow.tupled, PatternsRow.unapply)
    
    /** Database column patternId DBType(TEXT), PrimaryKey */
    val patternid: Column[Option[String]] = column[Option[String]]("patternId", O.PrimaryKey)
    /** Database column executableId DBType(TEXT) */
    val executableid: Column[Option[String]] = column[Option[String]]("executableId")
    /** Database column idx DBType(INT) */
    val idx: Column[Option[Int]] = column[Option[Int]]("idx")
    /** Database column patternFormula DBType(TEXT) */
    val patternformula: Column[Option[String]] = column[Option[String]]("patternFormula")
    /** Database column resultingExecutable DBType(TEXT) */
    val resultingexecutable: Column[Option[String]] = column[Option[String]]("resultingExecutable")
    
    /** Foreign key referencing Executables (database name executables_FK_1) */
    lazy val executablesFk = foreignKey("executables_FK_1", (resultingexecutable, executableid), Executables)(r => (r.executableid, r.executableid), onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Patterns */
  lazy val Patterns = new TableQuery(tag => new Patterns(tag))
  
  /** Entity class storing rows of table Proofs
   *  @param proofid Database column proofId DBType(TEXT), PrimaryKey
   *  @param modelid Database column modelId DBType(TEXT)
   *  @param name Database column name DBType(TEXT)
   *  @param description Database column description DBType(TEXT)
   *  @param date Database column date DBType(TEXT)
   *  @param closed Database column closed DBType(INTEGER) */
  case class ProofsRow(proofid: Option[String], modelid: Option[String], name: Option[String], description: Option[String], date: Option[String], closed: Option[Int])
  /** GetResult implicit for fetching ProofsRow objects using plain SQL queries */
  implicit def GetResultProofsRow(implicit e0: GR[Option[String]], e1: GR[Option[Int]]): GR[ProofsRow] = GR{
    prs => import prs._
    ProofsRow.tupled((<<?[String], <<?[String], <<?[String], <<?[String], <<?[String], <<?[Int]))
  }
  /** Table description of table proofs. Objects of this class serve as prototypes for rows in queries. */
  class Proofs(_tableTag: Tag) extends Table[ProofsRow](_tableTag, "proofs") {
    def * = (proofid, modelid, name, description, date, closed) <> (ProofsRow.tupled, ProofsRow.unapply)
    
    /** Database column proofId DBType(TEXT), PrimaryKey */
    val proofid: Column[Option[String]] = column[Option[String]]("proofId", O.PrimaryKey)
    /** Database column modelId DBType(TEXT) */
    val modelid: Column[Option[String]] = column[Option[String]]("modelId")
    /** Database column name DBType(TEXT) */
    val name: Column[Option[String]] = column[Option[String]]("name")
    /** Database column description DBType(TEXT) */
    val description: Column[Option[String]] = column[Option[String]]("description")
    /** Database column date DBType(TEXT) */
    val date: Column[Option[String]] = column[Option[String]]("date")
    /** Database column closed DBType(INTEGER) */
    val closed: Column[Option[Int]] = column[Option[Int]]("closed")
    
    /** Foreign key referencing Models (database name models_FK_1) */
    lazy val modelsFk = foreignKey("models_FK_1", modelid, Models)(r => r.modelid, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Proofs */
  lazy val Proofs = new TableQuery(tag => new Proofs(tag))
  
  /** Entity class storing rows of table Provables
   *  @param provableid Database column provableId DBType(TEXT), PrimaryKey */
  case class ProvablesRow(provableid: Option[String])
  /** GetResult implicit for fetching ProvablesRow objects using plain SQL queries */
  implicit def GetResultProvablesRow(implicit e0: GR[Option[String]]): GR[ProvablesRow] = GR{
    prs => import prs._
    ProvablesRow(<<?[String])
  }
  /** Table description of table provables. Objects of this class serve as prototypes for rows in queries. */
  class Provables(_tableTag: Tag) extends Table[ProvablesRow](_tableTag, "provables") {
    def * = provableid <> (ProvablesRow, ProvablesRow.unapply)
    
    /** Database column provableId DBType(TEXT), PrimaryKey */
    val provableid: Column[Option[String]] = column[Option[String]]("provableId", O.PrimaryKey)
  }
  /** Collection-like TableQuery object for table Provables */
  lazy val Provables = new TableQuery(tag => new Provables(tag))
  
  /** Entity class storing rows of table Scalatactics
   *  @param scalatacticid Database column scalaTacticId DBType(TEXT), PrimaryKey
   *  @param location Database column location DBType(TEXT) */
  case class ScalatacticsRow(scalatacticid: Option[String], location: Option[String])
  /** GetResult implicit for fetching ScalatacticsRow objects using plain SQL queries */
  implicit def GetResultScalatacticsRow(implicit e0: GR[Option[String]]): GR[ScalatacticsRow] = GR{
    prs => import prs._
    ScalatacticsRow.tupled((<<?[String], <<?[String]))
  }
  /** Table description of table scalaTactics. Objects of this class serve as prototypes for rows in queries. */
  class Scalatactics(_tableTag: Tag) extends Table[ScalatacticsRow](_tableTag, "scalaTactics") {
    def * = (scalatacticid, location) <> (ScalatacticsRow.tupled, ScalatacticsRow.unapply)
    
    /** Database column scalaTacticId DBType(TEXT), PrimaryKey */
    val scalatacticid: Column[Option[String]] = column[Option[String]]("scalaTacticId", O.PrimaryKey)
    /** Database column location DBType(TEXT) */
    val location: Column[Option[String]] = column[Option[String]]("location")
  }
  /** Collection-like TableQuery object for table Scalatactics */
  lazy val Scalatactics = new TableQuery(tag => new Scalatactics(tag))
  
  /** Entity class storing rows of table Sequentformulas
   *  @param sequentformulaid Database column sequentFormulaId DBType(TEXT), PrimaryKey
   *  @param provableid Database column provableId DBType(TEXT)
   *  @param isante Database column isAnte DBType(BOOLEAN)
   *  @param idx Database column idx DBType(INTEGER)
   *  @param formula Database column formula DBType(TEXT) */
  case class SequentformulasRow(sequentformulaid: Option[String], provableid: Option[String], isante: Option[String], idx: Option[Int], formula: Option[String])
  /** GetResult implicit for fetching SequentformulasRow objects using plain SQL queries */
  implicit def GetResultSequentformulasRow(implicit e0: GR[Option[String]], e1: GR[Option[Int]]): GR[SequentformulasRow] = GR{
    prs => import prs._
    SequentformulasRow.tupled((<<?[String], <<?[String], <<?[String], <<?[Int], <<?[String]))
  }
  /** Table description of table sequentFormulas. Objects of this class serve as prototypes for rows in queries. */
  class Sequentformulas(_tableTag: Tag) extends Table[SequentformulasRow](_tableTag, "sequentFormulas") {
    def * = (sequentformulaid, provableid, isante, idx, formula) <> (SequentformulasRow.tupled, SequentformulasRow.unapply)
    
    /** Database column sequentFormulaId DBType(TEXT), PrimaryKey */
    val sequentformulaid: Column[Option[String]] = column[Option[String]]("sequentFormulaId", O.PrimaryKey)
    /** Database column provableId DBType(TEXT) */
    val provableid: Column[Option[String]] = column[Option[String]]("provableId")
    /** Database column isAnte DBType(BOOLEAN) */
    val isante: Column[Option[String]] = column[Option[String]]("isAnte")
    /** Database column idx DBType(INTEGER) */
    val idx: Column[Option[Int]] = column[Option[Int]]("idx")
    /** Database column formula DBType(TEXT) */
    val formula: Column[Option[String]] = column[Option[String]]("formula")
    
    /** Foreign key referencing Provables (database name provables_FK_1) */
    lazy val provablesFk = foreignKey("provables_FK_1", provableid, Provables)(r => r.provableid, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Sequentformulas */
  lazy val Sequentformulas = new TableQuery(tag => new Sequentformulas(tag))
  
  /** Entity class storing rows of table Tacticexecutions
   *  @param executionid Database column executionId DBType(TEXT), PrimaryKey
   *  @param proofid Database column proofId DBType(TEXT) */
  case class TacticexecutionsRow(executionid: Option[String], proofid: Option[String])
  /** GetResult implicit for fetching TacticexecutionsRow objects using plain SQL queries */
  implicit def GetResultTacticexecutionsRow(implicit e0: GR[Option[String]]): GR[TacticexecutionsRow] = GR{
    prs => import prs._
    TacticexecutionsRow.tupled((<<?[String], <<?[String]))
  }
  /** Table description of table tacticExecutions. Objects of this class serve as prototypes for rows in queries. */
  class Tacticexecutions(_tableTag: Tag) extends Table[TacticexecutionsRow](_tableTag, "tacticExecutions") {
    def * = (executionid, proofid) <> (TacticexecutionsRow.tupled, TacticexecutionsRow.unapply)
    
    /** Database column executionId DBType(TEXT), PrimaryKey */
    val executionid: Column[Option[String]] = column[Option[String]]("executionId", O.PrimaryKey)
    /** Database column proofId DBType(TEXT) */
    val proofid: Column[Option[String]] = column[Option[String]]("proofId")
    
    /** Foreign key referencing Proofs (database name proofs_FK_1) */
    lazy val proofsFk = foreignKey("proofs_FK_1", proofid, Proofs)(r => r.proofid, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Tacticexecutions */
  lazy val Tacticexecutions = new TableQuery(tag => new Tacticexecutions(tag))
  
  /** Entity class storing rows of table Users
   *  @param email Database column email DBType(TEXT), PrimaryKey
   *  @param password Database column password DBType(TEXT) */
  case class UsersRow(email: Option[String], password: Option[String])
  /** GetResult implicit for fetching UsersRow objects using plain SQL queries */
  implicit def GetResultUsersRow(implicit e0: GR[Option[String]]): GR[UsersRow] = GR{
    prs => import prs._
    UsersRow.tupled((<<?[String], <<?[String]))
  }
  /** Table description of table users. Objects of this class serve as prototypes for rows in queries. */
  class Users(_tableTag: Tag) extends Table[UsersRow](_tableTag, "users") {
    def * = (email, password) <> (UsersRow.tupled, UsersRow.unapply)
    
    /** Database column email DBType(TEXT), PrimaryKey */
    val email: Column[Option[String]] = column[Option[String]]("email", O.PrimaryKey)
    /** Database column password DBType(TEXT) */
    val password: Column[Option[String]] = column[Option[String]]("password")
  }
  /** Collection-like TableQuery object for table Users */
  lazy val Users = new TableQuery(tag => new Users(tag))
  
  /** Entity class storing rows of table Valuetypes
   *  @param valuetypeid Database column valueTypeId DBType(TEXT), PrimaryKey
   *  @param `type` Database column type DBType(TEXT) */
  case class ValuetypesRow(valuetypeid: Option[String], `type`: Option[String])
  /** GetResult implicit for fetching ValuetypesRow objects using plain SQL queries */
  implicit def GetResultValuetypesRow(implicit e0: GR[Option[String]]): GR[ValuetypesRow] = GR{
    prs => import prs._
    ValuetypesRow.tupled((<<?[String], <<?[String]))
  }
  /** Table description of table valueTypes. Objects of this class serve as prototypes for rows in queries.
   *  NOTE: The following names collided with Scala keywords and were escaped: type */
  class Valuetypes(_tableTag: Tag) extends Table[ValuetypesRow](_tableTag, "valueTypes") {
    def * = (valuetypeid, `type`) <> (ValuetypesRow.tupled, ValuetypesRow.unapply)
    
    /** Database column valueTypeId DBType(TEXT), PrimaryKey */
    val valuetypeid: Column[Option[String]] = column[Option[String]]("valueTypeId", O.PrimaryKey)
    /** Database column type DBType(TEXT)
     *  NOTE: The name was escaped because it collided with a Scala keyword. */
    val `type`: Column[Option[String]] = column[Option[String]]("type")
  }
  /** Collection-like TableQuery object for table Valuetypes */
  lazy val Valuetypes = new TableQuery(tag => new Valuetypes(tag))
}