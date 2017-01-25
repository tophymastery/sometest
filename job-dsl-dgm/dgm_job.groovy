folder("dgm-bcrm")

def dependency_pipelines = [
  [
    name: "dgm-bcrm-dependency-check",
    repository: "git@bitbucket.org:engagelab/scb-bcrm.git",
    branch: "*/scb",
    label: "master"
  ],
  [
    name: "dgm-cms-dependency-check",
    repository: "git@bitbucket.org:engagelab/scb-bcrm.git",
    branch: "*/scb",
    label: "master"
  ]
].each { i ->
  job_name     = i['name'].replaceAll(" ", "-")
  build_branch = i.containsKey('branch') ? i['branch'] : 'master'

  job("dgm-bcrm/${job_name}") {
    description """
    Configure infrastructure as defined in the 'infrastructure/${job_name}.yml' playbook.
    """.stripIndent().trim()
    disabled(false)
    using "template-defaults"

    logRotator(-1, 5)
    quietPeriod(0)

    if (i['label'] != "") {
      label(i['label'])
    }

    scm {
      git {
        remote {
          url(i['repository'])
        }
        branch(build_branch)
      }
    }

    triggers {
      cron("@daily")
    }

    steps {
      shell("""\
        export RBENV_ROOT=/usr/local/var/rbenv
        eval "\$(rbenv init -)"

        # Add authorization for gems
        # for line gem
        bundle config gem.linebcrm.com dgm59:CfR8KXFuMWKxaRqHEds4vWYJ
        # for sidekiq pro gem
        bundle config gems.contribsys.com d45c69de:4c1de664

        # Check before install
        bundle check || bundle install

        # @see https://jeremylong.github.io/DependencyCheck/dependency-check-cli/arguments.html
        dependency-check --project "SCB on LINE" \\
                        --scan "\${WORKSPACE}" \\
                        --bundleAudit /usr/local/var/rbenv/shims/bundle-audit

        bundle-audit update
        bundle-audit check
      """.stripIndent().trim())
    }

    publishers {
      htmlPublisher {
        reportTargets {
          htmlPublisherTarget {
            reportName("Dependency Check Report")
            reportDir("")
            reportFiles("dependency-check-report.html")
            keepAll(true)
            alwaysLinkToLastBuild(true)
            allowMissing(true)
          }
        }
      }
    }
}

def build_pipelines = [
  [
    name: "dgm-bcrm-build",
    repository: "git@bitbucket.org:engagelab/scb-bcrm.git",
    description: "Pull code from DGM, run test, build RPM and install bcrm",
    label: "master"
  ],
  [
    name: "dgm-cms-build",
    repository: "git@bitbucket.org:engagelab/scb-bcrm.git",
    description: "Pull code from DGM, run test, build RPM and install cms",
    label: "master"
  ]
].each { i ->
  job_description     = i['description']
  build_branch = i.containsKey('branch') ? i['branch'] : 'master'

  job("dgm-bcrm/${job_name}") {
    description """
    ${job_description}
    """.stripIndent().trim()
    disabled(false)
    using "template-defaults"

    logRotator(-1, 5)
    quietPeriod(0)

    if (i['label'] != "") {
      label(i['label'])
    }
}
