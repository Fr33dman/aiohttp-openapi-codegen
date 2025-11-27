clean:
	rm -rf target >> /dev/null

build: clean
	mvn -q -DskipTests package
