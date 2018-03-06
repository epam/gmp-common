# GMP common module [![Build Status](https://travis-ci.org/epam/gmp-common.svg?branch=master)](https://travis-ci.org/epam/gmp-common)

The goal for this library is to reduce code size for several operations:

1.Freemarker template (https://freemarker.apache.org) processing Groovy example. Template processing from classpath resources:

```
def variables = ['key1': 'value 1',
                 'key2': 'value 2',
                 'key3': ['value 3.1', 'value 3.2']]

FreemarkerHelper freemarker = new FreemarkerHelper(this.class.classLoader, '')
println(freemarker.processTemplate('template.flt', variables))
```

2.JSON Serialization. Groovy example:

```
def bean = ['one','two']
def document = JsonMapper.getInstance().map(bean)
```

3.Execution commands to OS. Groovy example:

```
def hash = {some hash}
OS os = OS.getOs()
List<String> params = ['git', 'diff-tree', '--no-commit-id', '--name-only', '-r', hash]
List<String> processOut = new ArrayList<String>()
def result = os.execCommandLine(params, processOut, '.', 600)
// result = process exit code
// processOut now contains the list of strings returned by process output
```
