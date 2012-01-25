#!/usr/bin/perl -wT
use strict;
use CGI qw(:standard);
use CGI::Carp qw(warningsToBrowser fatalsToBrowser);
use Config::Properties;

##
# Definition of global variables
#
my $jardir = "../lib";
my $appdir = "tim2-gtpm-editor/";
# Default codebase points to operation
my $codebase = "http://timweb.cern.ch/javaws";
my $c2monClientPropertiesFile = "/user/timoper/rep/c2mon/client/c2mon-client.properties";

##
# Reading version number from ../version.txt
#
open VFILE, "< ../version.txt"
  or die "Unable to open version file ../version.txt";
my $viewerVersion = <VFILE>;
chomp $viewerVersion; # removes new line character
close VFILE;

##
# In case of a SNAPSHOT the codebase will point to test
#
if ($viewerVersion =~ /-SNAPSHOT/) {
  $codebase = "http://timweb.cern.ch/test/javaws";
}

##
# Reading C2MON Client properties file #
#
open PROPS, "< $c2monClientPropertiesFile"
  or die "Unable to open configuration file $c2monClientPropertiesFile";
my $c2monProperties = new Config::Properties();
$c2monProperties->load(*PROPS);
my $jdbcDriver          = $c2monProperties->getProperty("c2mon.jdbc.driver");
my $jdbcRoUrl           = $c2monProperties->getProperty("c2mon.jdbc.ro.url");
my $jdbcRoUser          = $c2monProperties->getProperty("c2mon.jdbc.ro.user");
my $jdbcRoPassword      = $c2monProperties->getProperty("c2mon.jdbc.ro.password");
my $jmsUrl              = $c2monProperties->getProperty("c2mon.client.jms.url");
my $jmsUser             = $c2monProperties->getProperty("c2mon.client.jms.user");
my $jmsPassword         = $c2monProperties->getProperty("c2mon.client.jms.password");
my $jmsSupervisionTopic = $c2monProperties->getProperty("c2mon.client.jms.supervision.topic");
my $jmsHeartbeatTopic   = $c2monProperties->getProperty("c2mon.client.jms.heartbeat.topic");
my $jmsRequestQueue     = $c2monProperties->getProperty("c2mon.client.jms.request.queue");
close PROPS;


##
# Procedure to generate for each library defined in the ../lib directory
# an entry in the jnlp file.
#
sub jarlist {
  my $dir = shift;
  opendir DIR, $dir or return;
  my @contents = 
        map "$dir/$_", 
        sort grep !/^\.\.?$/,
        readdir DIR;
    closedir DIR;
    foreach (@contents) {
              my $htmldir = $appdir.substr($_, 3, length($_));
        if (!-l && -d) {
      &jarlist($_);
    }
    else {
      if (/tim-gtpm-editor.jar$/) {
            print "   <jar href=\"", $htmldir, "\" main=\"true\" download=\"eager\"/>\n";
      }
      elsif (/jar$/) {
            print "   <jar href=\"", $htmldir, "\" main=\"false\" download=\"eager\"/>\n";
      }
    }
        next
    }
}





##########################################
#         Generating JNLP file           #
##########################################

print "Content-type: application/x-java-jnlp-file" , "\n\n";
print "<?xml version = '1.0' encoding = 'utf-8'?>
	<jnlp spec=\"1.0+\" codebase=\"$codebase\">
	<information>
      		<title>TIM GTPM Editor</title>
      		<vendor>Technical Infrastructure Monitoring (TIM) Team</vendor>
      		<homepage href=\"http://timweb.cern.ch\"/>
      		<description>This application is based on the IBM ILOG GTPM Editor and allows users to draw GTPM diagrams for the TIM Viewer.</description>
			<icon href=\"http://timweb.cern.ch/img/tim-animated-320x200.gif\" kind=\"splash\"/>
      		<offline-allowed/>
		<shortcut online=\"false\">
		  <desktop/>
		  <menu submenu=\"TIM GTPM Editor\"/>
		</shortcut>
	</information>
	<security> 
		<all-permissions/> 
	</security> 
	<resources>
		<java version=\"1.6+\" initial-heap-size=\"256M\"  max-heap-size=\"512M\"/>" , "\n";

jarlist ("$jardir");

# Defines the version number that is shown in the TIM Viewer about dialog
print "   <property name=\"tim.version\" value=\"$viewerVersion\"/>\n";
# JMS configuration parameters needed by C2MON client API
print "   <property name=\"c2mon.client.jms.url\" value=\"$jmsUrl\"/>\n";
print "   <property name=\"c2mon.client.jms.user\" value=\"$jmsUser\"/>\n";
print "   <property name=\"c2mon.client.jms.password\" value=\"$jmsPassword\"/>\n";
print "   <property name=\"c2mon.client.jms.supervision.topic\" value=\"$jmsSupervisionTopic\"/>\n";
print "   <property name=\"c2mon.client.jms.heartbeat.topic\" value=\"$jmsHeartbeatTopic\"/>\n";
print "   <property name=\"c2mon.client.jms.request.queue\" value=\"$jmsRequestQueue\"/>\n";
# C2MON read-only credentials to STL database, needed for the history player and charts
print "   <property name=\"c2mon.jdbc.driver\" value=\"$jdbcDriver\"/>\n";
print "   <property name=\"c2mon.jdbc.ro.url\" value=\"$jdbcRoUrl\"/>\n";
print "   <property name=\"c2mon.jdbc.ro.user\" value=\"$jdbcRoUser\"/>\n";
print "   <property name=\"c2mon.jdbc.ro.password\" value=\"$jdbcRoPassword\"/>\n";
print " </resources>
        <resources os=\"Windows\" >
                <property name=\"tim.log.file\" value=\"c:\\temp\\tim-gtpm-editor.log\"/>
        </resources>
        <application-desc main-class=\"ch.cern.gtpmeditor.Main\">
        </application-desc>
</jnlp>" , "\n";
