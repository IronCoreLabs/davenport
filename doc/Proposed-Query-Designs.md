# Proposed Query Designs

For our initial implementation, we should have a few goals:

1. Readily emulatable on MemDatastore
2. Enforces good practices for performant queries
3. Minimize parsing at run time

Both 1 and 2 could lead to us not supporting queries that couchbase would support.  For the performance requirement, for example, we may not allow conditionals against unindexed fields.  If we should later want to add the ability to filter on unindexed fields, we might perhaps add to the ADT something with a `Slow` prefix.

There are two types of queries in CouchBase v4: Map Views and N1QL.  Map views have a longer history and are better tested at this point.  N1QL has more power, but may not be enabled and requires separate indexing servers (services at least) to run.  In either case, the update of the index is asynchronous and could be delayed from a previous write.

Below we explore each of these types and how we might model them.

## Map Views

A map view is basically a map/reduce routine that is registered with the server.  This generates a new set of keys and values that can be looked up by key.  In this scenario you essentially predefine what queries you need to run and the server keeps the query results up-to-date for quick querying.  Every time a new document is added, the map and reduce functions are called and the results merged back.  Importantly, these functions `emit` keys and values which can amount to aggregates (sums under certain conditions) secondary indices (documents with lists of documents meeting some condition) or just about anything.

See http://developer.couchbase.com/documentation/server/4.0/developer-guide/views-writing.html

Couchbase takes JavaScript routines for map and the optional reduce.  Modeling Map Views in scala therefore would mean taking scala code and producing javascript.  This may be possible with ScalaJS.  If we could generate javascript code from scala code, then we could use the scala routines on the MemDatastore and register the generated JavaScript with the CouchBase server for the CouchDatastore.  In theory, we could have arbitrary map/reduce routines and could query them and test them with some high degree of certainty.

This would be very useful and pretty damn cool, too.

## N1QL

The first thing to know with N1QL is that it requires extra services and servers to run.  The second thing to know is that for any given bucket, it is disabled by default until you turn on global indexing for that bucket.

With global indexing on, you can theoretically query anything, but querying anything is not necessarily a good idea from a performance perspective.  Ideally, you have specific indices on the fields that are being filtered.

N1QL also allows you to return partial documents, selecting out just a few fields that are desired.  This has its benefits, but potentially gets us away from working with strongly typed documents inside scala.  It would be fair to disallow partial documents in the beginning and add that as a separate feature if necessary later.  We may be able to get away with always doing a `SELECT *`.

Aggregates are the wrinkle here.  Fetching a count of records meeting some condition or a sum of them or several fields and their counts or sums would require an arbitrary `SELECT` clause.  We could account for this in the beginning or we could punt this and have separate ADTs for aggregates.

In the simplest starting case, we could only allow full record selection and no aggregates.  We could require that each field being used in a conditional be indexed first.  And we could have very specific comparison operations (as an ADT) for equal/not-equal/greater-than/less-than/greater-than-or-equal/less-than-or-equal to be applied to the fields.  

Do we assign a type to the fields when they are indexed so we can ensure proper query strings?

On the MemDatastore side, this should be fairly easy to simulate (even if by brute force) and on the CouchDatastore side it should be fairly performant.  A good starting point, even if limited?

One other thought is on key scanning.  Keys can be used in conditionals as like any other field.  However, the common use case for us would probably be to generate a set of documents whose keys match some prefix.  Building up a set N1QL query that specifically does this as an ADT could be extremely useful.  We would not need any index beyond the global one as a prerequisite.


