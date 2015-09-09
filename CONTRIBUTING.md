# Contributor Guide

## Code Coverage Checks (local)

Make sure any pull requests have adequate unit test code coverage.  You can check this without waiting for travis and codecov.io to do their thing by doing this:

```bash
> sbt coverage test
```

And then open up the file `target/scala-2.11/scoverage-report/index.html` in your browser.

## Updating Documentation and Website

Tutorials should be added to the `src/tutsrc` directory.  Code blocks should typically use `\`\`\`tuts` instead of `\`\`\`scala` to add code.  This will ensure the code blocks are checked by the compiler and the output shown inline.  See [tpolecat/tut](https://github.com/tpolecat/tut) for details.

To update the site, do this:

```bash
> sbt tut make-site
> sbt ghpages-push-site
```

You can also do `sbt previewSite` to check things out locally, but you'll need to update the `src/jekyll/_config.yml` file to change the `baseurl` config to `""`.  Just don't check that in and push or you'll break the live site.  If you want to set up some kind of conditional to make this better, that would be great.

