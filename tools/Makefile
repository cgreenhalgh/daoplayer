default: test

classes/gps/TestFilter.class: src/gps/TestFilter.java classes/org/opensharingtoolkit/daoplayer/audio/Context.class classes/org/opensharingtoolkit/daoplayer/audio/UserModel.class  classes/org/opensharingtoolkit/daoplayer/audio/KalmanFilter.class classes/org/opensharingtoolkit/daoplayer/ILog.class
	javac -d classes -classpath classes:lib/json.jar src/gps/TestFilter.java

classes/org/opensharingtoolkit/daoplayer/audio/Context.class : ../src/org/opensharingtoolkit/daoplayer/audio/Context.java classes/android/util/Log.class classes/org/opensharingtoolkit/daoplayer/ILog.class
	javac -d classes -classpath classes:lib/json.jar ../src/org/opensharingtoolkit/daoplayer/audio/Context.java

classes/org/opensharingtoolkit/daoplayer/ILog.class : src/org/opensharingtoolkit/daoplayer/ILog.java
	javac -d classes -classpath classes:lib/json.jar src/org/opensharingtoolkit/daoplayer/ILog.java


classes/org/opensharingtoolkit/daoplayer/audio/KalmanFilter.class : ../src/org/opensharingtoolkit/daoplayer/audio/KalmanFilter.java classes/android/util/Log.class
	javac -d classes -classpath classes:lib/json.jar:../libs/EJML-core-0.26.jar ../src/org/opensharingtoolkit/daoplayer/audio/KalmanFilter.java

classes/org/opensharingtoolkit/daoplayer/audio/UserModel.class : ../src/org/opensharingtoolkit/daoplayer/audio/UserModel.java classes/org/opensharingtoolkit/daoplayer/audio/Utils.class classes/android/util/Log.class classes/org/opensharingtoolkit/daoplayer/audio/KalmanFilter.class
	javac -d classes -classpath classes:lib/json.jar:../libs/EJML-core-0.26.jar ../src/org/opensharingtoolkit/daoplayer/audio/UserModel.java

classes/org/opensharingtoolkit/daoplayer/audio/Utils.class : ../src/org/opensharingtoolkit/daoplayer/audio/Utils.java
	javac -d classes -classpath classes:lib/json.jar ../src/org/opensharingtoolkit/daoplayer/audio/Utils.java

classes/android/util/Log.class: src/android/util/Log.java
	javac -d classes src/android/util/Log.java
	
.PHONY: test

test: classes/gps/TestFilter.class
	java -cp classes:lib/json.jar:../libs/EJML-core-0.26.jar gps.TestFilter ../test/contexttest/composition.json uon-lakewalk-20150310.log out.json

	
