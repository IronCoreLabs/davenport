# TODO

## Documentation

- [ ] Setup github pages site with documentation
    - See https://github.com/oncue/knobs/tree/master/docs  
    - Remember to use tut
- [ ] Add scaladoc comments http://docs.scala-lang.org/style/scaladoc.html
- [ ] Add scaladocs to the github pages site (via unidoc?)

## Build

- [ ] Move snapshots to Sonatype
- [ ] Cross-compile against different scala versions - at least 2.10 and 2.11
- [ ] Setup release process using [sbt-release](https://github.com/sbt/sbt-release)
- [ ] Setup travis to use docker image so couchbase tests can run. Links:
    - http://docs.travis-ci.com/user/docker/
    - https://github.com/travis-ci/docker-sinatra/blob/master/Dockerfile
    - https://hub.docker.com/r/zmre/couchbase-enterprise-ubuntu/
- [ ] PGP sign package
- [ ] Setup sbt updates check: https://github.com/rtimush/sbt-updates
- [x] Add notifications

## Code

- [ ] Resolve codacy concerns

## Announce

- [ ] Add to tools wiki: https://wiki.scala-lang.org/display/SW/Tools+and+Libraries
- [ ] Add to libs: http://www.couchbase.com/open-source
- [ ] Add to awesome: https://github.com/lauris/awesome-scala
- [ ] Add to implicitly: http://notes.implicit.ly
    - [ ] Also add infrastructure for autopublishing on update. See https://github.com/n8han/herald

## Misc

- [ ] Add team members to
    - Travis-CI
    - Codacy
    - Bintray
    - Codecov.io
