/usr/lib/jvm/java-11-openjdk-amd64/bin/java -classpath ~/".groovy/lib/*:$HOME/.groovy/lib_java11/*" YurlList.java 2>&1 | tee /tmp/out.log
/usr/lib/jvm/java-11-openjdk-amd64/bin/java -classpath ~/".groovy/lib/*:$HOME/.groovy/lib_java11/*" YurlStash.java 2>&1 | tee /tmp/out.log
#/usr/lib/jvm/java-11-openjdk-amd64/bin/java -classpath ~/".groovy/lib/*" /tmp/YurlStash.java
