
CLASSPATH= -classpath "lib/gson-2.2.4.jar:."

JC = javac
.SUFFIXES: .java .class
.java.class:
		$(JC) $(CLASSPATH) $*.java

CLASSES = \
        GrepProtocol.java \
        TestGrepProtocol.java \
		TestClientGrep.java \
		GrepServer.java \
		GrepClient.java \
        GossipUdpServer.java \
        GossipServer.java

default: classes


classes: $(CLASSES:.java=.class)

clean:
		$(RM) *.class
