ant-salesforce-ext
==================

The library contains several ant tasks that build upon the ant tasks available in the Salesforce Metadata Migration Toolkit.

The bin folder contains a sample build.xml file, sample properties files and the necessary compiled jar libraries. 

Properties files should be named build.properties.branch - e.g. build.properites.master for example.

The tasks rely on a folder structure to extract and stage metadata:

    parent       parent.output
    |—git        git.root (must contain a git repository against which git checkout can be executed)
    | `-src      git.source
    |—output_ms  git.output.ms
    |—output     git.output
    | `-upsert   git.output.source
    |-temp       git.temp


