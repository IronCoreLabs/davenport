---
title: Storing and Retrieving Case Classes
---

Suppose you have a User class that has some basic fields:

```scala
case class User(firstName: String, lastName: String, email: String, createdDate: Long)
```

We prefer not to adulterate the underlying class. All you need to write is the `JsonCodec` and a way to generate the `Key` for your type. In our example, we'll put the `createKey` function on the companion object, but it could be located anywhere.

First, the data type definition:

```tut:silent
import com.ironcorelabs.davenport.db.Key
import argonaut._, Argonaut._

object Example {
  case class User(firstName: String, lastName: String, email: String, createdDate: Long)
  
  object User{
    implicit def codec: CodecJson[User] = casecodec4(User.apply, User.unapply)("firstName", "lastName", "email", "createdDate")
    def createKey(u: User): Key = Key(s"user::${u.email}")
  }
}
```

Now that we have a `JsonCodec` and a way to get a key, let's see how it works!

```tut
import Example._
import com.ironcorelabs.davenport.datastore.MemDatastore
import com.ironcorelabs.davenport.syntax._
import scalaz._

val user1 = User("User", "One", "readyplayerone@example.com", System.currentTimeMillis())
val user2 = User("User", "Two", "readyplayertwo@example.com", System.currentTimeMillis())
val addTwoNewUsers = for {
  newu1 <- User.createKey(user1).dbCreate(user1)
  newu2 <- User.createKey(user2).dbCreate(user2)
} yield List(newu1, newu2)

val users = MemDatastore.empty.execute(addTwoNewUsers).run
```

Feel free to test against Couchbase as well.  We'll keep illustrating with the MemDatastore for now to show how you can easily experiment and write unit tests.  As an alternative to calling `MemDatastore.empty.execute` you can call `CouchConnection.createInterpreter.execute`.  You could also import the syntax which will add `execute` to `DBProg` which takes any `Interpreter`. Building on our example above, we could instead do this:

```tut
val datastore = MemDatastore.empty
val users = addTwoNewUsers.execute(datastore).run

// Fetch one of the users out of the database
val u1 = Key("user::readyplayerone@example.com").dbGet[User].execute(datastore).run
```
