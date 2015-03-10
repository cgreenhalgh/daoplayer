default: test

classes/gps/TestFilter.class: src/gps/TestFilter.java classes/org/opensharingtoolkit/daoplayer/audio/Context.class classes/org/opensharingtoolkit/daoplayer/audio/UserModel.class
	javac -d classes -classpath classes:lib/json.jar src/gps/TestFilter.java

classes/org/opensharingtoolkit/daoplayer/audio/Context.class : ../src/org/opensharingtoolkit/daoplayer/audio/Context.java classes/android/util/Log.class
	javac -d classes -classpath classes:lib/json.jar ../src/org/opensharingtoolkit/daoplayer/audio/Context.java

classes/org/opensharingtoolkit/daoplayer/audio/UserModel.class : ../src/org/opensharingtoolkit/daoplayer/audio/UserModel.java classes/org/opensharingtoolkit/daoplayer/audio/Utils.class classes/android/util/Log.class
	javac -d classes -classpath classes:lib/json.jar ../src/org/opensharingtoolkit/daoplayer/audio/UserModel.java

classes/org/opensharingtoolkit/daoplayer/audio/Utils.class : ../src/org/opensharingtoolkit/daoplayer/audio/Utils.java
	javac -d classes -classpath classes:lib/json.jar ../src/org/opensharingtoolkit/daoplayer/audio/Utils.java

classes/android/util/Log.class: src/android/util/Log.java
	javac -d classes src/android/util/Log.java
	
.PHONY: test

test: classes/gps/TestFilter.class
	java -cp classes:lib/json.jar gps.TestFilter ../test/contexttest/composition.json uon-lakewalk-20150310.log out.json

	