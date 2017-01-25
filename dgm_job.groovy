folder("dgm-bcrm")

job("dgm-bcrm/build") {
  description ""
  disabled(false)
  using "template-defaults"

  label("sally")

  scm {
    git {
      remote {
        url('git@bitbucket.org:engagelab/scb-bcrm.git')
      }
      branch('*/scb')
    }
  }

  triggers {
    scm("H/5 * * * *")
  }

  steps{
    shell("""
      echo "Get access to DGM repository"
      eval \$(aws ecr get-login --registry-ids 460105102933)

      echo "Start application stack"
      docker-compose down || true
      docker-compose up -d || true

      retries=1
      max_retries=50
      echo "INFO Try to connect \$max_retries time(s)...."
      until \$(curl --output /dev/null --silent --head --fail http://localhost:3000/bcrm/ping); do
        printf '.'
        sleep 10
        if [[ \$retries = \$max_retries ]]; then
          exit 1
        fi
        retries=\$(( \$retries + 1 ))
      done

      docker-compose exec -T web bash -c "rspec -f doc --require rails_helper"
      """.stripIndent().trim())
  }
}

job("dgm-bcrm/dependency-check") {
  description """
  Checks the dependencies for security issues.
  """.stripIndent().trim()
  disabled(false)
  using "template-defaults"

  logRotator(-1, 5)
  quietPeriod(0)

  label("master")

  scm {
    git {
      remote {
        url('git@bitbucket.org:engagelab/scb-bcrm.git')
      }
      branch('*/scb')
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

def dgm_dependency_jobs = [
  [
    name: "dgm-bcrm-dependency-check",
    component: "bcrm",
    repository: "git@bitbucket.org:engagelab/scb-bcrm.git",
    trigger: "@daily",
    label: "master",
    subsequent_job: ""   
  ],
  [
    name: "dgm-cms-dependency-check",
    component: "cms",
    repository: "git@bitbucket.org:engagelab/scb-cms.git",
    trigger: "@daily",
    label: "master",
    subsequent_job: ""
  ]
].each { i ->
  job_name     = i['name'].replaceAll(" ", "-")
  build_branch = i.containsKey('branch') ? i['branch'] : 'master'

  job("dgm/${job_name}") {

  description """
    Configure of ${job_name}
    """.stripIndent().trim()
  disabled(false)
  using "template-defaults"

  logRotator(-1, 5)
  quietPeriod(0)

  label("${i['label']}")

  scm {
    git {
      remote {
        url("${i['repository']}")
      }
      branch('*/scb')
    }
  }

  triggers {
    cron("${i['trigger']}")
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
          reportFiles("${i['component']}-dependency-check-report.html")
          keepAll(true)
          alwaysLinkToLastBuild(true)
          allowMissing(true)
        }
      }
    }
  }
  }
}


def dgm_build_jobs = [
  [
    name: "dgm-bcrm-build",
    repository: "git@bitbucket.org:engagelab/scb-bcrm.git",
    trigger: "H/5 * * * *",
    label: "sally",
    subsequent_job: ""
  ],
  [
    name: "dgm-cms-build",
    repository: "git@bitbucket.org:engagelab/scb-cms.git",
    trigger: "H/5 * * * *",
    label: "sally",
    subsequent_job: ""
  ]
].each { i ->
  job_name     = i['name'].replaceAll(" ", "-")
  build_branch = i.containsKey('branch') ? i['branch'] : 'master'
  repository   = i['repository']
  subsequent_job = i['subsequent_job']

  job("dgm/${job_name}") {

  description """
    Configure of ${job_name}
    """.stripIndent().trim()

  disabled(false)
  using "template-defaults"

  label("${i['label']}")

  scm {
    git {
      remote {
        url('${repository}')
      }
      branch('*/scb')
    }
  }

  triggers {
    scm("${i['trigger']}")
  }

  steps{
    shell("""
      echo "Get access to DGM repository"
      eval \$(aws ecr get-login --registry-ids 460105102933)

      echo "Start application stack"
      docker-compose down || true
      docker-compose up -d || true

      retries=1
      max_retries=50
      echo "INFO Try to connect \$max_retries time(s)...."
      until \$(curl --output /dev/null --silent --head --fail http://localhost:3000/bcrm/ping); do
        printf '.'
        sleep 10
        if [[ \$retries = \$max_retries ]]; then
          exit 1
        fi
        retries=\$(( \$retries + 1 ))
      done

      docker-compose exec -T web bash -c "rspec -f doc --require rails_helper"
      """.stripIndent().trim())
  }

  publishers {
    downstreamParameterized {
      trigger("dgm/${subsequent_job}") {
        parameters {
          currentBuild()
        }
      }
    }
  }

  }
}

def dgm_deploy_jobs = [
  [
    name: "dgm-deploy-sit",
    repository: "git@bitbucket.org:engagelab/scb-cms.git",
    trigger: "H/15 * * * *",
    label: "easyci"
  ],
  [
    name: "dgm-deploy-uat",
    repository: "git@bitbucket.org:engagelab/scb-cms.git",
    trigger: "H/15 * * * *",
    label: "easyci"
  ]
].each { i ->
  job_name     = i['name'].replaceAll(" ", "-")
  build_branch = i.containsKey('branch') ? i['branch'] : 'master'

  job("${job_name}/deploy-${current}") {
    description "Deploy job for dgm project "
    using "template-defaults"
    disabled(false)

    label("${i['label']}")

    scm {
        git {
          remote {
            url("${i['repository']}")
          }
        }
    }

    steps {
      copyArtifacts("${job_name}/build") {
        includePatterns('build.properties, gateway/bin/*')
        targetDirectory('.')
        flatten(true)
        optional(false)
        buildSelector {
          upstreamBuild {
            // Use "Last successful build" as fallback.
            fallbackToLastSuccessful(true)
          }
        }
      }

      shell("""\
          #!/bin/bash
          set -e

          # Set STAGE environment variable
          # because is used in the deploy scripts
          export STAGE=${current}

          TARGET_FILE="deploy-\${STAGE}.sh"
          if [ -f \$TARGET_FILE ]; then
            echo INFO Executing \$TARGET_FILE
            bash -e \$TARGET_FILE
          else
            echo INFO Using default deploy script
            bash -e deploy.sh
          fi
        """.stripIndent().trim())
      }

    }
  }


buildPipelineView("dgm-bcrm/pipeline") {
  filterBuildQueue()
  filterExecutors()
  title("CD Pipeline for bcrm")
  displayedBuilds(5)
  selectedJob("dgm/dgm-bcrm-build")
  alwaysAllowManualTrigger()
  showPipelineParameters()
  refreshFrequency(2)
}
