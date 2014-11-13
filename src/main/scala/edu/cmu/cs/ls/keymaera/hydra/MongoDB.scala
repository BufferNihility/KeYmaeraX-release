package edu.cmu.cs.ls.keymaera.hydra

import com.mongodb.casbah.Imports._

/**
 * Created by nfulton on 9/23/14.
 */
object MongoDB extends DBAbstraction {
  val mongoClient = MongoClient("localhost", 27017)

  //Collections.
  val users   = mongoClient("keymaera")("users")
  val models  = mongoClient("keymaera")("models")
  val proofs  = mongoClient("keymaera")("proofs")
  val logs    = mongoClient("keymaera")("logs")
  val tasks   = mongoClient("keymaera")("tasks")
  val tactics = mongoClient("keymaera")("tactics")
  val dispatchedTactics = mongoClient("keymaera")("dispatchedTactics")
  val proofNodes = mongoClient("keymaera")("proofNodes")
  val sequents = mongoClient("keymaera")("sequents")

  def cleanup() = {
    users.drop()
    models.drop()
    proofs.drop()
    logs.drop()
    tasks.drop()
  }

  //val proofs = mongoClient("keymaera")("proofs")
  //val tactics = mongoClient("keymaera")("tactics")
  //val positionTactics = mongoClient("keymaera")("positionTactics")
  //val models = mongoClient("keymaera")("models")

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Users
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  override def getUsername(uid: String): String = uid

  override def userExists(username : String) = {
    val query = MongoDBObject("username" -> username)
    users.count(query) > 0
  }

  override def createUser(username: String, password: String): Unit = {
    val newUser = MongoDBObject("username" -> username, "password" -> password)
    users.insert(newUser)
  }

  override def checkPassword(username: String, password: String): Boolean = {
    val result = MongoDBObject("username" -> username, "password" -> password)
    users.count(result) > 0
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Models
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  override def createModel(userId: String, name: String, fileContents: String, currentDate : String): Boolean = {

    var query = MongoDBObject("userId" -> userId, "name" -> name, "fileContents" -> fileContents) //w/o date
    if(models.find(query).length == 0) {
      var obj = MongoDBObject("userId" -> userId, "name" -> name, "date" -> currentDate, "fileContents" -> fileContents)
      models.insert(obj)
      true // TODO should we allow models by the same user and with the same name?
    }
    else {
      false
    }
  }

  override def getModelList(userId : String) = {
    var query = MongoDBObject("userId" -> userId)
    val allModels = models.find(query)
    allModels.map(model =>
      if(!model.containsField("_id")) ???
      else new ModelPOJO(
        model.getAs[ObjectId]("_id").getOrElse(null).toString(),
        model.getAs[String]("userId").getOrElse(""),
        model.getAs[String]("name").getOrElse("") ,
        model.getAs[String]("date").getOrElse(""),
        model.getAs[String]("fileContents").getOrElse("")
      )
    ).toList
  }

  override def getModel(modelId: String): ModelPOJO = {
    var query = MongoDBObject("_id" -> new ObjectId(modelId))
    val results = models.find(query)
    if(results.length > 1) ??? //There should only be one response b/c _id is a pk.
    if(results.length < 1) throw new Exception(modelId + " is a bad modelId!")
    results.map(result => new ModelPOJO(
      result.getAs[ObjectId]("_id").getOrElse(null).toString(),
      result.getAs[String]("userId").getOrElse(""),
      result.getAs[String]("name").getOrElse("") ,
      result.getAs[String]("date").getOrElse(""),
      result.getAs[String]("fileContents").getOrElse("")
    )).toList.last
  }

  override def createProofForModel(modelId : String, name : String, description : String, date:String) : String = {
    var query = MongoDBObject("modelId" -> modelId, "name" -> name, "description" -> description, "date" -> date)
    var result = proofs.insert(query)
    query.get("_id").toString()
  }

  override def getProofsForModel(modelId : String) : List[ProofPOJO] = {
    var query = MongoDBObject("modelId" -> modelId)
    var results = proofs.find(query)
    if(results.length < 1) Nil
    else {
      results.map(result => {
        //TODO count the number of steps
        val stepCount = -1
        //TODO determine if the proof is closed
        val isClosed = false

        new ProofPOJO(
          result.getAs[ObjectId]("_id").getOrElse(null).toString(),
          result.getAs[String]("modelId").getOrElse(null).toString(),
          result.getAs[String]("name").getOrElse(null).toString(),
          result.getAs[String]("description").getOrElse("no description"),
          result.getAs[String]("date").getOrElse(null).toString(),
          stepCount,
          isClosed
        )
      }).toList
    }
  }

  override def getProofInfo(proofId: String): ProofPOJO = {
    var query = MongoDBObject("_id" -> new ObjectId(proofId))
    val results = proofs.find(query)
    if(results.length > 1) ??? //There should only be one response b/c _id is a pk.
    if(results.length < 1) throw new Exception(proofId + " is a bad proofId!")
    results.map(result => new ProofPOJO(
      result.getAs[ObjectId]("_id").getOrElse(null).toString(),
      result.getAs[String]("modelId").getOrElse(""),
      result.getAs[String]("name").getOrElse(""),
      result.getAs[String]("description").getOrElse("") ,
      result.getAs[String]("date").getOrElse(""),
      result.getAs[Integer]("stepCount").getOrElse(0),
      result.getAs[Boolean]("closed").getOrElse(false)
    )).toList.last
  }



  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Proof Nodes
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  override def createTask(proofId:String) : String = {
    val query = MongoDBObject(
      "proofId"      -> proofId
    )
    tasks.insert(query)
    query.get("_id").toString()
  }
  override def addTask(task : TaskPOJO) = {
    val taskDbObject = task.nodeId match {
      case Some(n) => MongoDBObject(
        "_id"          -> task.taskId,
        "nodeId"       -> n,
        "task"         -> task.task,
        "rootTaskId"   -> task.rootTaskId,
        "proofId"      -> task.proofId)
      case None => MongoDBObject(
        "_id"          -> task.taskId,
        "task"         -> task.task,
        "rootTaskId"   -> task.rootTaskId,
        "proofId"      -> task.proofId)
    }

    tasks.insert(taskDbObject)
  }


  override def getTask(taskId : String) = {
    val query = MongoDBObject("_id" -> taskId)
    val results = tasks.find(query)
    if(results.length < 1 || results.length > 1) ??? //TODO handle db errors
    val result = results.toList.last
    new TaskPOJO(
      result.getAs[ObjectId]("_id").orNull.toString(),
      if (result.containsField("nodeId")) Some(result.getAs[String]("nodeId").getOrElse("")) else None,
      result.getAs[String]("task").getOrElse(""),
      result.getAs[String]("rootTaskId").getOrElse(""),
      result.getAs[String]("proofId").getOrElse("")
    )
  }

  override def getProofTasks(proofId : String) = {
    val query = MongoDBObject("proofId" -> proofId)
    val results = tasks.find(query)
    results.map(result => new TaskPOJO(
      result.getAs[ObjectId]("_id").orNull.toString(),
      if (result.containsField("nodeId")) Some(result.getAs[String]("nodeId").getOrElse("")) else None,
      result.getAs[String]("task").getOrElse(""),
      result.getAs[String]("rootTaskId").getOrElse(""),
      result.getAs[String]("proofId").getOrElse("")
    )).toList;
  }

  override def updateTask(task: TaskPOJO) = {
    val update = task.nodeId match {
      case Some(n) => MongoDBObject(
        "nodeId"       -> n,
        "task"         -> task.task,
        "rootTaskId"   -> task.rootTaskId,
        "proofId"      -> task.proofId)
      case None => MongoDBObject(
        "task"         -> task.task,
        "rootTaskId"   -> task.rootTaskId,
        "proofId"      -> task.proofId)
    }
    tasks.update(MongoDBObject("_id" -> new ObjectId(task.taskId)), update)
  }


  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Tactics
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  def createTactic(name : String, clazz : String, kind : TacticKind.Value) : String = {
    val query = MongoDBObject(
      "name"    -> name,
      "clazz"   -> clazz,
      "kind"    -> kind.toString()
    )
    tactics.insert(query)
    query.get("_id").toString()
  }
  def getTactic(id: String) : TacticPOJO = {
    val query = MongoDBObject("_id" -> new ObjectId(id))
    val results = tactics.find(query)
    if(results.length < 1 || results.length > 1) ??? //TODO handle db errors
    results.map(result => new TacticPOJO(
      result.getAs[ObjectId]("_id").orNull.toString(),
      result.getAs[String]("name").getOrElse(""),
      result.getAs[String]("clazz").getOrElse(""),
      TacticKind.withName(result.getAs[String]("kind").getOrElse(""))
    )).toList.last
  }
  def getTacticByName(name: String) : Option[TacticPOJO] = {
    val query = MongoDBObject("name" -> name)
    val results = tactics.find(query)
    if(results.length > 1) ??? //TODO handle db errors
    if (results.length < 1) None
    else Some(results.map(result => new TacticPOJO(
      result.getAs[ObjectId]("_id").orNull.toString(),
      result.getAs[String]("name").getOrElse(""),
      result.getAs[String]("clazz").getOrElse(""),
      TacticKind.withName(result.getAs[String]("kind").getOrElse(""))
    )).toList.last)
  }
  override def getTactics() : List[TacticPOJO] = {
    val results = tactics.find()
    results.map(result => new TacticPOJO(
      result.getAs[ObjectId]("_id").orNull.toString(),
      result.getAs[String]("name").getOrElse(""),
      result.getAs[String]("clazz").getOrElse(""),
      TacticKind.withName(result.getAs[String]("kind").getOrElse(""))
    )).toList
  }

  override def createDispatchedTactics(taskId:String, nodeId:Option[String], formulaId:Option[String], tacticsId:String) : String = {
    val fields = List(
      "taskId"       -> taskId,
      "tacticsId"    -> tacticsId)
    if (nodeId.isDefined) { fields :+ ("nodeId" -> nodeId) }
    if (formulaId.isDefined) { fields :+ ("formulaId" -> formulaId) }

    val query = MongoDBObject(fields)
    dispatchedTactics.insert(query)
    query.get("_id").toString()
  }
  override def getDispatchedTactics(tId : String) : DispatchedTacticPOJO = {
    val query = MongoDBObject("_id" -> new ObjectId(tId))
    val results = dispatchedTactics.find(query)
    if(results.length < 1 || results.length > 1) ??? //TODO handle db errors
    results.map(result => new DispatchedTacticPOJO(
      result.getAs[ObjectId]("_id").orNull.toString(),
      result.getAs[String]("taskId").getOrElse(""),
      if (result.containsField("nodeId")) Some(result.getAs[String]("nodeId").getOrElse("")) else None,
      if (result.containsField("formulaId")) Some(result.getAs[String]("formulaId").getOrElse("")) else None,
      result.getAs[String]("tacticsId").getOrElse("")
    )).toList.last
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Proof Nodes
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  override def subtreeExists(pnId: String): Boolean = {
    val query = MongoDBObject("_id" -> new ObjectId(pnId))
    val results = proofNodes.find(query)
    results.length > 0
  }
  override def createSubtree(pnId: String, tree: String): String = {
    val query = MongoDBObject()
    proofNodes.insert(query)
    query.get("_id").toString()
  }
  override def getSubtree(pnId: String): String = {
    val query = MongoDBObject("_id" -> new ObjectId(pnId))
    val results = proofNodes.find(query)
    if (results.length < 1) throw new IllegalArgumentException("Unknown ID " + pnId)
    if (results.length > 1) throw new IllegalStateException("None-unique ID " + pnId)
    results.map(result => result.getAs[String]("tree").getOrElse("")).toList.last
  }
  override def updateSubtree(pnId: String, tree: String) = {
    val update = MongoDBObject("tree" -> tree)
    tasks.update(MongoDBObject("_id" -> new ObjectId(pnId)), update)
  }

}
