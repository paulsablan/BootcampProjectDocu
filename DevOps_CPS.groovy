
// Folders
//def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Jobs
def cartridge_build = freeStyleJob(projectFolderName + "/build_job")
def cartridge_sonar = freeStyleJob(projectFolderName + "/code_analysis")
def cartridge_snapshot_nexus = freeStyleJob(projectFolderName + "/snapshot_artifact")
def cartridge_ansible = freeStyleJob(projectFolderName + "/deploy_to_dev")
def cartridge_selenium = freeStyleJob(projectFolderName + "/functional_test")
def cartridge_release_nexus = freeStyleJob(projectFolderName + "/release_artifact")


// Views
def pipelineView = buildPipelineView(projectFolderName + "/DevOps-CPS-Pipeline")

pipelineView.with{
    title('DevOps-CPS-Pipeline')
    displayedBuilds(10)
    selectedJob(projectFolderName + "/build_job")
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}

//Maven
cartridge_build.with{

  properties {
    copyArtifactPermissionProperty {
      projectNames('build_job')
    } 
  }

  scm {
    git {           
      remote {
        credentials('adop-jenkins-master')
        //url('https://jona.micah.v.fidel@innersource.accenture.com/scm/~jona.micah.v.fidel/devops_cps_forked.git')
        url('git@gitlab:acn_eastwood/CurrencyConverterDTS.git')
        }
      branch('*/master')
    }
  }

  wrappers {
    preBuildCleanup()
  }

  triggers {
    bitbucketPush()
    scm('')
  }
  
  steps {
	maven{
	  mavenInstallation('ADOP Maven')
	  goals('package')    
	}
  }

  publishers {
    archiveArtifacts('**/*.war')
    downstream('code_analysis','SUCCESS')
  }
}

//SonarQube
cartridge_sonar.with{

  scm {
    git {
      remote {
         credentials('adop-jenkins-master')
        //url('https://jona.micah.v.fidel@innersource.accenture.com/scm/~jona.micah.v.fidel/devops_cps_forked.git')
        url('git@gitlab:acn_eastwood/CurrencyConverterDTS.git')
        }
      branch('*/master')
    }
  }
  
  configure { project ->
    project / 'builders' / 'hudson.plugins.sonar.SonarRunnerBuilder' {
      properties('''
sonar.projectKey=cpsprojectkey
sonar.projectName=CPS-CI-CD
sonar.projectVersion=1.0
sonar.sources=.''')
      javaOpts()
      jdk('(Inherit From Job)')
      task('scan')
    }
  }
  
  publishers {
    downstream('snapshot_artifact','SUCCESS') 
  }

}

//Nexus Snapshot
cartridge_nexus_snapshot.with{
  scm {
    git {
      remote {
         credentials('adop-jenkins-master')
        //url('https://jona.micah.v.fidel@innersource.accenture.com/scm/~jona.micah.v.fidel/devops_cps_forked.git')
        url('git@gitlab:acn_eastwood/CurrencyConverterDTS.git')
        }
      branch('*/master')
    }
  }
  steps {
    copyArtifacts('build_job') {
      includePatterns('target/*.war')
      buildSelector {
        latestSuccessful(true)
      }
      fingerprintArtifacts(true)
    }

    nexusArtifactUploader {
      nexusVersion('nexus2')
      protocol('HTTP')
      nexusUrl('nexus:8081/nexus')
      groupId('DTSActivity')
      version('1')
      repository('snapshots')
      credentialsId('acn_eastwood')
      artifact {
        artifactId('CurrencyConverter')
        type('war')
        file('/var/jenkins_home/jobs/CurrConv_BakedMac_Snapshots_Nexus/workspace/target/CurrencyConverter.war')
      }

    }

      publishers {
    downstream('deploy_to_dev','SUCCESS') 
  }
}

//Ansible
cartridge_ansible.with{

  label("ansible")
  scm {
    git {
      remote {
        credentials('adop-jenkins-master')
        url('git@gitlab:acn_eastwood/ansible-deploy.git')
      }
      branch('*/master')
    }
  }
  
  wrappers {
  	preBuildCleanup()
    sshAgent('ec2-user')
    }
  

  steps {
    shell('''
ansible-playbook -i hosts master.yml -u ec2-user -e "image_version=$BUILD_NUMBER username=$username password=$password"''')
  }

  publishers{
  	downstream('functional_test','SUCCESS')
  }
}

cartridge_selenium.with{

  scm {
    git {
      remote {
        credentials('acn_eastwood')
        url('git@gitlab:acn_eastwood/CurrencyConverterDTS.git')
      }          
      branch('*/master')
    }
  }
  
  steps{
    maven{
      mavenInstallation('ADOP Maven')
      goals('compile test')    
    }
  }
  
  publishers {
    downstream('release_artifact','SUCCESS') 
  }
}

//Nexus Release
cartridge_nexus_release.with{

  properties {
    copyArtifactPermissionProperty {
      projectNames('snapshot_artifact')
    } 
  }
  steps {
    copyArtifacts('snapshot_artifact') {
      includePatterns('target/*.tar')
      buildSelector {
        latestSuccessful(true)
      }
      fingerprintArtifacts(true)
    }

    nexusArtifactUploader {
      nexusVersion('nexus2')
      protocol('HTTP')
      nexusUrl('nexus:8081/nexus')
      groupId('DTSActivity')
      version('${BUILD_NUMBER}')
      repository('releases')
      credentialsId('nexus_id')
      artifact {
        artifactId('CurrencyConverter')
        type('war')
        file('target/CurrencyConverter.war')
      }

    }

}



}
