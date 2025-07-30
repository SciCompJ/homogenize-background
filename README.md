# Plugin Template for Imago

Simple plugin template for the Imago project.

## Installation

* create a new project from this template
* clone it locally

## Configuration

* update the `pom.xml` file with information relevant to the plugin to create (in particular the "artifactId" tag)
* add the necessary dependencies if needed

Note: CS4J and Imago are not available as dependencies yet. Within eclipse, they can be added from the contextual menu "properties > Java Build Path", and selecting the "Libraries" tab.

## Edition

* start from the file `my.plugin.HelloImagoPlugin` to develop own plugin and asociated files
* update the `plugins.config` configuration file

Package into a jar file, and put into the `plugins` directory of the current imago installation (can be the development version if necessary).
