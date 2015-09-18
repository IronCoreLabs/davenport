---
title: Storing and Retrieving Case Classes
---

Suppose you have a User class that has some basic fields:

```scala
case class User(firstName: String, lastName: String, email: String, createdDate: Long)
```

We prefer not to adulterate the underlying class, but instead to wrap it in a class that can persist it to/from the database.  We do so by creating an interface that by convention we call `DBUser`:

```scala
case class DBUser(key: Key, data: User, cas: Long) extends DBDocument[User] {
  // ...
}
object DBUser extends DBDocumentCompanion[User] {
  // ...
}
```

In wrapping the User class, we put the concerns such as the key that is used, the check-and-store (cas) value (used to make sure someone else hasn't updated the database since we last fetched a record) and implementations for serializing/deserializing outside of the core concerns for the class itself.

In terms of implementation, the instantiated document needs to support one function:

```scala
def dataJson: Throwable \/ RawJsonString
```

And the companion object needs to support a few more:

```scala
implicit def codec: CodecJson[T]
def genKey(d: T): DBProg[Key]
def fromJson(s: RawJsonString): Throwable \/ T
def create(d: T): DBProg[_]
def get(k: Key): DBProg[_]
```

Generally speaking, these are pretty boilerplate.  One thing to note is that the `genKey` method returns a `DBProg`.  This is in case the key itself depends on the database, as in the case where you are using an incrementing index as the key.  If you are deriving the key from the underlying class, you just wrap the result into a `DBProg`.  In our case, our key for a `User` will be `user::<email>`, so statically derived.  Here's a fuller implementation:

```tut:silent
import com.ironcorelabs.davenport._, DB._
import scalaz._, Scalaz._, scalaz.concurrent.Task
import argonaut._, Argonaut._

case class User(firstName: String, lastName: String, email: String, createdDate: Long)

object Example {
  case class DBUser(key: Key, data: User, cas: Long) extends DBDocument[User] {
    def dataJson: Throwable \/ RawJsonString =
      \/.fromTryCatchNonFatal(DBUser.toJsonString(data)(DBUser.codec))
  }
  object DBUser extends DBDocumentCompanion[User] {
    implicit def codec: CodecJson[User] = casecodec4(User.apply, User.unapply)(
      "firstName", "lastName", "email", "createdDate"
    )
    def genKey(u: User): DBProg[Key] = liftIntoDBProg(Key(s"user::${u.email}").right[Throwable])
    def fromJson(s: RawJsonString): Throwable \/ User =
      fromJsonString(s.value) \/> new Exception("Failed to decode json to User")
    def create(u: User): DBProg[DBUser] = for {
      json <- liftIntoDBProg(\/.fromTryCatchNonFatal(toJsonString(u)))
      key <- genKey(u)
      newdoc <- createDoc(key, json)
      cas = newdoc.hashVer.value
    } yield DBUser(key, u, cas)
    def get(key: Key): DBProg[DBUser] = for {
      doc <- getDoc(key)
      u <- liftIntoDBProg(fromJson(doc.jsonString))
      cas = doc.hashVer.value
    } yield DBUser(key, u, cas)
  }
}
```

Anyone wanting to factor out some of the boilerplate -- please be my guest, we'd love the pull requests.

Now that we have a nice abstraction for persisting our user class, let's try it out:

```tut
import Example._
import com.ironcorelabs.davenport.interpreter.MemInterpreter
import com.ironcorelabs.davenport.syntax.dbprog._
val addTwoNewUsers = for {
  newu1 <- DBUser.create(User("User", "One", "readyplayerone@example.com", System.currentTimeMillis()))
  newu2 <- DBUser.create(User("User", "Two", "readyplayertwo@example.com", System.currentTimeMillis()))
} yield List(newu1, newu2)

val users: Throwable \/ List[DBUser] = MemInterpreter.empty.interpret(addTwoNewUsers).run
```

Feel free to test against Couchbase as well.  We'll keep illustrating with the MemInterpreter for now to show how you can easily experiment and write unit tests.  As an alternative to calling `MemInterpreter.empty.interpret` you can call `CouchConnection.createInterpreter.interpret`.  You could also import the syntax which will add `interpret` to `DBProg` which takes any `Interpreter`. Building on our example above, we could instead do this:

```tut
val interpreter = MemInterpreter.empty
val users = addTwoNewUsers.interpret(interpreter).run

// Fetch one of the users out of the database
val u1 = DBUser.get(Key("user::readyplayerone@example.com")).interpret(interpreter).run
```

We expect that the primitives with `RawJsonString` will generally not be used outside of the `DBDocument` classes.

