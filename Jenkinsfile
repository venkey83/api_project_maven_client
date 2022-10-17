pipeline {
  agent any
  stages {
    stage('Unit Test') {
      steps {
        sh 'sudo mvn clean test'
      }
    }
    stage('Deploy Standalone') {
      steps {
        sh 'sudo mvn clean package'
      }
    }
    stage('Deploy ARM') {
      environment {
        ANYPOINT_CREDENTIALS = credentials('anypoint.credentials')
      }
      steps {
        sh 'sudo mvn deploy -P arm -Darm.target.name=local-4.4.0-ee -Danypoint.username=${ANYPOINT_CREDENTIALS_USR} -Danypoint.password=${ANYPOINT_CREDENTIALS_PSW}'
      }
    }
    stage('Deploy CloudHub') {
      environment {
        ANYPOINT_CREDENTIALS = credentials('anypoint.credentials')
      }
      steps {
        sh 'sudo mvn deploy -P cloudhub -Dmule.version=4.4.0 -Danypoint.username=${ANYPOINT_CREDENTIALS_USR} -Danypoint.password=${ANYPOINT_CREDENTIALS_PSW}'
      }
    }
  }
}
