routes-maven-plugin
-------------------

Add plugin to project:

	<plugin>
		<groupId>no.oftedal</groupId>
		<artifactId>routes-maven-plugin</artifactId>
		<version>1.0-SNAPSHOT</version>
		<configuration>
			<goalPrefix>routes</goalPrefix>
		</configuration>
	</plugin>

Run plugin:

	mvn install routes-maven-plugin:routes -Dscanpackages="com.company"

