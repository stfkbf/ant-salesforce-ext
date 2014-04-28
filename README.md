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

A typical flow to extract metadata using, for example, Hudson might be:

    ant extractBranch commitAndPushBranch -Dbranch=master

A typical flow to deploy changes from one org to another (assuming a deployment from master to patch):

    # Extract metadata from the source org into the master branch
    ant extractBranch commitAndPushBranch -Dbranch=master

    # Extract metadata from the target org into the patch branch
    ant extractBranch commitAndPushBranch -Dbranch=patch

    # Merge master into patch
    git merge master

    # Create a package containing the differences between patch and the target org
    # prepared for deployment
    ant createPackage -Dbranch=patch

    # Deploy to the target org
    ant deployToOrg -Dbranch=patch

The above would require the properties file build.properties.master to specify the source org and build.properties.patch the target org. Further the above assumes patch is a branch from master.

