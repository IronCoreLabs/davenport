---
title: Free Grammar Implementation
---

Davenport uses a Free Grammar abstraction based on [scalaz](https://github.com/scalaz/scalaz)'s `Free` monads. This means that you string together a bunch of database instructions, but delay executing them until you're ready.  When you do execute them, you get to choose your interpreter.  All interpreters must handle all instructions.

In Davenport, we provide an in-memory local option as well as Couchbase, but someone could implement the grammar against any backend and none of your code would change except your choice of interpreter.  The immediate advantage is testing: you get fast unit tests via the MemInterpreter that verify functionality without needing a Couchbase server or connection setup and teardown.  For example:

```tut:silent
import com.ironcorelabs.davenport.DB._
import com.ironcorelabs.davenport.CouchConnection
import com.ironcorelabs.davenport.interpreter.MemInterpreter
import com.ironcorelabs.davenport.syntax._

// Some definitions that should help understand the code below
//   case class Key(value: String)
//   case class RawJsonString(value: String)
//   case class DBDocument[A](key: Key, hashVer: HashVer, data: A)
//   type DBValue = DBDocument[RawJsonString]
//   case class DBValue(data: RawJsonString, hashVer: HashVer)

// Write something to the DB, then fetch it (we're ignoring the fact that we return
// the written value from the update command to make a point)
val k = Key("Record1")
val v = RawJsonString("""{ "field1": "value1", "field2": "value2" }""")
val operations = for {
  newDoc <- createDoc(k, v)
  fetchedDoc <- getDoc(k)
} yield fetchedDoc

// Now we can execute those operations using Couch or Mem.  Either:
val finalResult = operations.interpret(MemInterpreter.empty).run

// or: val finalResult = CouchConnection.createInterpreter.interpret(operations).run
// and in either case the result will be the same except for the hashVer
```

* This also has some nice short-circuiting properties. If you have a DB error early on, continued DB operations will halt (unless you prefer otherwise).
* You can map over the DB and inject whatever other functions you like into the process.  As a more complex example, you can make an operation that copies a json field from one stored document to another:

```tut
import com.ironcorelabs.davenport.DB._
import argonaut._, Argonaut._

// This function is just argonaut magic and not really important for our use case
// We parse the strings, and if the source is a json object and the field exists
// and if the destination is a json object, then rework the json to copy in the
// new field and value from src to dst.
def copyFieldJson(field: String, srcJson: RawJsonString, dstJson: RawJsonString): Option[RawJsonString] = for {
  jsd <- Parse.parseOption(dstJson.value)
  jss <- Parse.parseOption(srcJson.value)
  lens = jObjectPL >=> jsonObjectPL(field)
  fieldval <- lens.get(jss)
} yield RawJsonString(((field, fieldval) ->: jsd).nospaces)

// This shows how to use the above copy operation on data from the db
// any errors along the way (such as a failure to find a document with the key)
// will abort the whole thing (short circuit) and result in an error when
// the DBProg is executed
def copyFieldInDb(field: String, srcKey: Key, dstKey: Key): DBProg[DBValue] = for {
  src <- getDoc(srcKey)
  dst <- getDoc(dstKey)
  newjson <- liftIntoDBProg(copyFieldJson(field, src.data, dst.data), "Serde failed.")
  updatedDst <- updateDoc(dstKey, newjson, dst.hashVer)
} yield updatedDst


// in this case, the result will be an error since docA and docB have not been created
val finalResult = copyFieldInDb("a", Key("docA"), Key("docB")).interpret(MemInterpreter.empty).run

// in this case, the result will be a successful new docB with a:1, c: 2, d: 2
val finalResult = MemInterpreter.empty.interpret(for {
  docA <- createDoc(Key("docA"), RawJsonString("""{ "a": 1, "b": 1, "c": 1 }"""))
  docB <- createDoc(Key("docB"), RawJsonString("""{ "c": 2, "d": 2 }"""))
  newB <- copyFieldInDb("a", Key("docA"), Key("docB"))
} yield newB).run
```

This implementation uses scalaz Disjunctions (`\/`) and scalaz `Tasks`. You can choose to spin off the task asynchronously or combine it with other async events such as service calls.

Errors are nicely handled with returned exceptions (as opposed to thrown exceptions) to give insight into any issues while retaining type safety.

Note: this is not meant to be used with raw json and keys. Instead, this solution was built to work with thin DB wrappers around case classes that manage persistence of classes.  See the other tutorials for more details on how that works.

