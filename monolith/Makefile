all:
	@echo "" \
	&& echo "=== Unmigrated endpoints ===" \
	&& head -6 /Volumes/git/github/2024/repos/yurl/YurlStash.java \
	&& echo "" \
	&& sleep 2 \
	&& cd app/src/main/java/ \
		&& test -L /Library/WebServer/Documents/yurl \
		&& ln -srf /Volumes/git/github/depot_parent/graalvm_aotc_java/5_java_httpcat_with_write/app/src/main/java/com/HttpCatWithWrite.java  \
		&& ln -srf /Volumes/git/github/2024/repos/yurl/YurlList.java \
		&& cd - \
		&& ./gradlew build \
		&& echo "" \
		&& echo "http://localhost/yurl/?rootId=221013&limit=500" \
		&& echo "" \
		&& ./gradlew run
		